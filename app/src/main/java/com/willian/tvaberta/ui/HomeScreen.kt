package com.willian.tvaberta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel

private fun fmt(n: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR")).format(n)

@Composable
fun HomeScreen(vm: MainViewModel) {
    val c by vm.homeCounts.collectAsState()
    val cont by vm.continueWatching.collectAsState()
    val all = (c?.live ?: 0) + (c?.movies ?: 0) + (c?.series ?: 0)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenHeader("TV Aberta", if (c == null) "Carregando…" else fmt(all) + " itens") {}

        HomeCard(
            Icons.Default.LiveTv, "TV ao vivo",
            if (c == null) "" else fmt(c!!.live) + " canais · " + c!!.liveCats + " categorias",
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) { vm.openArea(0) }
        Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeCard(Icons.Default.Movie, "Filmes", if (c == null) "" else fmt(c!!.movies), Modifier.weight(1f)) { vm.openArea(1) }
            HomeCard(Icons.Default.Tv, "Séries", if (c == null) "" else fmt(c!!.series), Modifier.weight(1f)) { vm.openArea(2) }
        }

        if (cont.isNotEmpty()) {
            Text(
                "Continuar assistindo",
                Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(cont, key = { it.ch.key }) { it ->
                    Column(
                        Modifier
                            .width(150.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { vm.play(it.ch, cont.map { x -> x.ch }) }
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(it.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCard(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, Modifier.padding(end = 14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall)
        }
    }
}
