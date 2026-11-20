package com.willian.tvaberta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.SettingsStore

private class NavTab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("Canais", Icons.Default.LiveTv),
    NavTab("Favoritos", Icons.Default.Star),
    NavTab("Gerenciar", Icons.Default.Tune),
    NavTab("Ajustes", Icons.Default.Settings),
)

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    AppTheme(s) {
        val host = remember { SnackbarHostState() }
        val playing = vm.player.current != null

        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                host.currentSnackbarData?.dismiss()
                val r = host.showSnackbar(
                    message = e.text,
                    actionLabel = e.actionLabel,
                    duration = SnackbarDuration.Short
                )
                if (r == SnackbarResult.ActionPerformed) e.onAction?.invoke()
            }
        }

        BackHandler(enabled = !playing && (vm.tab != 0 || vm.settingsPage != null)) {
            if (vm.settingsPage != null) vm.settingsPage = null else vm.tab = 0
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = vm.tab == i,
                                onClick = {
                                    vm.tab = i
                                    if (i == 3) vm.settingsPage = null
                                },
                                icon = { Icon(t.icon, contentDescription = t.label) },
                                label = { Text(t.label) }
                            )
                        }
                    }
                }
            ) { pad ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                ) {
                    when (vm.tab) {
                        0 -> ChannelsScreen(vm, favoritesOnly = false)
                        1 -> ChannelsScreen(vm, favoritesOnly = true)
                        2 -> ManageScreen(vm)
                        else -> SettingsScreen(vm)
                    }
                }
            }

            if (playing) PlayerScreen(vm)

            SnackbarHost(
                hostState = host,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (playing) 96.dp else 88.dp)
            )
        }

        RenameDialog(vm)
        DeleteDialog(vm)
    }
}

@Composable
private fun RenameDialog(vm: MainViewModel) {
    val target = vm.renameTarget ?: return
    var text by remember(target.ch.key) { mutableStateOf(target.title) }
    AlertDialog(
        onDismissRequest = { vm.renameTarget = null },
        title = { Text("Renomear canal") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Nome") },
                supportingText = { Text("Deixe em branco para voltar ao nome original.") }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.rename(target.ch.key, text)
                vm.renameTarget = null
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = { vm.renameTarget = null }) { Text("Cancelar") } }
    )
}

@Composable
private fun DeleteDialog(vm: MainViewModel) {
    val keys = vm.pendingDelete
    if (keys.isEmpty()) return
    AlertDialog(
        onDismissRequest = { vm.pendingDelete = emptyList() },
        title = { Text(if (keys.size == 1) "Excluir este canal?" else "Excluir ${keys.size} canais?") },
        text = {
            Text(
                "O canal sai da lista e não volta com as atualizações. " +
                    "Você pode restaurá-lo depois em Gerenciar > Excluídos."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                vm.pendingDelete = emptyList()
                vm.delete(keys)
            }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { vm.pendingDelete = emptyList() }) { Text("Cancelar") } }
    )
}
