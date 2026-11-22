package com.willian.tvaberta.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel

private class MoreItem(val icon: ImageVector, val title: String, val sub: String, val tab: Int)

@Composable
fun MoreScreen(vm: MainViewModel) {
    val favs by vm.favorites.collectAsState()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val items = listOf(
        MoreItem(Icons.Default.Schedule, "Guia de programação", "Agora e o que vem a seguir", 1),
        MoreItem(Icons.Default.Star, "Favoritos", if (favs.isEmpty()) "Nenhum ainda" else favs.size.toString() + " itens", 2),
        MoreItem(Icons.Default.Tune, "Gerenciar", "Listas, pacotes, categorias e backup", 3),
        MoreItem(Icons.Default.Settings, "Ajustes", "Tema, cor, player e filtros", 4),
    )
    val open: (MoreItem) -> Unit = {
        if (it.tab == 4) vm.settingsPage = null
        vm.tab = it.tab
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        ScreenHeader("Mais") {}
        if (landscape) {
            items.chunked(2).forEach { pair ->
                Row(Modifier.padding(horizontal = 8.dp)) {
                    pair.forEach { Box(Modifier.weight(1f)) { MoreRow(it) { open(it) } } }
                    if (pair.size == 1) Box(Modifier.weight(1f)) {}
                }
            }
        } else {
            items.forEach { MoreRow(it) { open(it) } }
        }
    }
}

@Composable
private fun MoreRow(item: MoreItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(item.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(item.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyLarge)
            Text(item.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
