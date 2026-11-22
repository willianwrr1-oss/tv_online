package com.willian.tvaberta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.delay

@Composable
fun TvChannelsScreen(vm: MainViewModel, favoritesOnly: Boolean) {
    val s by SettingsStore.flow.collectAsState()
    val query by vm.query.collectAsState()
    val category by vm.category.collectAsState()
    val refreshing by Repo.refreshing.collectAsState()

    val origin by vm.origin.collectAsState()
    val baseAll by vm.favorites.collectAsState()
    val origins = remember(baseAll) {
        baseAll.map { it.ch.sourceId }.distinct().sortedBy { Repo.originName(it).lowercase() }
    }
    val org = origin?.takeIf { it in origins }
    val base = remember(baseAll, org) {
        if (org == null) baseAll else baseAll.filter { it.ch.sourceId == org }
    }
    val cats = remember(base) { categoriesOf(base) }
    val cat = category?.takeIf { it in cats }
    val visible: List<ChannelItem> = remember(base, cat) {
        if (cat == null) base else base.filter { cat in it.ch.groups }
    }
    val queue = remember(visible) { visible.map { it.ch } }

    val grid = rememberLazyGridState()
    val focusReq = remember { FocusRequester() }
    val headerReq = remember { FocusRequester() }
    var menuTarget by remember { mutableStateOf<ChannelItem?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf<String?>(null) }
    var originMenu by remember { mutableStateOf<String?>(null) }

    // Índice que recebe o foco ao abrir a tela ou ao voltar do player.
    val target = if (vm.focusPending) visible.indexOfFirst { it.ch.key == vm.lastFocusKey }.let { if (it < 0) 0 else it } else -1

    LaunchedEffect(visible.size, vm.focusPending) {
        if (!vm.focusPending) return@LaunchedEffect
        if (visible.isEmpty()) {
            delay(200)
            runCatching { headerReq.requestFocus() }
            vm.focusPending = false
            return@LaunchedEffect
        }
        val idx = visible.indexOfFirst { it.ch.key == vm.lastFocusKey }.let { if (it < 0) 0 else it }
        runCatching { grid.scrollToItem(idx) }
        delay(150)
        runCatching { focusReq.requestFocus() }
        vm.focusPending = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                TvTitle(
                    if (favoritesOnly) "Favoritos" else "Canais",
                    if (visible.size == 1) "1 canal" else "${visible.size} canais"
                )
            }
            TvButton(
                if (query.isBlank()) "Buscar" else "Busca: " + query.trim(),
                icon = Icons.Default.Search,
                requester = headerReq
            ) { searchOpen = true }
            Spacer(Modifier.width(10.dp))
            TvButton("Atualizar", icon = Icons.Default.Refresh) { vm.refresh(true) }
        }
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (origins.size > 1) {
            LazyRow(
                state = vm.originListState,
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TvButton("Todas as listas", selected = org == null) { vm.origin.value = null }
                }
                items(origins) { o ->
                    TvButton(Repo.originName(o), selected = org == o, onLongClick = { originMenu = o }) { vm.origin.value = o }
                }
            }
        }

        if (s.showCategoryChips && cats.size > 1) {
            LazyRow(
                state = vm.catListState,
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    TvButton("Todos", selected = cat == null) { vm.category.value = null }
                }
                items(cats) { c ->
                    TvButton(catLabel(c), selected = cat == c, onLongClick = { catMenu = c }) { vm.category.value = c }
                }
            }
        }

        if (visible.isEmpty()) {
            TvEmpty(
                if (favoritesOnly) "Nenhum favorito" else "Nenhum canal",
                if (favoritesOnly) "Segure OK sobre um canal (ou aperte Menu) e escolha Favoritar."
                else "Atualize a lista ou ative uma fonte em Ajustes > Fontes de canais.",
                if (favoritesOnly) null else "Atualizar lista"
            ) { vm.refresh(true) }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(210.dp),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(visible, key = { _, it -> it.ch.key }) { index, item ->
                    ChannelCard(
                        item = item,
                        s = s,
                        requester = if (index == target) focusReq else null,
                        onFocus = { vm.lastFocusKey = item.ch.key },
                        onMenu = { menuTarget = item },
                        onPlay = { vm.play(item.ch, queue) }
                    )
                }
            }
        }
    }

    val om = originMenu
    if (om != null) {
        val keys = baseAll.filter { it.ch.sourceId == om }.map { it.ch.key }
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
    val cm = catMenu
    if (cm != null) {
        val keys = base.filter { cm in it.ch.groups }.map { it.ch.key }
        val safe = remember { FocusRequester() }
        LaunchedEffect(cm) {
            delay(150)
            runCatching { safe.requestFocus() }
        }
        AlertDialog(
            onDismissRequest = { catMenu = null },
            title = { Text(catLabel(cm)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(keys.size.toString() + " canais nesta categoria" + if (org != null) " (na lista escolhida)." else ".")
                    TvButton("Cancelar", modifier = Modifier.fillMaxWidth(), requester = safe) { catMenu = null }
                    TvButton("Desativar os " + keys.size + " canais", modifier = Modifier.fillMaxWidth()) {
                        vm.setDisabled(keys, true)
                        catMenu = null
                    }
                    TvButton("Excluir os " + keys.size + " canais", modifier = Modifier.fillMaxWidth(), danger = true) {
                        vm.requestDelete(keys)
                        if (vm.category.value == cm) vm.category.value = null
                        catMenu = null
                    }
                }
            },
            confirmButton = {}
        )
    }
    val mt = menuTarget
    if (mt != null) {
        ChannelMenuDialog(vm, mt) { menuTarget = null }
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

@Composable
private fun ChannelCard(
    item: ChannelItem,
    s: com.willian.tvaberta.data.AppSettings,
    requester: FocusRequester?,
    onFocus: () -> Unit,
    onMenu: () -> Unit,
    onPlay: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val nowT = nowTitle(item, s)
    Column(
        Modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .tvClickable(shape, onFocus = { if (it) onFocus() }, onLongClick = onMenu, onClick = onPlay)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
    ) {
        ChannelLogo(item, s.showLogos, Modifier.fillMaxWidth().height(104.dp))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (nowT != null) "▶ " + nowT else catLabel(item.ch.groups.first()),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                if (s.showBadges) item.ch.quality?.let { MiniTag(it) }
                if (item.st.favorite) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Star, contentDescription = "Favorito", tint = Color(0xFFF5B301), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Ações do canal (OK segurado ou tecla Menu sobre o canal). */
@Composable
fun ChannelMenuDialog(vm: MainViewModel, item: ChannelItem, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    var moveOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { first.requestFocus() }
    }
    if (moveOpen) {
        TvMoveGroupDialog(vm, listOf(item.ch.key)) {
            moveOpen = false
            onClose()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton(
                    if (item.st.favorite) "Remover dos favoritos" else "Adicionar aos favoritos",
                    modifier = Modifier.fillMaxWidth(),
                    requester = first
                ) {
                    vm.toggleFavorite(item.ch.key)
                    onClose()
                }
                TvButton("Renomear", modifier = Modifier.fillMaxWidth()) {
                    vm.renameTarget = item
                    onClose()
                }
                TvButton("Mover para outro grupo", modifier = Modifier.fillMaxWidth()) { moveOpen = true }
                TvButton("Desativar (esconder da lista)", modifier = Modifier.fillMaxWidth()) {
                    vm.setDisabled(listOf(item.ch.key), true)
                    onClose()
                }
                TvButton("Excluir", modifier = Modifier.fillMaxWidth(), danger = true) {
                    vm.requestDelete(listOf(item.ch.key))
                    onClose()
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } }
    )
}
