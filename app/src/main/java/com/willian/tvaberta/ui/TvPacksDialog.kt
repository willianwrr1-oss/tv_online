package com.willian.tvaberta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.PackInfo
import com.willian.tvaberta.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

fun packSubtitle(p: PackInfo): String {
    val kind = when (p.kind) {
        "source" -> "lista por link"
        "file" -> "arquivo importado em " + formatDateTime(p.time)
        else -> "canais seus"
    }
    return p.count.toString() + " canais · " + kind
}

/** Lista os pacotes (listas por link e arquivos importados); OK num pacote mostra os canais e permite excluir. */
@Composable
fun TvPacksDialog(vm: MainViewModel, onClose: () -> Unit) {
    val sources by Repo.sources.collectAsState()
    val packsState by Repo.packs.collectAsState()
    val dataV by Repo.dataVersion.collectAsState()
    val infos by produceState(emptyList<PackInfo>(), sources, packsState, dataV) {
        value = withContext(Dispatchers.IO) { Repo.packInfos() }
    }
    var sel by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val cur = infos.firstOrNull { it.id == sel }
    val chList by produceState(emptyList<Channel>(), cur?.id) {
        val c = cur
        value = if (c == null) emptyList() else withContext(Dispatchers.IO) { Repo.packChannels(c, 300) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(cur?.id) {
        delay(150)
        runCatching { first.requestFocus() }
    }

    if (cur == null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Pacotes de canais") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvButton("Fechar", requester = first) { onClose() }
                    if (infos.isEmpty()) {
                        Text("Nenhum pacote. Adicione uma lista por link ou importe um arquivo M3U.")
                    }
                    LazyColumn(Modifier.heightIn(max = 330.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(infos, key = { it.id }) { p ->
                            TvButton(p.name + "  ·  " + packSubtitle(p), modifier = Modifier.fillMaxWidth()) {
                                sel = p.id
                                confirm = false
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    } else {
        AlertDialog(
            onDismissRequest = {
                sel = null
                confirm = false
            },
            title = { Text(cur.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(packSubtitle(cur), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvButton("Voltar", requester = first) {
                            sel = null
                            confirm = false
                        }
                        TvButton(if (confirm) "Confirmar exclusão" else "Excluir pacote", danger = true) {
                            if (confirm) {
                                Repo.deletePack(cur)
                                vm.toast("Pacote excluído: " + cur.name)
                                sel = null
                                confirm = false
                            } else {
                                confirm = true
                            }
                        }
                    }
                    if (confirm) {
                        Text(
                            "Aperte OK de novo em Confirmar exclusão para apagar o pacote e todos esses canais.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(chList, key = { it.key }) { c ->
                            TvButton(c.name, modifier = Modifier.fillMaxWidth()) { }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
