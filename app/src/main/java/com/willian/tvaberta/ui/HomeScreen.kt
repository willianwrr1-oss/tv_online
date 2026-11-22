package com.willian.tvaberta.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.ChannelItem

private fun fmt(n: Int): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR")).format(n)

private fun hues(seed: String): Pair<Color, Color> {
    val h = ((seed.hashCode() and 0x7fffffff) % 360).toFloat()
    return Pair(Color.hsv(h, 0.38f, 0.30f), Color.hsv(h, 0.42f, 0.16f))
}

private fun initials(name: String): String =
    name.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun HomeScreen(vm: MainViewModel) {
    val c by vm.homeCounts.collectAsState()
    val cont by vm.continueWatching.collectAsState()
    val favs by vm.favorites.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val feat = cont.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenHeader("TV Aberta", if (c == null) "Carregando…" else fmt((c?.live ?: 0) + (c?.movies ?: 0) + (c?.series ?: 0)) + " itens") {}

        val hero: @Composable (Modifier) -> Unit = { m ->
            Hero(feat, m, onPlay = { if (feat != null) vm.play(feat.ch, cont.map { it.ch }) else vm.openArea(0) })
        }
        val areas: @Composable (Boolean) -> Unit = { vertical ->
            val a: List<Triple<ImageVector, String, String>> = listOf(
                Triple(Icons.Default.LiveTv, "TV ao vivo", if (c == null) "" else fmt(c!!.live) + " canais"),
                Triple(Icons.Default.Movie, "Filmes", if (c == null) "" else fmt(c!!.movies)),
                Triple(Icons.Default.Tv, "Séries", if (c == null) "" else fmt(c!!.series)),
            )
            if (vertical) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.forEachIndexed { i, t -> AreaCard(t.first, t.second, t.third, Modifier.fillMaxWidth(), true) { vm.openArea(i) } }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    a.forEachIndexed { i, t -> AreaCard(t.first, t.second, t.third, Modifier.weight(1f), false) { vm.openArea(i) } }
                }
            }
        }

        if (landscape) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                hero(Modifier.weight(1f).height(150.dp))
                Box(Modifier.width(210.dp)) { areas(true) }
            }
        } else {
            Box(Modifier.padding(horizontal = 16.dp)) { hero(Modifier.fillMaxWidth().height(176.dp)) }
            Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) { areas(false) }
        }

        if (cont.isNotEmpty()) {
            SectionTitle("Continuar assistindo")
            ThumbRow(cont) { vm.play(it.ch, cont.map { x -> x.ch }) }
        }
        if (favs.isNotEmpty()) {
            SectionTitle("Favoritos")
            ThumbRow(favs.take(30)) { vm.play(it.ch, favs.map { x -> x.ch }) }
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(
        t,
        Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp),
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun Hero(feat: ChannelItem?, modifier: Modifier, onPlay: () -> Unit) {
    val (c1, c2) = hues(feat?.title ?: "TV Aberta")
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(c1, c2)))) {
        if (feat != null && !feat.ch.logo.isNullOrBlank()) {
            AsyncImage(
                model = feat.ch.logo, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.CenterEnd).padding(20.dp).size(84.dp)
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x1A0E1116), Color(0xEB0E1116)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                if (feat != null) "CONTINUAR ASSISTINDO" else "BEM-VINDO",
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary
            )
            Text(
                feat?.title ?: "TV Aberta",
                Modifier.padding(top = 2.dp, bottom = 8.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Button(onClick = onPlay, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(16.dp))
                Text(if (feat != null) "Assistir" else "Abrir TV ao vivo", Modifier.padding(start = 4.dp), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AreaCard(icon: ImageVector, title: String, sub: String, modifier: Modifier, row: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val body: @Composable () -> Unit = {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(Modifier.padding(start = if (row) 10.dp else 0.dp, top = if (row) 0.dp else 8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
            if (sub.isNotEmpty()) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
    if (row) {
        Row(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { body() }
    } else {
        Column(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp)) { body() }
    }
}

@Composable
private fun ThumbRow(list: List<ChannelItem>, onClick: (ChannelItem) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it.ch.key }) { it -> Thumb(it, 140.dp) { onClick(it) } }
    }
}

@Composable
private fun Thumb(item: ChannelItem, w: Dp, onClick: () -> Unit) {
    val (c1, c2) = hues(item.title)
    val shape = RoundedCornerShape(10.dp)
    Column(Modifier.width(w).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(w * 9f / 16f).clip(shape).background(Brush.linearGradient(listOf(c1, c2))),
            contentAlignment = Alignment.Center
        ) {
            if (!item.ch.logo.isNullOrBlank()) {
                AsyncImage(
                    model = item.ch.logo, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(10.dp)
                )
            } else {
                Text(initials(item.title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color(0xB8FFFFFF))
            }
        }
        Text(item.title, Modifier.padding(top = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            catLabel(item.ch.groups.firstOrNull().orEmpty()),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}
