package com.willian.tvaberta.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** Gerenciar: ativos, desativados e excluídos, com botões grandes para o controle remoto. */
@Composable
fun TvManageScreen(vm: MainViewModel) {
    val dv by Repo.dataVersion.collectAsState()
    val scope = rememberCoroutineScope()
    val s by SettingsStore.flow.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var addOpen by remember { mutableStateOf(false) }
    var refocus by remember { mutableIntStateOf(-1) }
    val startImport = rememberTvM3uImport(vm)
    var selectMode by remember { mutableStateOf(false) }
    var sel by remember { mutableStateOf(setOf<String>()) }
    var moveKeys by remember { mutableStateOf<List<String>?>(null) }

    val counts by produceState(Triple(0, 0, 0), dv) {
        value = withContext(Dispatchers.IO) {
            Triple(Repo.db.manageCount(0, ""), Repo.db.manageCount(1, ""), Repo.db.manageCount(2, ""))
        }
    }
    val tabTotal = when (tab) {
        0 -> counts.first
        1 -> counts.second
        else -> counts.third
    }
    val list by produceState(emptyList<ChannelItem>(), dv, tab) {
        value = withContext(Dispatchers.IO) { Repo.db.manageList(tab, "", 500).map { Repo.item(it) } }
    }
    fun keysOfTab(done: (List<String>) -> Unit) {
        scope.launch { done(withContext(Dispatchers.IO) { Repo.db.manageKeys(tab, "") }) }
    }

    val firstChip = remember { FocusRequester() }
    val rowReq = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (vm.focusPending) {
            delay(150)
            runCatching { firstChip.requestFocus() }
            vm.focusPending = false
        }
    }
    // Depois de agir num canal, o foco continua na mesma posição da lista.
    LaunchedEffect(list.size) {
        if (refocus >= 0 && list.isNotEmpty()) {
            delay(120)
            runCatching { rowReq.requestFocus() }
        }
        refocus = -1
    }
    val target = if (refocus >= 0) refocus.coerceAtMost(list.size - 1) else -1

    Column(Modifier.fillMaxSize()) {
        TvTitle("Gerenciar canais", "Desative para esconder, exclua para não voltar mais. Dá para restaurar.")
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { TvButton("Ativos (${counts.first})", selected = tab == 0, requester = firstChip) { tab = 0 } }
                item { TvButton("Desativados (${counts.second})", selected = tab == 1) { tab = 1 } }
                item { TvButton("Excluídos (${counts.third})", selected = tab == 2) { tab = 2 } }
            }
            Spacer(Modifier.width(10.dp))
            if (tab != 2 && list.isNotEmpty()) {
                TvButton(if (selectMode) "Concluir seleção" else "Selecionar vários", selected = selectMode) {
                    selectMode = !selectMode
                    sel = emptySet()
                }
                Spacer(Modifier.width(10.dp))
            }
            if (tab == 1 && counts.second > 0) {
                TvButton("Ativar todos") { keysOfTab { vm.setDisabled(it, false, withUndo = false) } }
                Spacer(Modifier.width(10.dp))
            }
            if (tab == 2 && counts.third > 0) {
                TvButton("Restaurar todos") { keysOfTab { vm.restore(it) } }
                Spacer(Modifier.width(10.dp))
            }
            TvButton("Importar M3U") {
                startImport()
            }
            Spacer(Modifier.width(10.dp))
            TvButton("Excluir duplicados") { vm.removeDuplicates() }
            Spacer(Modifier.width(10.dp))
            TvButton("Adicionar canal", icon = Icons.Default.Add) { addOpen = true }
        }
        if (selectMode && tab != 2) {
            TvSpacer(8)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TvButton(if (sel.size >= tabTotal && tabTotal > 0) "Limpar seleção" else "Marcar todos (" + tabTotal + ")") {
                    if (sel.size >= tabTotal) sel = emptySet() else keysOfTab { sel = it.toSet() }
                }
                Spacer(Modifier.width(10.dp))
                if (sel.isNotEmpty()) {
                    TvButton("Excluir (" + sel.size + ")", danger = true) {
                        vm.requestDelete(sel.toList())
                        sel = emptySet()
                        selectMode = false
                    }
                    Spacer(Modifier.width(10.dp))
                    TvButton(if (tab == 0) "Desativar (" + sel.size + ")" else "Ativar (" + sel.size + ")") {
                        vm.setDisabled(sel.toList(), tab == 0)
                        sel = emptySet()
                        selectMode = false
                    }
                    Spacer(Modifier.width(10.dp))
                    TvButton("Mover (" + sel.size + ")") { moveKeys = sel.toList() }
                }
            }
        }
        TvSpacer(10)

        if (list.isEmpty()) {
            TvEmpty(
                when (tab) {
                    0 -> "Nenhum canal ativo"
                    1 -> "Nenhum canal desativado"
                    else -> "Nenhum canal excluído"
                },
                if (tab == 0) "Atualize a lista na tela Canais." else "Quando você desativar ou excluir canais, eles aparecem aqui."
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(list, key = { _, it -> it.ch.key }) { index, item ->
                    if (selectMode && tab != 2) {
                        val checked = item.ch.key in sel
                        SelectRow(item, s, checked) {
                            sel = if (checked) sel - item.ch.key else sel + item.ch.key
                        }
                        return@itemsIndexed
                    }
                    ManageRow(
                        item = item,
                        tab = tab,
                        s = s,
                        requester = if (index == target) rowReq else null,
                        onAction = { refocus = index },
                        vm = vm
                    )
                }
            }
        }
    }

    if (addOpen) AddChannelDialog(vm) { addOpen = false }
    val mk = moveKeys
    if (mk != null) {
        TvMoveGroupDialog(vm, mk) {
            moveKeys = null
            sel = emptySet()
            selectMode = false
        }
    }
}

