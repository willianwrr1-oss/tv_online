package com.willian.tvaberta.ui

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.PlayStatus
import com.willian.tvaberta.data.Programme
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.delay

/**
 * Player para controle remoto.
 *  - Cima / baixo (ou CH+ / CH-): próximo / canal anterior.
 *  - OK: abre o menu de ações. Menu: abre ou fecha o menu.
 *  - Esquerda / direita: em filmes, volta ou avança 10 s; em canais ao vivo, mostra as informações.
 *  - Play/Pause do controle: pausa ou continua. Voltar: fecha o menu e, depois, o player.
 */
@Composable
fun TvPlayerScreen(vm: MainViewModel) {
    val pc = vm.player
    val ch = pc.current ?: return
    val s by SettingsStore.flow.collectAsState()
    val states by Repo.states.collectAsState()
    val fav = states[ch.key]?.favorite == true
    val title = states[ch.key]?.customName?.takeIf { it.isNotBlank() } ?: ch.name
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()

    // 0 = nada, 1 = informações, 2 = menu de ações
    var mode by remember { mutableIntStateOf(1) }
    var tick by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val firstBtn = remember { FocusRequester() }

    // Guia: programa no ar e o seguinte
    val epgVersion by Epg.version.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val nowNext: Pair<Programme?, Programme?> = if (s.epgEnabled) remember(ch.key, nowMs, epgVersion) { Epg.nowNext(ch, nowMs) } else Pair(null, null)

    // Posição e duração (filmes e episódios)
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(true) }
    LaunchedEffect(pc.exo, ch.key) {
        while (true) {
            val p = pc.exo
            if (p != null) {
                val d = p.duration
                isLive = p.isCurrentMediaItemLive || d == C.TIME_UNSET
                durMs = if (d == C.TIME_UNSET) 0L else d
                posMs = p.currentPosition
            }
            delay(500)
        }
    }

    BackHandler {
        if (mode == 2) mode = 0 else vm.closePlayer()
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    DisposableEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    // Trocou de canal: mostra as informações. Deu erro: abre o menu (com "Tentar de novo").
    LaunchedEffect(ch.key) {
        if (mode != 2) mode = 1
        tick++
    }
    LaunchedEffect(pc.status) {
        if (pc.status == PlayStatus.ERROR) mode = 2
    }

    // Esconde sozinho enquanto o vídeo roda.
    LaunchedEffect(mode, tick, pc.status) {
        if (pc.status == PlayStatus.PLAYING && mode != 0) {
            delay(if (mode == 2) maxOf(s.overlaySeconds * 1000L, 10_000L) else s.overlaySeconds * 1000L)
            mode = 0
        }
    }

    // O foco vai para o primeiro botão quando o menu abre e volta para a tela quando fecha.
    LaunchedEffect(mode) {
        delay(80)
        runCatching { if (mode == 2) firstBtn.requestFocus() else rootFocus.requestFocus() }
    }

    fun seekBy(deltaMs: Long) {
        val p = pc.exo ?: return
        val d = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0L)
        p.seekTo(if (d != C.TIME_UNSET) target.coerceAtMost(d) else target)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                val k = e.key
                // Menu: só alterna o menu uma vez por toque; segurar ou soltar não faz mais nada.
                if (k == Key.Menu) {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) {
                        tick++
                        mode = if (mode == 2) 0 else 2
                    }
                    return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // OK segurado não repete ação.
                if (e.nativeKeyEvent.repeatCount > 0 &&
                    (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter)
                ) return@onPreviewKeyEvent true
                tick++
                when {
                    k == Key.MediaPlayPause || k == Key.MediaPlay || k == Key.MediaPause -> {
                        pc.togglePlayPause()
                        if (mode == 0) mode = 1
                        true
                    }
                    k == Key.MediaNext || k == Key.ChannelUp -> {
                        pc.next()
                        true
                    }
                    k == Key.MediaPrevious || k == Key.ChannelDown -> {
                        pc.previous()
                        true
                    }
                    k == Key.Menu -> {
                        mode = if (mode == 2) 0 else 2
                        true
                    }
                    mode == 2 -> false
                    k == Key.DirectionUp -> {
                        pc.next()
                        true
                    }
                    k == Key.DirectionDown -> {
                        pc.previous()
                        true
                    }
                    k == Key.DirectionLeft -> {
                        if (!isLive) seekBy(-10_000L)
                        mode = 1
                        true
                    }
                    k == Key.DirectionRight -> {
                        if (!isLive) seekBy(10_000L)
                        mode = 1
                        true
                    }
                    k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter -> {
                        mode = 2
                        true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { v ->
                v.player = pc.exo
                v.resizeMode = when (s.resizeMode) {
                    1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            }
        )

        if (pc.status == PlayStatus.BUFFERING) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        AnimatedVisibility(
            visible = mode != 0,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
                    .padding(horizontal = 48.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (pc.status == PlayStatus.ERROR) {
                    Text(
                        pc.errorText ?: "Não foi possível reproduzir este canal.",
                        color = Color(0xFFFFB4B4),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (mode == 2) {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        var assigned = false
                        fun req(): FocusRequester? {
                            if (assigned) return null
                            assigned = true
                            return firstBtn
                        }
                        if (pc.status == PlayStatus.ERROR) {
                            TvButton("Tentar de novo", icon = Icons.Default.Refresh, requester = req()) { pc.retry() }
                        }
                        TvButton("Anterior", icon = Icons.Default.SkipPrevious, requester = req()) { pc.previous() }
                        TvButton("Próximo", icon = Icons.Default.SkipNext, requester = req()) { pc.next() }
                        TvButton(
                            if (pc.status == PlayStatus.PLAYING) "Pausar" else "Continuar",
                            icon = if (pc.status == PlayStatus.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                            requester = req()
                        ) { pc.togglePlayPause() }
                        TvButton(
                            if (fav) "Favorito" else "Favoritar",
                            icon = if (fav) Icons.Default.Star else Icons.Default.StarBorder,
                            requester = req()
                        ) {
                            vm.toggleFavorite(ch.key)
                            tick++
                        }
                        TvButton(
                            if (pc.muted) "Ligar som" else "Mudo",
                            icon = if (pc.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            requester = req()
                        ) { pc.toggleMute() }
                        TvButton("Tela", icon = Icons.Default.AspectRatio, requester = req()) {
                            SettingsStore.update { it.copy(resizeMode = (it.resizeMode + 1) % 3) }
                        }
                        TvButton("Desativar", icon = Icons.Default.VisibilityOff, danger = true, requester = req()) {
                            vm.setDisabled(listOf(ch.key), true, withUndo = false)
                            pc.skipAndForget()
                        }
                        TvButton("Excluir", icon = Icons.Default.Delete, danger = true, requester = req()) {
                            vm.delete(listOf(ch.key))
                            pc.skipAndForget()
                        }
                    }
                }

                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineSmall
                )
                val sub = buildString {
                    append(ch.groups.joinToString(", ") { catLabel(it) })
                    if (ch.quality != null) append(" · ").append(ch.quality)
                }
                Text(sub, color = Color(0xFFD0D6E0), maxLines = 1, style = MaterialTheme.typography.bodyMedium)

                val cur = nowNext.first
                val nxt = nowNext.second
                if (cur != null) {
                    Text(
                        "Agora: " + cur.title + "  (" + fmtClock(cur.start) + "–" + fmtClock(cur.stop) + ")",
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                    val total = (cur.stop - cur.start).coerceAtLeast(1L)
                    val frac = ((nowMs - cur.start).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { frac },
                        modifier = Modifier.fillMaxWidth(0.5f),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0x55FFFFFF)
                    )
                }
                if (nxt != null) {
                    Text(
                        "A seguir: " + nxt.title + "  (" + fmtClock(nxt.start) + ")",
                        color = Color(0xFFD0D6E0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (!isLive && durMs > 0L) {
                    LinearProgressIndicator(
                        progress = { (posMs.toFloat() / durMs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0x55FFFFFF)
                    )
                    Text(
                        fmtTime(posMs) + " / " + fmtTime(durMs) + "    ◀ ▶ avançam 10 s",
                        color = Color(0xFFD0D6E0),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (mode == 1) {
                    Text(
                        "OK: menu   ▲▼: trocar de canal   Voltar: sair",
                        color = Color(0x99FFFFFF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
