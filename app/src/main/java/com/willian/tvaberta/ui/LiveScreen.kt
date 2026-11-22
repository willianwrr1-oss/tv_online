package com.willian.tvaberta.ui

import android.view.ViewGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * TV ao vivo: lista de grupos que se abrem para mostrar os canais, pré-visualização com
 * dados técnicos e programação. Em paisagem fica lado a lado; em retrato, o vídeo vai no topo.
 */
@Composable
fun LiveScreen(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    val query by vm.query.collectAsState()
    val refreshing by Repo.refreshing.collectAsState()

    val model by vm.liveModel.collectAsState()
    val groups = model.groups
    val origins = model.origins
    val org = model.org
    val total = model.total
    val area = vm.area
    val areaTitle = when (area) { 1 -> "Filmes"; 2 -> "Séries"; else -> "TV ao vivo" }
    val unit = when (area) { 1 -> "filme"; 2 -> "série"; else -> "canal" }
    val countText = if (total == 1) "1 $unit" else "$total ${unit}s"

    var lowerTab by remember { mutableStateOf(0) }
    val ch = vm.player.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.42f).fillMaxHeight()) {
                    GroupsPane(vm, groups, total, origins, org, query, s.sortMode, showHeader = false, refreshing = refreshing)
                }
                BoxWithConstraints(
                    Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .padding(8.dp)
                ) {
                    val ph = minOf(maxWidth * 9f / 16f, maxHeight * 0.55f)
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().height(ph), contentAlignment = Alignment.Center) {
                            LivePreview(vm, Modifier.fillMaxHeight().aspectRatio(16f / 9f))
                        }
                        Spacer(Modifier.height(8.dp))
                        TechStrip(vm, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        EpgPanel(vm, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                ScreenHeader(areaTitle, countText) {
                    IconButton(onClick = { vm.openArea(null) }) {
                        Icon(Icons.Default.Home, contentDescription = "Voltar ao início")
                    }
                    IconButton(onClick = { vm.refresh(true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar lista de canais")
                    }
                }
                LivePreview(
                    vm,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .aspectRatio(16f / 9f)
                )
                if (ch != null) {
                    Text(
                        ch.name,
                        Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = lowerTab == 0, onClick = { lowerTab = 0 }, label = { Text("Canais") })
                    if (area == 0) FilterChip(selected = lowerTab == 1, onClick = { lowerTab = 1 }, label = { Text("Programação e dados") })
                }
                if (lowerTab == 0 || area != 0) {
                    GroupsPane(vm, groups, total, origins, org, query, s.sortMode, showHeader = true, refreshing = refreshing)
                } else {
                    Column(Modifier.fillMaxSize().padding(12.dp)) {
                        TechStrip(vm, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        EpgPanel(vm, Modifier.weight(1f).fillMaxWidth())
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------ lista de grupos

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupsPane(
    vm: MainViewModel,
    groups: List<LiveGroup>,
    total: Int,
    origins: List<String>,
    org: String?,
    query: String,
    sortMode: Int,
    showHeader: Boolean,
    refreshing: Boolean,
) {
    val s by SettingsStore.flow.collectAsState()
    val actions = rememberActions(vm)
    val open = vm.liveGroup
    val state = rememberLazyListState()
    var menuGroup by remember { mutableStateOf<String?>(null) }
    val current = vm.player.current

    LaunchedEffect(open) {
        val i = groups.indexOfFirst { it.id == open }
        if (i >= 0) runCatching { state.animateScrollToItem(i) }
    }

    Column(Modifier.fillMaxSize()) {
        if (!showHeader) {
            Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        when (vm.area) { 1 -> "Filmes"; 2 -> "Séries"; else -> "TV ao vivo" },
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        if (total == 1) "1 item" else "$total itens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { vm.openArea(null) }) {
                    Icon(Icons.Default.Home, contentDescription = "Voltar ao início")
                }
                IconButton(onClick = { vm.refresh(true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar lista de canais")
                }
            }
        }
        if (refreshing) {
            androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(when (vm.area) { 1 -> "Buscar filme"; 2 -> "Buscar série"; else -> "Buscar canal" }) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { vm.query.value = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                    }
                }
            }
        )
        if (origins.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = org == null,
                        onClick = { vm.origin.value = null },
                        label = { Text("Todas as listas") }
                    )
                }
                items(origins, key = { it }) { o ->
                    FilterChip(
                        selected = org == o,
                        onClick = { vm.origin.value = if (org == o) null else o },
                        label = { Text(Repo.originName(o)) }
                    )
                }
            }
        }

        if (groups.isEmpty()) {
            EmptyState(
                icon = Icons.Default.LiveTv,
                title = if (refreshing || !vm.liveModel.collectAsState().value.loaded) "Carregando canais…" else "Nenhum canal",
                text = "Baixe a lista de canais ou mude a busca. Canais desativados ou excluídos ficam em Gerenciar.",
                actionLabel = if (refreshing) null else "Atualizar lista",
                onAction = { vm.refresh(true) }
            )
        } else {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                groups.forEach { g ->
                    item(key = "h:" + g.id) {
                        GroupHeader(
                            label = g.label,
                            count = g.count,
                            expanded = open == g.id,
                            onClick = {
                                vm.liveSub = null
                                vm.liveGroup = if (open == g.id) null else g.id
                            },
                            onLong = { if (g.isCategory) menuGroup = g.id }
                        )
                    }
                    if (open == g.id) {
                        if (g.children.isNotEmpty()) {
                            g.children.forEach { c ->
                                val subOpen = vm.liveSub == c.id
                                item(key = "h:" + c.id) {
                                    Box(Modifier.padding(start = 10.dp)) {
                                        GroupHeader(
                                            label = c.label,
                                            count = c.count,
                                            expanded = subOpen,
                                            onClick = { vm.liveSub = if (subOpen) null else c.id },
                                            onLong = {}
                                        )
                                    }
                                }
                                if (subOpen) rows(vm, c, current, s, actions, 22)
                            }
                        } else {
                            rows(vm, g, current, s, actions, 10)
                        }
                    }
                }
            }
        }
    }

    val mg = groups.firstOrNull { it.id == menuGroup }
    if (mg != null) {
        val keys by produceState(emptyList<String>(), mg.id) { value = vm.keysOfGroup(mg.id) }
        AlertDialog(
            onDismissRequest = { menuGroup = null },
            title = { Text(mg.label) },
            text = { Text(keys.size.toString() + " canais nesta categoria" + if (org != null) " (na lista escolhida)." else ".") },
            confirmButton = {
                TextButton(onClick = {
                    vm.requestDelete(keys)
                    if (vm.liveGroup == mg.id) vm.liveGroup = null
                    menuGroup = null
                }) { Text("Excluir os " + keys.size, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { menuGroup = null }) { Text("Cancelar") }
                    TextButton(onClick = {
                        vm.setDisabled(keys, true)
                        menuGroup = null
                    }) { Text("Desativar") }
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GroupHeader(label: String, count: Int, expanded: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (expanded) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

// ------------------------------------------------------------------------------ prévia do vídeo

@Composable
private fun LivePreview(vm: MainViewModel, modifier: Modifier) {
    val pc = vm.player
    val ch = pc.current
    val s by SettingsStore.flow.collectAsState()
    val fs = vm.fullscreen
    val states by Repo.states.collectAsState()

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
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
                    // Enquanto a tela cheia está aberta, a prévia solta o player para ele usar a imagem.
                    val target = if (fs) null else pc.exo
                    if (v.player !== target) v.player = target
                    v.resizeMode = when (s.resizeMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                }
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.openFullscreen() }
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
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6101419))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        pc.errorText ?: "Não foi possível reproduzir este canal.",
                        color = Color(0xFFD0D6E0),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pc.retry() }) { Text("Tentar de novo") }
                        OutlinedButton(onClick = { pc.next() }) { Text("Próximo", color = Color.White) }
                    }
                }
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


private fun androidx.compose.foundation.lazy.LazyListScope.rows(
    vm: MainViewModel,
    g: LiveGroup,
    current: com.willian.tvaberta.data.Channel?,
    s: com.willian.tvaberta.data.AppSettings,
    actions: ChannelActions,
    indent: Int,
) {
    val queue = g.items.map { it.ch }
    items(g.items, key = { "r:" + g.id + ":" + it.ch.key }) { item ->
        val isCur = current?.key == item.ch.key && vm.previewMode
        Box(
            Modifier
                .padding(start = indent.dp)
                .then(
                    if (isCur) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    else Modifier
                )
        ) {
            ChannelListRow(item, s, actions) {
                if (isCur) vm.openFullscreen() else vm.playPreview(item.ch, queue)
            }
        }
    }
    if (g.hasMore) {
        item(key = "more:" + g.id) {
            LaunchedEffect(g.items.size) { vm.loadMoreGroup() }
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            }
        }
    }
}
