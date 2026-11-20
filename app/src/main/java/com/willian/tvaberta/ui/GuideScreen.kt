package com.willian.tvaberta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.Programme
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore

private const val DP_PER_MIN = 4f
private const val WINDOW_MIN = 24 * 60
private const val BACK_MIN = 60
private val NAME_W = 92.dp

/** Grade de programação: canais nas linhas, horários nas colunas, rolando para os lados. */
@Composable
fun GuideScreen(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    val all by vm.items.collectAsState()
    val version by Epg.version.collectAsState()
    val loading by Epg.loading.collectAsState()

    val now = System.currentTimeMillis()
    // Início da janela: 1 h antes de agora, arredondado para a meia hora.
    val start = remember(version, now / 600_000L) {
        val base = now - BACK_MIN * 60_000L
        base - (base % 1_800_000L)
    }
    val end = start + WINDOW_MIN * 60_000L

    val rows = remember(all, version, s.hideGeoBlocked, s.hideNot247, s.epgEnabled) {
        if (!s.epgEnabled) emptyList()
        else all.filter { isListed(it, s) && Epg.programmes(it.ch).isNotEmpty() }
            .sortedBy { it.title.lowercase() }
    }
    val queue: List<Channel> = remember(rows) { rows.map { it.ch } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Guia", "Programação dos canais") {
            if (loading) {
                CircularProgressIndicator(Modifier.padding(12.dp).width(22.dp).height(22.dp), strokeWidth = 2.dp)
            } else if (s.epgEnabled) {
                IconButton(onClick = { vm.refreshEpg(true) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Atualizar guia")
                }
            }
        }

        if (!s.epgEnabled) {
            EmptyState(
                Icons.Default.Schedule,
                "Guia desativado",
                "Ative o guia de programação para ver o que está passando em cada canal.",
                "Ativar guia"
            ) {
                SettingsStore.update { it.copy(epgEnabled = true) }
                vm.refreshEpg(true)
            }
            return@Column
        }
        if (rows.isEmpty()) {
            EmptyState(
                Icons.Default.Schedule,
                if (loading) "Baixando o guia..." else "Sem programação",
                "Nenhum dos seus canais tem programação no guia carregado. Toque em atualizar ou " +
                    "troque o link em Ajustes > Guia de programação. Nem todo canal consta nos guias.",
                if (loading) null else "Atualizar guia"
            ) { vm.refreshEpg(true) }
            return@Column
        }

        val hScroll = rememberScrollState()
        val density = LocalDensity.current
        LaunchedEffect(start) {
            // Já abre perto do horário de agora.
            val px = with(density) { ((BACK_MIN - 15) * DP_PER_MIN).dp.toPx() }
            hScroll.scrollTo(px.toInt().coerceAtLeast(0))
        }
        val totalW = (WINDOW_MIN * DP_PER_MIN).dp

        // Régua de horas
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(NAME_W))
            Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                Box(Modifier.width(totalW).height(26.dp)) {
                    var m = 0
                    while (m < WINDOW_MIN) {
                        Text(
                            fmtClock(start + m * 60_000L),
                            modifier = Modifier
                                .padding(start = (m * DP_PER_MIN).dp + 4.dp, top = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        m += 30
                    }
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.ch.key }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        modifier = Modifier.width(NAME_W).padding(horizontal = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        ProgrammeRow(Epg.programmes(item.ch), start, end, now) { vm.play(item.ch, queue) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeRow(list: List<Programme>, start: Long, end: Long, now: Long, onPlay: () -> Unit) {
    val totalW = (WINDOW_MIN * DP_PER_MIN).dp
    Row(Modifier.width(totalW).height(60.dp)) {
        var cursor = start
        for (p in list) {
            if (p.stop <= start) continue
            if (p.start >= end) break
            val from = maxOf(p.start, start)
            val to = minOf(p.stop, end)
            if (to <= cursor) continue
            val realFrom = maxOf(from, cursor)
            val gap = (realFrom - cursor) / 60_000f
            if (gap > 0.5f) Box(Modifier.width((gap * DP_PER_MIN).dp))
            val w = ((to - realFrom) / 60_000f * DP_PER_MIN).coerceAtLeast(8f)
            val live = p.start <= now && p.stop > now
            Column(
                Modifier
                    .width(w.dp)
                    .height(60.dp)
                    .padding(end = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (live) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        1.dp,
                        if (live) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(onClick = onPlay)
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    p.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    fmtClock(p.start) + "–" + fmtClock(p.stop) + (p.category?.let { " · $it" } ?: ""),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            cursor = to
        }
    }
}
