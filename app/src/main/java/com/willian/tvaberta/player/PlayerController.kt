package com.willian.tvaberta.player

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.M3uParser
import com.willian.tvaberta.data.PlayStatus
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore

/**
 * Cuida do ExoPlayer: abre o canal, tenta links de reserva, reconecta quando cai,
 * troca de canal (anterior/próximo) e respeita os ajustes de reprodução.
 */
class PlayerController(private val app: Application) {

    var exo by mutableStateOf<ExoPlayer?>(null)
        private set
    var current by mutableStateOf<Channel?>(null)
        private set
    var status by mutableStateOf(PlayStatus.IDLE)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set
    var muted by mutableStateOf(false)
        private set

    private var queue: List<Channel> = emptyList()
    private var urlIndex = 0
    private var retryCount = 0
    private var resumeOnStart = false
    private var builtWith = -1
    private val handler = Handler(Looper.getMainLooper())

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            refreshStatus()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            refreshStatus()
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError(error)
        }
    }

    // ------------------------------------------------------------------ abrir / fechar

    fun play(ch: Channel, list: List<Channel>) {
        handler.removeCallbacksAndMessages(null)
        queue = if (list.any { it.key == ch.key }) list else listOf(ch) + list
        current = ch
        urlIndex = 0
        retryCount = 0
        altUrl = null
        errorText = null
        val s = SettingsStore.value
        muted = s.startMuted
        if (s.blockMobile && onMobileData()) {
            exo?.stop()
            errorText = "Reprodução bloqueada em dados móveis. Conecte-se a uma rede Wi-Fi ou libere em Configurações > Reprodução."
            status = PlayStatus.ERROR
            return
        }
        Repo.updateState(ch.key) { it.copy(plays = it.plays + 1, lastPlayed = System.currentTimeMillis()) }
        SettingsStore.update { it.copy(lastChannelKey = ch.key) }
        prepareCurrent()
    }

    fun retry() {
        val ch = current ?: return
        play(ch, queue)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        exo?.release()
        exo = null
        builtWith = -1
        current = null
        errorText = null
        status = PlayStatus.IDLE
    }

    fun next() = step(1)

    fun previous() = step(-1)

    private fun step(d: Int) {
        val c = current ?: return
        if (queue.size < 2) return
        val i = queue.indexOfFirst { it.key == c.key }.coerceAtLeast(0)
        val n = queue[(i + d + queue.size) % queue.size]
        play(n, queue)
    }

    /** Tira o canal atual da fila (porque foi desativado ou excluído) e abre o seguinte. */
    fun skipAndForget() {
        val c = current ?: return
        val i = queue.indexOfFirst { it.key == c.key }
        val rest = queue.filter { it.key != c.key }
        if (rest.isEmpty()) {
            stop()
            return
        }
        val n = rest[(if (i < 0) 0 else i) % rest.size]
        play(n, rest)
    }

    fun togglePlayPause() {
        val p = exo ?: return
        if (p.isPlaying || p.playWhenReady) {
            p.pause()
        } else {
            // Em canal ao vivo volta para o ponto atual; em filme continua de onde parou.
            if (p.isCurrentMediaItemLive) p.seekToDefaultPosition()
            p.play()
        }
        refreshStatus()
    }

    fun toggleMute() {
        muted = !muted
        exo?.volume = if (muted) 0f else 1f
    }

    fun isPlayingNow(): Boolean = exo?.isPlaying == true

    // ------------------------------------------------------------------ ciclo de vida da tela

    fun onActivityStop(inPip: Boolean) {
        val p = exo ?: return
        if (!inPip && (p.isPlaying || p.playWhenReady)) {
            resumeOnStart = true
            p.pause()
        }
    }

    fun onActivityStart() {
        if (resumeOnStart) {
            resumeOnStart = false
            exo?.let {
                if (it.isCurrentMediaItemLive) it.seekToDefaultPosition()
                it.play()
            }
        }
    }

    fun pauseNow() {
        exo?.pause()
    }

    // ------------------------------------------------------------------ interno

    private fun ensurePlayer(): ExoPlayer {
        val mode = SettingsStore.value.bufferMode
        val existing = exo
        if (existing != null && builtWith == mode) return existing
        existing?.release()
        val load = when (mode) {
            0 -> DefaultLoadControl.Builder().setBufferDurationsMs(2000, 8000, 1000, 1500).build()
            2 -> DefaultLoadControl.Builder().setBufferDurationsMs(20000, 60000, 3000, 7000).build()
            else -> DefaultLoadControl.Builder().setBufferDurationsMs(10000, 30000, 2500, 5000).build()
        }
        val p = ExoPlayer.Builder(app).setLoadControl(load).build()
        p.addListener(listener)
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        p.setHandleAudioBecomingNoisy(true)
        exo = p
        builtWith = mode
        return p
    }

    private var altUrl: String? = null

    private fun prepareCurrent() {
        val ch = current ?: return
        val url = ch.urls.getOrNull(urlIndex) ?: return
        val s = SettingsStore.value
        val p = ensurePlayer()

        val (maxW, maxH) = when (s.maxQuality) {
            1 -> 1920 to 1080
            2 -> 1280 to 720
            3 -> 854 to 480
            4 -> 640 to 360
            else -> Int.MAX_VALUE to Int.MAX_VALUE
        }
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(maxW, maxH)
            .build()
        p.volume = if (muted) 0f else 1f

        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)
            .setUserAgent(ch.userAgent ?: M3uParser.DEFAULT_UA)
        val ref = ch.referrer
        if (!ref.isNullOrBlank()) {
            http.setDefaultRequestProperties(mapOf("Referer" to ref))
        }

        // Analisa o caminho, não a URL inteira: tokens e parâmetros de autenticação
        // podem aparecer depois da extensão (?token=...), sem alterar o formato real.
        val uri = android.net.Uri.parse(url)
        val scheme = uri.scheme?.lowercase().orEmpty()
        val path = uri.path?.lowercase().orEmpty()
        val builder = MediaItem.Builder().setUri(url)
        val flip = altUrl == url

        when {
            // RTSP é detectado automaticamente pelo DefaultMediaSourceFactory
            // quando a dependência media3-exoplayer-rtsp está presente.
            scheme == "rtsp" -> Unit

            path.endsWith(".mpd") ->
                builder.setMimeType(MimeTypes.APPLICATION_MPD)

            // Microsoft Smooth Streaming: /arquivo.ism/manifest
            path.endsWith(".ism/manifest") || path.endsWith(".isml/manifest") ||
                path.endsWith(".ismc") ->
                builder.setMimeType(MimeTypes.APPLICATION_SS)

            else -> {
                // URLs sem extensão podem ser HLS ou MPEG-TS direto.
                // Mantém a tentativa alternativa em caso de erro de parsing.
                val progressiveExtensions = listOf(
                    ".mp4", ".m4v", ".mov", ".mkv", ".webm", ".ts", ".m2ts", ".mts",
                    ".mp3", ".aac", ".m4a", ".wav", ".flac", ".ogg", ".opus",
                    ".ac3", ".ec3", ".amr", ".flv", ".mpeg", ".mpg"
                )
                val isKnownProgressive = progressiveExtensions.any { path.endsWith(it) }
                val guessHls = path.endsWith(".m3u8") || !isKnownProgressive
                val hls = if (flip) !guessHls else guessHls
                if (hls) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }
        val source = DefaultMediaSourceFactory(http).createMediaSource(builder.build())
        p.setMediaSource(source)
        p.prepare()
        p.playWhenReady = true
        refreshStatus()
    }

    private fun handleError(error: PlaybackException) {
        val ch = current ?: return
        val p = exo
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && p != null) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        val isFormatError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        val curUrl = ch.urls.getOrNull(urlIndex)
        if (isFormatError && curUrl != null && altUrl != curUrl) {
            altUrl = curUrl
            prepareCurrent()
            return
        }
        val s = SettingsStore.value
        if (s.autoFallback && urlIndex + 1 < ch.urls.size) {
            urlIndex++
            prepareCurrent()
            return
        }
        if (retryCount < s.retries) {
            retryCount++
            urlIndex = 0
            status = PlayStatus.BUFFERING
            handler.postDelayed({ prepareCurrent() }, 2000L * retryCount)
            return
        }
        altUrl = null
        errorText = friendly(error)
        status = PlayStatus.ERROR
    }

    private fun friendly(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Sem conexão com o canal. Verifique sua internet ou o canal pode estar fora do ar."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "O servidor recusou o acesso. O canal pode estar fora do ar ou bloqueado na sua região."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "O formato deste canal não é compatível: o link não entrega um vídeo válido (costuma ser link antigo que saiu do ar, página da web ou formato como RTMP/FLV). [" + e.errorCodeName + "]"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
            "Este aparelho não consegue decodificar o vídeo ou o áudio deste canal (codec não suportado). [" + e.errorCodeName + "]"
        else -> "Não foi possível reproduzir este canal agora. [" + e.errorCodeName + "]"
    }

    private fun refreshStatus() {
        val p = exo
        if (p == null) {
            status = PlayStatus.IDLE
            return
        }
        if (errorText != null) {
            status = PlayStatus.ERROR
            return
        }
        status = when {
            p.playbackState == Player.STATE_BUFFERING -> PlayStatus.BUFFERING
            p.isPlaying -> PlayStatus.PLAYING
            p.playbackState == Player.STATE_READY -> PlayStatus.PAUSED
            p.playbackState == Player.STATE_ENDED -> PlayStatus.PAUSED
            else -> PlayStatus.BUFFERING
        }
    }

    private fun onMobileData(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
