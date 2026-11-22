package com.willian.tvaberta.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.PlayStatus
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * TV ao vivo na Android TV: grupos à esquerda (OK abre e fecha), pré-visualização à direita
 * com dados técnicos e programação. OK no vídeo abre a tela cheia.
 */
@Composable
fun TvLiveScreen(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    val query by vm.query.collectAsState()
    val refreshing by Repo.refreshing.collectAsState()

    val model by vm.liveModel.collectAsState()
    val groups = model.groups
    val origins = model.origins
    val org = model.org
    val total = model.total

    val open = vm.liveGroup
    val listState = rememberLazyListState()
    val firstReq = remember { FocusRequester() }
    val previewReq = remember { FocusRequester() }
    var menuTarget by remember { mutableStateOf<ChannelItem?>(null) }
    var menuGroup by remember { mutableStateOf<String?>(null) }
    var originMenu by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    val current = vm.player.current

    LaunchedEffect(open) {
        val i = groups.indexOfFirst { it.id == open }
        if (i >= 0) runCatching { listState.animateScrollToItem(i) }
    }
    // Ao abrir a tela ou voltar da tela cheia, o foco vai para o vídeo (se há um) ou para a lista.
    LaunchedEffect(groups.size, vm.focusPending) {
        if (!vm.focusPending) return@LaunchedEffect
        delay(200)
        if (vm.player.current != null && vm.previewMode) runCatching { previewReq.requestFocus() }
        else if (groups.isNotEmpty()) runCatching { firstReq.requestFocus() }
        vm.focusPending = false
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.42f).fillMaxHeight()) {
            Text(
                when (vm.area) { 1 -> "Filmes"; 2 -> "Séries"; else -> "TV ao vivo" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (total == 1) "1 item" else "${total} itens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("Início") { vm.openArea(null) }
                TvButton(if (query.isBlank()) "Buscar" else "Busca: " + query.trim()) { searchOpen = true }
                TvButton("Atualizar") { vm.refresh(true) }
            }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (origins.size > 1) {
                LazyRow(
                    state = vm.originListState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item { TvButton("Todas as listas", selected = org == null) { vm.origin.value = null } }
                    items(origins) { o ->
                        TvButton(Repo.originName(o), selected = org == o, onLongClick = { originMenu = o }) { vm.origin.value = o }
                    }
                }
            }
            if (groups.isEmpty()) {
                TvEmpty(
                    "Nenhum canal",
                    "Atualize a lista ou ative uma fonte em Ajustes > Fontes de canais.",
                    "Atualizar lista"
                ) { vm.refresh(true) }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp, start = 4.dp, end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groups.forEachIndexed { gi, g ->
                        item(key = "h:" + g.id) {
                            TvButton(
                                g.label + "  (" + g.count + ")",
                                modifier = Modifier.fillMaxWidth(),
                                selected = open == g.id,
                                requester = if (gi == 0) firstReq else null,
                                onLongClick = { if (g.isCategory) menuGroup = g.id }
                            ) {
                                vm.liveSub = null
                                vm.liveGroup = if (open == g.id) null else g.id
                            }
                        }
                        if (open == g.id) {
                            if (g.children.isNotEmpty()) {
                                g.children.forEach { c ->
                                    val subOpen = vm.liveSub == c.id
                                    item(key = "h:" + c.id) {
                                        TvButton(
                                            c.label + "  (" + c.count + ")",
                                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                                            selected = subOpen
                                        ) { vm.liveSub = if (subOpen) null else c.id }
                                    }
                                    if (subOpen) tvRows(vm, c, current, 32) { menuTarget = it }
                                }
                            } else {
                                tvRows(vm, g, current, 16) { menuTarget = it }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.size(14.dp))
        BoxWithConstraints(Modifier.weight(0.58f).fillMaxHeight()) {
            val ph = minOf(maxWidth * 9f / 16f, maxHeight * 0.55f)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(ph), contentAlignment = Alignment.Center) {
                    LivePreview(vm, Modifier.fillMaxHeight().aspectRatio(16f / 9f), previewReq)
                }
                Spacer(Modifier.height(8.dp))
                TechStrip(vm, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                EpgPanel(vm, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    val mt = menuTarget
    if (mt != null) ChannelMenuDialog(vm, mt) { menuTarget = null }

    val mg = groups.firstOrNull { it.id == menuGroup }
    if (mg != null) {
        val keys by produceState(emptyList<String>(), mg.id) { value = vm.keysOfGroup(mg.id) }
        val safe = remember { FocusRequester() }
        LaunchedEffect(mg.id) {
            delay(150)
            runCatching { safe.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { menuGroup = null },
            title = { Text(mg.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(keys.size.toString() + " canais nesta categoria" + if (org != null) " (na lista escolhida)." else ".")
                    TvButton("Cancelar", modifier = Modifier.fillMaxWidth(), requester = safe) { menuGroup = null }
                    TvButton("Desativar os " + keys.size + " canais", modifier = Modifier.fillMaxWidth()) {
                        vm.setDisabled(keys, true)
                        menuGroup = null
                    }
                    TvButton("Excluir os " + keys.size + " canais", modifier = Modifier.fillMaxWidth(), danger = true) {
                        vm.requestDelete(keys)
                        if (vm.liveGroup == mg.id) vm.liveGroup = null
                        menuGroup = null
                    }
                }
            },
            confirmButton = {}
        )
    }
    val om = originMenu
    if (om != null) {
        val keys by produceState(emptyList<String>(), om) { value = vm.keysOfOrigin(om) }
        val safe = remember { FocusRequester() }
        LaunchedEffect(om) {
            delay(150)
            runCatching { safe.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { originMenu = null },
            title = { Text(Repo.originName(om)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(keys.size.toString() + " canais nesta lista, de todas as categorias.")
                    TvButton("Cancelar", modifier = Modifier.fillMaxWidth(), requester = safe) { originMenu = null }
                    TvButton("Desativar os " + keys.size + " canais", modifier = Modifier.fillMaxWidth()) {
                        vm.setDisabled(keys, true)
                        originMenu = null
                    }
                    TvButton("Excluir os " + keys.size + " canais", modifier = Modifier.fillMaxWidth(), danger = true) {
                        vm.requestDelete(keys)
                        if (vm.origin.value == om) vm.origin.value = null
                        originMenu = null
                    }
                }
            },
            confirmButton = {}
        )
    }
    if (searchOpen) {
        AlertDialog(
            onDismissRequest = { searchOpen = false },
            title = { Text("Buscar canal") },
            text = {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    singleLine = true,
                    label = { Text("Nome do canal") }
                )
            },
            confirmButton = { TextButton(onClick = { searchOpen = false }) { Text("OK") } },
            dismissButton = {
                TextButton(onClick = {
                    vm.query.value = ""
                    searchOpen = false
                }) { Text("Limpar") }
            }
        )
    }
}

// ------------------------------------------------------------------------------ prévia do vídeo

@Composable
private fun LivePreview(vm: MainViewModel, modifier: Modifier, requester: FocusRequester) {
    val pc = vm.player
    val ch = pc.current
    val s by SettingsStore.flow.collectAsState()
    val states by Repo.states.collectAsState()
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier
            .focusRequester(requester)
            .tvClickable(shape) { vm.openFullscreen() }
            .clip(shape)
            .background(Color.Black)
    ) {
        if (ch == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LiveTv, null, tint = Color(0xFF8A94A6), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(6.dp))
                Text("Escolha um canal", color = Color(0xFFA7B0BE))
            }
        } else {
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
                    if (v.player !== pc.exo) v.player = pc.exo
                    v.resizeMode = when (s.resizeMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                onRelease = { v -> v.player = null }
            )
            val title = states[ch.key]?.customName?.takeIf { it.isNotBlank() } ?: ch.name
            Text(
                title,
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "Tela cheia",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x99000000))
                    .padding(4.dp)
                    .size(22.dp)
            )
            if (pc.status == PlayStatus.BUFFERING) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
            if (pc.status == PlayStatus.ERROR) {
                Text(
                    (pc.errorText ?: "Não foi possível reproduzir este canal.") + "\nAperte OK para ver as opções.",
                    Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6101419))
                        .padding(12.dp),
                    color = Color(0xFFD0D6E0),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------ dados técnicos

private fun codecName(mime: String?): String {
    val m = (mime ?: "").lowercase(Locale.ROOT)
    return when {
        m.isEmpty() -> "—"
        m.contains("avc") -> "AVC (H.264)"
        m.contains("hevc") -> "HEVC (H.265)"
        m.contains("av01") -> "AV1"
        m.contains("vp9") -> "VP9"
        m.contains("vp8") -> "VP8"
        m.contains("mpeg2") -> "MPEG-2"
        m.contains("mp4v") -> "MPEG-4"
        else -> m.substringAfter('/')
    }
}

@Composable
private fun TechStrip(vm: MainViewModel, modifier: Modifier) {
    val pc = vm.player
    var res by remember { mutableStateOf("—") }
    var codec by remember { mutableStateOf("—") }
    var bitrate by remember { mutableStateOf("—") }
    var fps by remember { mutableStateOf("—") }

    LaunchedEffect(pc.exo, pc.current?.key) {
        while (true) {
            val f = pc.exo?.videoFormat
            if (f != null) {
                res = if (f.width > 0 && f.height > 0) f.width.toString() + "x" + f.height else "—"
                codec = codecName(f.sampleMimeType)
                val br = if (f.bitrate > 0) f.bitrate else f.averageBitrate
                bitrate = if (br > 0) String.format(Locale.US, "%.1f Mbps", br / 1_000_000.0) else "—"
                fps = if (f.frameRate > 0f) Math.round(f.frameRate).toString() else "—"
            } else {
                res = "—"
                codec = "—"
                bitrate = "—"
                fps = "—"
            }
            delay(1000)
        }
    }

    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TechCell("Resolução", res)
        TechCell("Codec", codec)
        TechCell("Bitrate", bitrate)
        TechCell("FPS", fps)
    }
}

@Composable
private fun TechCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

// ------------------------------------------------------------------------------ programação

@Composable
private fun EpgPanel(vm: MainViewModel, modifier: Modifier) {
    val s by SettingsStore.flow.collectAsState()
    val ch = vm.player.current
    val version by Epg.version.collectAsState()
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            nowMs = System.currentTimeMillis()
        }
    }

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        if (ch == null) {
            Text("Escolha um canal para ver a programação.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        if (!s.epgEnabled) {
            Text("O guia de programação está desativado em Ajustes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val list = remember(ch.key, version, nowMs / 60_000L) {
            Epg.programmes(ch).filter { it.stop > nowMs }.take(40)
        }
        if (list.isEmpty()) {
            Text(
                "Sem programação para este canal. Nem todo canal consta nos guias.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.size) { i ->
                val p = list[i]
                val live = p.start <= nowMs && p.stop > nowMs
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        fmtClock(p.start) + "–" + fmtClock(p.stop),
                        Modifier.padding(end = 10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (live) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        p.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (live) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}


private fun androidx.compose.foundation.lazy.LazyListScope.tvRows(
    vm: MainViewModel,
    g: LiveGroup,
    current: com.willian.tvaberta.data.Channel?,
    indent: Int,
    onMenu: (ChannelItem) -> Unit,
) {
    val queue = g.items.map { it.ch }
    items(g.items, key = { "r:" + g.id + ":" + it.ch.key }) { item ->
        val isCur = current?.key == item.ch.key && vm.previewMode
        TvButton(
            (if (isCur) "▶ " else "") + item.title,
            modifier = Modifier.fillMaxWidth().padding(start = indent.dp),
            selected = isCur,
            onLongClick = { onMenu(item) }
        ) {
            vm.lastFocusKey = item.ch.key
            if (isCur) vm.openFullscreen() else vm.playPreview(item.ch, queue)
        }
    }
    if (g.hasMore) {
        item(key = "more:" + g.id) {
            LaunchedEffect(g.items.size) { vm.loadMoreGroup() }
            Text(
                "Carregando mais…",
                Modifier.padding(start = indent.dp, top = 6.dp, bottom = 6.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
