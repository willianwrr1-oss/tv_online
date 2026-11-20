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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.MainViewModel

/** Escolhe para qual grupo mover os canais selecionados (ou cria um grupo novo). */
@Composable
fun MoveGroupDialog(vm: MainViewModel, keys: List<String>, onDone: () -> Unit) {
    val all by vm.items.collectAsState()
    val groups = remember(all) { categoriesOf(all.filter { !it.st.deleted }) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (keys.size == 1) "Mover canal para..." else "Mover " + keys.size + " canais para...") },
        text = {
            Column {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Novo grupo") },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        vm.moveToGroup(keys, newName)
                        onDone()
                    }
                ) { Text("Criar grupo e mover") }
                Text(
                    "Ou escolha um grupo existente:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                LazyColumn(Modifier.heightIn(max = 260.dp)) {
                    items(groups) { g ->
                        Text(
                            catLabel(g),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.moveToGroup(keys, g)
                                    onDone()
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                TextButton(onClick = {
                    vm.moveToGroup(keys, null)
                    onDone()
                }) { Text("Voltar ao grupo original") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } }
    )
}
