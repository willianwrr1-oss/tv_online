package com.willian.tvaberta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore

@Composable
fun rememberActions(vm: MainViewModel): ChannelActions {
    val clip = LocalClipboardManager.current
    return remember(vm, clip) {
        ChannelActions(
            toggleFavorite = { vm.toggleFavorite(it.ch.key) },
            disable = { vm.setDisabled(listOf(it.ch.key), true) },
            enable = { vm.setDisabled(listOf(it.ch.key), false) },
            delete = { vm.requestDelete(listOf(it.ch.key)) },
            rename = { vm.renameTarget = it },
            copyLink = {
                clip.setText(AnnotatedString(it.ch.urls.first()))
                vm.toast("Link copiado.")
            },
        )
    }
}

@Composable
fun ChannelsScreen(vm: MainViewModel, favoritesOnly: Boolean) {
    val all by vm.items.collectAsState()
    val s by SettingsStore.flow.collectAsState()
    val query by vm.query.collectAsState()
    val category by vm.category.collectAsState()
    val refreshing by Repo.refreshing.collectAsState()
    val actions = rememberActions(vm)
    var catMenu by remember { mutableStateOf<String?>(null) }
    var originMenu by remember { mutableStateOf<String?>(null) }

    val origin by vm.origin.collectAsState()
    val baseAll = remember(all, s) {
        val listed = all.filter { isListed(it, s) }
        if (favoritesOnly) listed.filter { it.st.favorite } else listed
    }
    val origins = remember(baseAll) {
        baseAll.map { it.ch.sourceId }.distinct().sortedBy { Repo.originName(it).lowercase() }
    }
    val org = origin?.takeIf { it in origins }
    val base = remember(baseAll, org) {
        if (org == null) baseAll else baseAll.filter { it.ch.sourceId == org }
    }
    val cats = remember(base) { categoriesOf(base) }
    val cat = category?.takeIf { it in cats }
    val visible: List<ChannelItem> = remember(base, query, cat, s.sortMode) {
        val q = query.trim()
        val filtered = base.filter { item ->
            (cat == null || cat in item.ch.groups) &&
                (q.isEmpty() || item.title.contains(q, ignoreCase = true))
        }
        sortItems(filtered, s.sortMode)
    }
    val queue = remember(visible) { visible.map { it.ch } }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            title = if (favoritesOnly) "Favoritos" else "TV Aberta",
            subtitle = if (visible.size == 1) "1 canal" else "${visible.size} canais",
        ) {
            IconButton(onClick = { SettingsStore.update { it.copy(gridLayout = !it.gridLayout) } }) {
                Icon(
                    if (s.gridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Alternar entre grade e lista"
                )
            }
            IconButton(onClick = { vm.refresh(true) }) {
                Icon(Icons.Default.Refresh, contentDescription = "Atualizar lista de canais")
            }
        }
        if (refreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Buscar canal") },
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
                    LongPressBox(onClick = { vm.origin.value = if (org == o) null else o }, onLong = { originMenu = o }) {
                        FilterChip(
                            selected = org == o,
                            onClick = { vm.origin.value = if (org == o) null else o },
                            label = { Text(Repo.originName(o)) }
                        )
                    }
                }
            }
        }

        if (s.showCategoryChips && cats.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = cat == null,
                        onClick = { vm.category.value = null },
                        label = { Text("Todos") }
                    )
                }
                items(cats, key = { it }) { c ->
                    LongPressBox(onClick = { vm.category.value = if (cat == c) null else c }, onLong = { catMenu = c }) {
                        FilterChip(
                            selected = cat == c,
                            onClick = { vm.category.value = if (cat == c) null else c },
                            label = { Text(catLabel(c)) }
                        )
                    }
                }
            }
        }

        val om = originMenu
        if (om != null) {
            val keys = baseAll.filter { it.ch.sourceId == om }.map { it.ch.key }
            AlertDialog(
                onDismissRequest = { originMenu = null },
                title = { Text(Repo.originName(om)) },
                text = { Text(keys.size.toString() + " canais nesta lista, de todas as categorias.") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.requestDelete(keys)
                        if (vm.origin.value == om) vm.origin.value = null
                        originMenu = null
                    }) { Text("Excluir os " + keys.size, color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { originMenu = null }) { Text("Cancelar") }
                        TextButton(onClick = {
                            vm.setDisabled(keys, true)
                            originMenu = null
                        }) { Text("Desativar") }
                    }
                }
            )
        }
        val cm = catMenu
        if (cm != null) {
            val keys = base.filter { cm in it.ch.groups }.map { it.ch.key }
            AlertDialog(
                onDismissRequest = { catMenu = null },
                title = { Text(catLabel(cm)) },
                text = { Text(keys.size.toString() + " canais nesta categoria" + if (org != null) " (na lista escolhida)." else ".") },
                confirmButton = {
                    TextButton(onClick = {
                        vm.requestDelete(keys)
                        if (vm.category.value == cm) vm.category.value = null
                        catMenu = null
                    }) { Text("Excluir os " + keys.size, color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { catMenu = null }) { Text("Cancelar") }
                        TextButton(onClick = {
                            vm.setDisabled(keys, true)
                            catMenu = null
                        }) { Text("Desativar") }
                    }
                }
            )
        }

        when {
            visible.isNotEmpty() && s.gridLayout -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(s.gridColumns.coerceIn(2, 4)),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.ch.key }) { item ->
                        ChannelGridCard(item, s, actions) { vm.play(item.ch, queue) }
                    }
                }
            }
            visible.isNotEmpty() -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (s.compactList) 6.dp else 10.dp)
                ) {
                    items(visible, key = { it.ch.key }) { item ->
                        ChannelListRow(item, s, actions) { vm.play(item.ch, queue) }
                    }
                }
            }
            favoritesOnly && all.isNotEmpty() && query.isBlank() -> {
                EmptyState(
                    icon = Icons.Default.Star,
                    title = "Nenhum favorito ainda",
                    text = "Abra o menu ⋮ de um canal e toque em Favoritar para vê-lo aqui."
                )
            }
            all.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.LiveTv,
                    title = if (refreshing) "Carregando canais…" else "Nenhum canal carregado",
                    text = "Baixe a lista de canais abertos para começar. Precisa de internet.",
                    actionLabel = if (refreshing) null else "Atualizar lista",
                    onAction = { vm.refresh(true) }
                )
            }
            else -> {
                EmptyState(
                    icon = Icons.Default.Search,
                    title = "Nenhum canal encontrado",
                    text = "Mude a busca ou a categoria. Canais desativados ou excluídos ficam em Gerenciar."
                )
            }
        }
    }
}

/** Envolve um chip e detecta o toque longo por cima dele (o chip sozinho consome o toque). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LongPressBox(onClick: () -> Unit, onLong: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Box {
        content()
        Box(
            Modifier
                .matchParentSize()
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLong()
                    }
                )
        )
    }
}
