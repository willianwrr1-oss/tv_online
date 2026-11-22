package com.willian.tvaberta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.PackInfo
import com.willian.tvaberta.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun packSubtitle(p: PackInfo): String {
    val kind = when (p.kind) {
        "source" -> "lista por link"
        "file" -> "arquivo importado em " + formatDateTime(p.time)
        else -> "canais seus"
    }
    return p.count.toString() + " canais · " + kind
}

/** Lista os pacotes (listas por link e arquivos importados); toque num pacote para ver os canais e excluir. */
@Composable
fun PacksDialog(vm: MainViewModel, onClose: () -> Unit) {
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

    if (cur == null) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("Pacotes de canais") },
            text = {
                if (infos.isEmpty()) {
                    Text("Nenhum pacote. Adicione uma lista por link ou importe um arquivo M3U.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(infos, key = { it.id }) { p ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { sel = p.id }
                                    .padding(vertical = 10.dp)
                            ) {
                                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    packSubtitle(p),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Fechar") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = {
                sel = null
                confirm = false
            },
            title = { Text(cur.name) },
            text = {
                Column {
                    Text(
                        packSubtitle(cur),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (confirm) {
                        Text(
                            "Toque em Confirmar para excluir o pacote e todos esses canais.",
                            Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 300.dp).padding(top = 8.dp)) {
                        items(chList, key = { it.key }) { c ->
                            Text(c.name, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (confirm) {
                        Repo.deletePack(cur)
                        vm.toast("Pacote excluído: " + cur.name)
                        sel = null
                        confirm = false
                    } else {
                        confirm = true
                    }
                }) {
                    Text(
                        if (confirm) "Confirmar exclusão" else "Excluir pacote",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    sel = null
                    confirm = false
                }) { Text("Voltar") }
            }
        )
    }
}
