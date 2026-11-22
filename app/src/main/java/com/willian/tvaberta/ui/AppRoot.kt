package com.willian.tvaberta.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.SettingsStore

private class NavTab(val label: String, val icon: ImageVector)

// Início, TV ao vivo, Filmes, Séries e Mais (Guia, Favoritos, Gerenciar e Ajustes ficam dentro de Mais).
private val tabs = listOf(
    NavTab("Início", Icons.Default.Home),
    NavTab("Ao vivo", Icons.Default.LiveTv),
    NavTab("Filmes", Icons.Default.Movie),
    NavTab("Séries", Icons.Default.Tv),
    NavTab("Mais", Icons.Default.Menu),
)

/** Telas de vm.tab: 0 início/áreas, 1 guia, 2 favoritos, 3 gerenciar, 4 ajustes, 5 menu Mais. */
private const val TAB_MORE = 5

@Composable
fun AppRoot(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    AppTheme(s) {
        val host = remember { SnackbarHostState() }
        val playing = vm.player.current != null && vm.fullscreen
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val selected = when {
            vm.tab != 0 -> 4
            vm.area == null -> 0
            else -> vm.area!! + 1
        }
        val pick: (Int) -> Unit = { i ->
            if (i == 4) {
                if (vm.tab != TAB_MORE) vm.tab = TAB_MORE
            } else {
                vm.tab = 0
                vm.openArea(if (i == 0) null else i - 1)
            }
        }

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

        LaunchedEffect(vm.tab) {
            if (vm.tab != 0 && vm.previewMode && !vm.fullscreen) vm.stopPreview()
        }

        BackHandler(enabled = !playing && (vm.tab != 0 || vm.settingsPage != null || vm.area != null)) {
            if (vm.settingsPage != null) vm.settingsPage = null
            else if (vm.tab in 1..4) vm.tab = TAB_MORE
            else if (vm.tab == TAB_MORE) vm.tab = 0
            else vm.openArea(null)
        }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!landscape) {
                        NavigationBar {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = selected == i,
                                    onClick = { pick(i) },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) }
                                )
                            }
                        }
                    }
                }
            ) { pad ->
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(pad)
                ) {
                    if (landscape) {
                        NavigationRail {
                            Spacer(Modifier.weight(1f))
                            tabs.forEachIndexed { i, t ->
                                NavigationRailItem(
                                    selected = selected == i,
                                    onClick = { pick(i) },
                                    icon = { Icon(t.icon, contentDescription = t.label) },
                                    label = { Text(t.label) }
                                )
                            }
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        when (vm.tab) {
                            0 -> if (vm.area == null) HomeScreen(vm) else LiveScreen(vm)
                            1 -> GuideScreen(vm)
                            2 -> ChannelsScreen(vm, favoritesOnly = true)
                            3 -> ManageScreen(vm)
                            4 -> SettingsScreen(vm)
                            else -> MoreScreen(vm)
                        }
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
