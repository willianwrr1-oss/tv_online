package com.willian.tvaberta.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.Programme
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.delay

private const val DP_PER_MIN = 5f
private const val WINDOW_MIN = 24 * 60
private const val BACK_MIN = 60
private val NAME_W = 190.dp

/** Grade de programação para controle remoto: setas movem entre programas, OK abre o canal. */
@Composable
fun TvGuideScreen(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    val dataV by Repo.dataVersion.collectAsState()
    val version by Epg.version.collectAsState()
    val loading by Epg.loading.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    // Ao abrir o guia, atualiza sozinho se os dados estiverem velhos (mais de 3 horas) ou ausentes.
    LaunchedEffect(Unit) {
        val last = SettingsStore.value.lastEpgRefresh
        if (s.epgEnabled && (System.currentTimeMillis() - last > 3 * 3_600_000L)) vm.refreshEpg(false)
    }
    val start = remember(version, now / 600_000L) {
        val base = now - BACK_MIN * 60_000L
        base - (base % 1_800_000L)
    }
    val end = start + WINDOW_MIN * 60_000L

    val rows by produceState(
        emptyList<ChannelItem>(), dataV, version, s.hideGeoBlocked, s.hideNot247, s.epgEnabled
    ) {
        value = withContext(Dispatchers.Default) {
            if (!s.epgEnabled) emptyList()
            else {
                val out = ArrayList<ChannelItem>()
                Repo.db.forEachLive(Repo.flt(s)) { ch ->
                    if (Epg.programmes(ch).isNotEmpty()) out.add(Repo.item(ch))
                    out.size < 800
                }
                out
            }
        }
    }
    val queue: List<Channel> = remember(rows) { rows.map { it.ch } }

    var focusedProg by remember { mutableStateOf<Programme?>(null) }
    var focusedName by remember { mutableStateOf("") }
    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val restore = remember { FocusRequester() }
    val refreshReq = remember { FocusRequester() }

    val target = if (vm.focusPending) rows.indexOfFirst { it.ch.key == vm.lastFocusKey }.let { if (it < 0) 0 else it } else -1

    LaunchedEffect(start) {
        val px = with(density) { ((BACK_MIN - 15) * DP_PER_MIN).dp.toPx() }
        hScroll.scrollTo(px.toInt().coerceAtLeast(0))
    }
    LaunchedEffect(rows.size, vm.focusPending) {
        if (!vm.focusPending) return@LaunchedEffect
        if (rows.isEmpty()) {
            delay(200)
            runCatching { refreshReq.requestFocus() }
            vm.focusPending = false
            return@LaunchedEffect
        }
        val idx = rows.indexOfFirst { it.ch.key == vm.lastFocusKey }.let { if (it < 0) 0 else it }
        runCatching { listState.scrollToItem(idx) }
        delay(200)
        runCatching { restore.requestFocus() }
        vm.focusPending = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TvTitle("Guia de programação", if (loading) "Baixando o guia..." else "${rows.size} canais com programação")
            }
            TvButton("Atualizar guia", icon = Icons.Default.Refresh, requester = refreshReq) { vm.refreshEpg(true) }
        }

        if (!s.epgEnabled) {
            TvEmpty("Guia desativado", "Ative o guia de programação para ver o que passa em cada canal.", "Ativar guia") {
                SettingsStore.update { it.copy(epgEnabled = true) }
                vm.refreshEpg(true)
            }
            return@Column
        }
        if (rows.isEmpty()) {
            TvEmpty(
                if (loading) "Baixando o guia..." else "Sem programação",
                "Nenhum dos seus canais tem programação no guia carregado. Atualize ou troque o link em " +
                    "Ajustes > Guia de programação. Nem todo canal consta nos guias."
            )
            return@Column
        }

        // Detalhe do programa em foco
        val fp = focusedProg
        Column(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .padding(bottom = 6.dp)
        ) {
            if (fp != null) {
                Text(
                    fp.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    focusedName + " · " + fmtClock(fp.start) + "–" + fmtClock(fp.stop) + (fp.category?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    fp.desc ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    "Use as setas para navegar e OK para assistir.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Régua de horas
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(NAME_W))
            Box(
                Modifier
                    .weight(1f)
                    .horizontalScroll(hScroll)
            ) {
                Box(Modifier.width((WINDOW_MIN * DP_PER_MIN).dp).height(26.dp)) {
                    var m = 0
                    while (m < WINDOW_MIN) {
                        Text(
                            fmtClock(start + m * 60_000L),
                            modifier = Modifier.padding(start = (m * DP_PER_MIN).dp + 4.dp, top = 2.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        m += 30
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), state = listState) {
            itemsIndexed(rows, key = { _, it -> it.ch.key }) { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(Modifier.width(NAME_W), verticalAlignment = Alignment.CenterVertically) {
                        ChannelLogo(item, s.showLogos, Modifier.size(44.dp))
                        Text(
                            item.title,
                            modifier = Modifier.padding(horizontal = 8.dp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .horizontalScroll(hScroll)
                    ) {
                        TvProgrammeRow(
                            list = Epg.programmes(item.ch),
                            start = start,
                            end = end,
                            now = now,
                            requester = if (index == target) restore else null,
                            onFocus = { p ->
                                focusedProg = p
                                focusedName = item.title
                                vm.lastFocusKey = item.ch.key
                            },
                            onPlay = { vm.play(item.ch, queue) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvProgrammeRow(
    list: List<Programme>,
    start: Long,
    end: Long,
    now: Long,
    requester: FocusRequester?,
    onFocus: (Programme) -> Unit,
    onPlay: () -> Unit,
) {
    val totalW = (WINDOW_MIN * DP_PER_MIN).dp
    val shape = RoundedCornerShape(10.dp)
    Row(Modifier.width(totalW).height(66.dp)) {
        var cursor = start
        // O foco volta para o programa que está no ar; se não houver, para o primeiro da linha.
        val liveIdx = list.indexOfFirst { it.start <= now && it.stop > now }
        val focusIdx = if (liveIdx >= 0) liveIdx else list.indexOfFirst { it.stop > start }
        list.forEachIndexed { idx, p ->
            if (p.stop <= start || p.start >= end) return@forEachIndexed
            val from = maxOf(p.start, start)
            val to = minOf(p.stop, end)
            if (to <= cursor) return@forEachIndexed
            val realFrom = maxOf(from, cursor)
            val gap = (realFrom - cursor) / 60_000f
            if (gap > 0.5f) Box(Modifier.width((gap * DP_PER_MIN).dp))
            val w = ((to - realFrom) / 60_000f * DP_PER_MIN).coerceAtLeast(10f)
            val live = p.start <= now && p.stop > now
            Column(
                Modifier
                    .width(w.dp)
                    .height(66.dp)
                    .padding(end = 3.dp)
                    .then(if (requester != null && idx == focusIdx) Modifier.focusRequester(requester) else Modifier)
                    .tvClickable(shape, onFocus = { if (it) onFocus(p) }, onClick = onPlay)
                    .background(
                        if (live) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    p.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    fmtClock(p.start) + "–" + fmtClock(p.stop),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            cursor = to
        }
    }
}
