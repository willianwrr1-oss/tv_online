package com.willian.tvaberta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import kotlinx.coroutines.delay

private fun fmt(n: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR")).format(n)

@Composable
fun TvHomeScreen(vm: MainViewModel) {
    val c by vm.homeCounts.collectAsState()
    val cont by vm.continueWatching.collectAsState()
    val first = remember { FocusRequester() }
    val all = (c?.live ?: 0) + (c?.movies ?: 0) + (c?.series ?: 0)

    LaunchedEffect(c != null) {
        delay(200)
        if (vm.focusPending) {
            runCatching { first.requestFocus() }
            vm.focusPending = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        Text("TV Aberta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            if (c == null) "Carregando…" else fmt(all) + " itens",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        TvButton(
            "TV ao vivo   " + (if (c == null) "" else fmt(c!!.live) + " canais · " + c!!.liveCats + " categorias"),
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Default.LiveTv,
            requester = first
        ) { vm.openArea(0) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton(
                "Filmes   " + (if (c == null) "" else fmt(c!!.movies)),
                modifier = Modifier.weight(1f), icon = Icons.Default.Movie
            ) { vm.openArea(1) }
            TvButton(
                "Séries   " + (if (c == null) "" else fmt(c!!.series)),
                modifier = Modifier.weight(1f), icon = Icons.Default.Tv
            ) { vm.openArea(2) }
        }
        if (cont.isNotEmpty()) {
            Text(
                "Continuar assistindo",
                Modifier.padding(top = 22.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cont, key = { it.ch.key }) { it ->
                    TvButton(it.title.take(26)) { vm.play(it.ch, cont.map { x -> x.ch }) }
                }
            }
        }
    }
}