@Composable
private fun SelectRow(
    item: ChannelItem,
    s: com.willian.tvaberta.data.AppSettings,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .tvClickable(shape, onClick = onToggle)
            .background(
                if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(14.dp))
        ChannelLogo(item, s.showLogos, Modifier.size(44.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ManageRow(
    item: ChannelItem,
    tab: Int,
    s: com.willian.tvaberta.data.AppSettings,
    requester: FocusRequester?,
    onAction: () -> Unit,
    vm: MainViewModel,
) {
    var moveOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChannelLogo(item, s.showLogos, Modifier.size(52.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                item.ch.groups.joinToString(" · ") { catLabel(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (tab) {
            0 -> {
                TvButton("Desativar", requester = requester) {
                    onAction()
                    vm.setDisabled(listOf(item.ch.key), true, withUndo = false)
                }
                Spacer(Modifier.width(10.dp))
                TvButton("Mover") { moveOpen = true }
                Spacer(Modifier.width(10.dp))
                TvButton("Excluir", danger = true) {
                    onAction()
                    vm.delete(listOf(item.ch.key))
                }
            }
            1 -> {
                TvButton("Ativar", requester = requester) {
                    onAction()
                    vm.setDisabled(listOf(item.ch.key), false, withUndo = false)
                }
                Spacer(Modifier.width(10.dp))
                TvButton("Mover") { moveOpen = true }
                Spacer(Modifier.width(10.dp))
                TvButton("Excluir", danger = true) {
                    onAction()
                    vm.delete(listOf(item.ch.key))
                }
            }
            else -> {
                TvButton("Restaurar", requester = requester) {
                    onAction()
                    vm.restore(listOf(item.ch.key))
                }
            }
        }
    }
    if (moveOpen) TvMoveGroupDialog(vm, listOf(item.ch.key)) { moveOpen = false }
}

@Composable
private fun AddChannelDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && (url.trim().startsWith("http://") || url.trim().startsWith("https://"))
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Adicionar canal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome do canal") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Link do vídeo (http ou https)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                vm.addChannel(name.trim(), url.trim(), "Meus canais", "")
                onClose()
            }) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}
