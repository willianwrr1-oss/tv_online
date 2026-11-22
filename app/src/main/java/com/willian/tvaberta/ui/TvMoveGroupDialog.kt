package com.willian.tvaberta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Flt
import com.willian.tvaberta.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/** Escolhe, com o controle remoto, para qual grupo mover os canais. */
@Composable
fun TvMoveGroupDialog(vm: MainViewModel, keys: List<String>, onDone: () -> Unit) {
    val groups by produceState(emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            Repo.db.groupCounts(Flt()).map { it.grp }.sortedBy { catLabel(it).lowercase() }
        }
    }
    var newName by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (keys.size == 1) "Mover canal para..." else "Mover " + keys.size + " canais para...") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TvButton("Voltar ao grupo original", modifier = Modifier.fillMaxWidth(), requester = first) {
                    vm.moveToGroup(keys, null)
                    onDone()
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Novo grupo") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (newName.isNotBlank()) {
                    TvButton("Criar \"" + newName.trim() + "\" e mover", modifier = Modifier.fillMaxWidth()) {
                        vm.moveToGroup(keys, newName)
                        onDone()
                    }
                }
                LazyColumn(
                    Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups) { g ->
                        TvButton(catLabel(g), modifier = Modifier.fillMaxWidth()) {
                            vm.moveToGroup(keys, g)
                            onDone()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } }
    )
}
