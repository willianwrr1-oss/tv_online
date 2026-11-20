package com.willian.tvaberta.ui

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(vm: MainViewModel) {
    val pc = vm.player
    val ch = pc.current ?: return
    val s by SettingsStore.flow.collectAsState()
    val states by com.willian.tvaberta.data.Repo.states.collectAsState()
    val fav = states[ch.key]?.favorite == true
    val title = states[ch.key]?.customName?.takeIf { it.isNotBlank() } ?: ch.name

    val context = LocalContext.current
    val activity = context.findActivity()
    val view = LocalView.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    var overlay by remember { mutableStateOf(true) }

    // Guia: programa no ar e o seguinte, atualizados a cada 30 s.
    val epgVersion by Epg.version.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }
    val nowNext: Pair<Programme?, Programme?> = if (s.epgEnabled) remember(ch.key, nowMs, epgVersion) { Epg.nowNext(ch, nowMs) } else Pair(null, null)
    var tick by remember { mutableIntStateOf(0) }

    // Barra de progresso: só aparece em filmes e episódios (conteúdo que não é ao vivo).
    var posMs by remember { mutableLongStateOf(0L) }
    var durMs by remember { mutableLongStateOf(0L) }
    var isLive by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pc.exo, ch.key) {
        while (true) {
            val p = pc.exo
            if (p != null) {
                val d = p.duration
                isLive = p.isCurrentMediaItemLive || d == C.TIME_UNSET
                durMs = if (d == C.TIME_UNSET) 0L else d
                if (!scrubbing) posMs = p.currentPosition
            }
            delay(500)
        }
    }

    BackHandler { vm.closePlayer() }

    // Tela cheia imersiva enquanto o player estiver aberto.
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(s.keepScreenOn) {
        view.keepScreenOn = s.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(s.landscapeOnPlay) {
        if (s.landscapeOnPlay) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // Esconde os controles sozinho enquanto o vídeo roda.
    LaunchedEffect(overlay, tick, pc.status) {
        if (overlay && pc.status == PlayStatus.PLAYING) {
            delay(s.overlaySeconds * 1000L)
            overlay = false
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
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

        // Camada de gestos: toque mostra/esconde controles, deslizar troca de canal.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        overlay = !overlay
                        tick++
                    })
                }
                .pointerInput(s.swipeZap) {
                    if (s.swipeZap) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -150f) pc.next() else if (total > 150f) pc.previous()
                                total = 0f
                            },
                            onDragCancel = { total = 0f },
                            onVerticalDrag = { _, amount -> total += amount }
                        )
                    }
                }
        )

        // Carregando
        if (pc.status == PlayStatus.BUFFERING) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // Erro
        if (pc.status == PlayStatus.ERROR) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE6101419))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF8A8A), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    pc.errorText ?: "Não foi possível reproduzir este canal.",
                    color = Color(0xFFD0D6E0),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pc.retry() }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Tentar de novo")
                    }
                    OutlinedButton(onClick = { pc.next() }) { Text("Próximo", color = Color.White) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.setDisabled(listOf(ch.key), true)
                        pc.skipAndForget()
                    }) {
                        Icon(Icons.Default.VisibilityOff, null, Modifier.size(16.dp), tint = Color.White)
                        Spacer(Modifier.size(6.dp))
                        Text("Desativar", color = Color.White)
                    }
                    OutlinedButton(onClick = {
                        vm.delete(listOf(ch.key))
                        pc.skipAndForget()
                    }) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color(0xFFFF8A8A))
                        Spacer(Modifier.size(6.dp))
                        Text("Excluir", color = Color(0xFFFF8A8A))
                    }
                }
            }
        }

        // Controles
        AnimatedVisibility(
            visible = overlay && !vm.inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                // Barra superior
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverlayIcon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") { vm.closePlayer() }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val sub = buildString {
                            append(ch.groups.joinToString(", ") { catLabel(it) })
                            if (ch.quality != null) append(" · ").append(ch.quality)
                        }
                        Text(
                            sub,
                            color = Color(0xFFD0D6E0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                        val cur = nowNext.first
                        val nxt = nowNext.second
                        if (cur != null) {
                            Text(
                                "Agora: " + cur.title + "  (" + fmtClock(cur.start) + "–" + fmtClock(cur.stop) + ")",
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val total = (cur.stop - cur.start).coerceAtLeast(1L)
                            val frac = ((nowMs - cur.start).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .padding(vertical = 3.dp)
                                    .fillMaxWidth(0.6f)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x55FFFFFF))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(frac)
                                        .height(3.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        if (nxt != null) {
                            Text(
                                "A seguir: " + nxt.title + "  (" + fmtClock(nxt.start) + ")",
                                color = Color(0xFFD0D6E0),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    OverlayIcon(
                        if (fav) Icons.Default.Star else Icons.Default.StarBorder,
                        "Favoritar",
                        tint = if (fav) Color(0xFFF5B301) else Color.White
                    ) {
                        vm.toggleFavorite(ch.key)
                        tick++
                    }
                    OverlayIcon(Icons.Default.ScreenRotation, "Girar tela") {
                        activity?.requestedOrientation =
                            if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        tick++
                    }
                    OverlayIcon(Icons.Default.PictureInPictureAlt, "Imagem em imagem") {
                        if (activity != null) enterPip(activity)
                    }
                }

                // Barra inferior
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .navigationBarsPadding()
                        .padding(vertical = 14.dp)
                ) {
                if (!isLive && durMs > 0L) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            fmtTime(if (scrubbing) scrubValue.toLong() else posMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Slider(
                            value = (if (scrubbing) scrubValue else posMs.toFloat()).coerceIn(0f, durMs.toFloat()),
                            onValueChange = {
                                scrubbing = true
                                scrubValue = it
                                tick++
                            },
                            onValueChangeFinished = {
                                pc.exo?.seekTo(scrubValue.toLong())
                                posMs = scrubValue.toLong()
                                scrubbing = false
                            },
                            valueRange = 0f..durMs.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        Text(
                            fmtTime(durMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OverlayIcon(
                        if (pc.muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        "Som"
                    ) {
                        pc.toggleMute()
                        tick++
                    }
                    OverlayIcon(Icons.Default.SkipPrevious, "Canal anterior", size = 52.dp) {
                        pc.previous()
                        tick++
                    }
                    Box(
                        Modifier
                            .padding(horizontal = 20.dp)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        OverlayIcon(
                            if (pc.status == PlayStatus.PLAYING || pc.status == PlayStatus.BUFFERING)
                                Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Pausar ou continuar",
                            size = 68.dp
                        ) {
                            pc.togglePlayPause()
                            tick++
                        }
                    }
                    OverlayIcon(Icons.Default.SkipNext, "Próximo canal", size = 52.dp) {
                        pc.next()
                        tick++
                    }
                    OverlayIcon(Icons.Default.AspectRatio, "Proporção da imagem") {
                        SettingsStore.update { it.copy(resizeMode = (it.resizeMode + 1) % 3) }
                        val label = when (SettingsStore.value.resizeMode) {
                            1 -> "Preencher"
                            2 -> "Zoom"
                            else -> "Ajustar à tela"
                        }
                        vm.toast("Imagem: $label")
                        tick++
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun OverlayIcon(
    icon: ImageVector,
    description: String,
    size: Dp = 44.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}
