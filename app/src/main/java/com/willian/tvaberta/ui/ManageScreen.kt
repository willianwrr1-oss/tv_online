package com.willian.tvaberta.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.SettingsStore

@Composable
fun ManageScreen(vm: MainViewModel) {
    val all by vm.items.collectAsState()
    val s by SettingsStore.flow.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var showAdd by remember { mutableStateOf(false) }
    var moveKeys by remember { mutableStateOf<List<String>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importM3u(uri)
    }

    val nActive = remember(all) { all.count { !it.st.deleted && !it.st.disabled } }
    val nOff = remember(all) { all.count { !it.st.deleted && it.st.disabled } }
    val nDeleted = remember(all) { all.count { it.st.deleted } }

    val list by produceState(emptyList<ChannelItem>(), all, tab, query) {
        value = withContext(Dispatchers.Default) {
            val q = query.trim()
            sortByTitle(
                all.filter {
                    val inTab = when (tab) {
                        0 -> !it.st.deleted && !it.st.disabled
                        1 -> !it.st.deleted && it.st.disabled
                        else -> it.st.deleted
                    }
                    inTab && (q.isEmpty() || it.title.contains(q, ignoreCase = true))
                }
            )
        }
    }

    LaunchedEffect(tab) { selected = emptySet() }

    val keysInList = remember(list) { list.map { it.ch.key } }
    val allSelected = keysInList.isNotEmpty() && selected.containsAll(keysInList)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                title = "Gerenciar canais",
                subtitle = "Desative para esconder ou exclua para remover de vez"
            ) {
                IconButton(onClick = { vm.removeDuplicates() }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Excluir canais duplicados")
                }
                IconButton(onClick = { picker.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Default.FolderOpen, contentDescription = "Importar arquivo M3U")
                }
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Ativos ($nActive)", maxLines = 1) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Desativados ($nOff)", maxLines = 1) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Excluídos ($nDeleted)", maxLines = 1) })
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Buscar nesta lista") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                        }
                    }
                }
            )

            if (list.isEmpty()) {
                EmptyState(
                    icon = when (tab) {
                        0 -> Icons.Default.Visibility
                        1 -> Icons.Default.VisibilityOff
                        else -> Icons.Default.Delete
                    },
                    title = when (tab) {
                        0 -> "Nenhum canal ativo"
                        1 -> "Nenhum canal desativado"
                        else -> "Nenhum canal excluído"
                    },
                    text = when (tab) {
                        0 -> "Atualize a lista na aba Canais ou adicione um canal pelo botão abaixo."
                        1 -> "Canais desativados ficam escondidos, mas continuam aqui para você reativar."
                        else -> "Canais excluídos não voltam nas atualizações. Aqui você pode restaurá-los."
                    }
                )
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = if (allSelected) emptySet() else keysInList.toSet()
                        }
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = allSelected, onCheckedChange = {
                        selected = if (allSelected) emptySet() else keysInList.toSet()
                    })
                    Text(
                        if (selected.isEmpty()) "Selecionar todos (${list.size})" else "${selected.size} selecionado(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(list, key = { it.ch.key }) { item ->
                        val checked = item.ch.key in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - item.ch.key else selected + item.ch.key
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selected = if (checked) selected - item.ch.key else selected + item.ch.key
                            })
                            ChannelLogo(item, s.showLogos, Modifier.size(44.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    item.ch.groups.joinToString(" · ") { catLabel(it) } +
                                        if (item.ch.sourceId == "manual") " · manual" else "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    Modifier
                        .navigationBarsPadding()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val keys = selected.toList()
                    when (tab) {
                        0 -> {
                            OutlinedButton(
                                onClick = {
                                    vm.setDisabled(keys, true)
                                    selected = emptySet()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.VisibilityOff, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Desativar")
                            }
                            OutlinedButton(
                                onClick = { moveKeys = keys },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DriveFileMove, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mover")
                            }
                            Button(
                                onClick = {
                                    vm.requestDelete(keys)
                                    selected = emptySet()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Excluir")
                            }
                        }
                        1 -> {
                            OutlinedButton(
                                onClick = {
                                    vm.setDisabled(keys, false)
                                    selected = emptySet()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Ativar")
                            }
                            OutlinedButton(
                                onClick = { moveKeys = keys },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.DriveFileMove, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mover")
                            }
                            Button(
                                onClick = {
                                    vm.requestDelete(keys)
                                    selected = emptySet()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Excluir")
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    vm.restore(keys)
                                    selected = emptySet()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.RestoreFromTrash, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Restaurar")
                            }
                        }
                    }
                }
            }
        } else if (tab == 0) {
            ExtendedFloatingActionButton(
                onClick = { showAdd = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Adicionar canal") }
            )
        }
    }

    val mk = moveKeys
    if (mk != null) {
        MoveGroupDialog(vm, mk) {
            moveKeys = null
            selected = emptySet()
        }
    }

    if (showAdd) {
        AddChannelDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url, group, logo ->
                vm.addChannel(name, url, group, logo)
                showAdd = false
            }
        )
    }
}

@Composable
fun AddChannelDialog(onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && (url.trim().startsWith("http://") || url.trim().startsWith("https://"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar canal") },
        text = {
            DialogList {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Nome do canal") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Link da transmissão (http/https, .m3u8)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = group, onValueChange = { group = it },
                    label = { Text("Categoria (opcional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = logo, onValueChange = { logo = it },
                    label = { Text("Link do logo (opcional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, url, group, logo) }, enabled = valid) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
