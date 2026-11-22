package com.willian.tvaberta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.willian.tvaberta.BuildConfig
import java.io.File
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Countries
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import kotlinx.coroutines.delay

/** Ajustes numa só página rolável: cada linha é um item para o controle remoto (OK troca o valor). */
@Composable
fun TvSettingsScreen(vm: MainViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s by SettingsStore.flow.collectAsState()
    val sources by Repo.sources.collectAsState()
    val loadingEpg by Epg.loading.collectAsState()
    var pickCountry by remember { mutableStateOf(false) }
    val startImport = rememberTvM3uImport(vm)
    val startRestore = rememberTvFileChooser(vm, setOf("json"), "Escolher arquivo de backup", ctx.getExternalFilesDir(null)) { f ->
        vm.restoreFrom { f.inputStream() }
    }
    var confirmResetList by remember { mutableStateOf(false) }
    var packsOpen by remember { mutableStateOf(false) }
    if (packsOpen) TvPacksDialog(vm) { packsOpen = false }
    var customDialog by remember { mutableStateOf(false) }
    var editUrls by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (vm.focusPending) {
            delay(150)
            runCatching { first.requestFocus() }
            vm.focusPending = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 40.dp, start = 6.dp, end = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TvTitle("Ajustes", "Use OK para trocar o valor de cada item")

        TvSection("Aparência") {
            TvChoiceRow("Tema", listOf("Seguir o sistema", "Claro", "Escuro"), s.theme, requester = first) { v ->
                SettingsStore.update { it.copy(theme = v) }
            }
            TvChoiceRow("Cor de destaque", AccentPalette.map { it.first }, s.accent) { v ->
                SettingsStore.update { it.copy(accent = v) }
            }
            TvSwitchRow("Preto puro", "Fundo totalmente preto no tema escuro", s.pureBlack) { v ->
                SettingsStore.update { it.copy(pureBlack = v) }
            }
            TvSwitchRow("Mostrar logos", "Desligue para economizar dados", s.showLogos) { v ->
                SettingsStore.update { it.copy(showLogos = v) }
            }
            TvSwitchRow("Mostrar selos de qualidade", "Ex.: 720p, 1080p", s.showBadges) { v ->
                SettingsStore.update { it.copy(showBadges = v) }
            }
            TvSwitchRow("Mostrar categorias no topo", "Faixa de filtros por categoria", s.showCategoryChips) { v ->
                SettingsStore.update { it.copy(showCategoryChips = v) }
            }
        }

        TvSection("Reprodução") {
            TvChoiceRow("Proporção da imagem", listOf("Ajustar à tela", "Preencher (pode esticar)", "Zoom (corta as bordas)"), s.resizeMode) { v ->
                SettingsStore.update { it.copy(resizeMode = v) }
            }
            TvChoiceRow("Qualidade máxima", listOf("Automática", "1080p", "720p", "480p", "360p"), s.maxQuality) { v ->
                SettingsStore.update { it.copy(maxQuality = v) }
            }
            TvChoiceRow("Buffer", listOf("Baixa latência", "Equilibrado", "Estável"), s.bufferMode) { v ->
                SettingsStore.update { it.copy(bufferMode = v) }
            }
            TvIntChoiceRow("Esconder informações após", listOf("3 segundos", "5 segundos", "6 segundos", "8 segundos", "12 segundos"), listOf(3, 5, 6, 8, 12), s.overlaySeconds) { v ->
                SettingsStore.update { it.copy(overlaySeconds = v) }
            }
            TvSwitchRow("Manter a tela ligada", "Durante a reprodução", s.keepScreenOn) { v ->
                SettingsStore.update { it.copy(keepScreenOn = v) }
            }
            TvSwitchRow("Iniciar sem som", "Abre cada canal no mudo", s.startMuted) { v ->
                SettingsStore.update { it.copy(startMuted = v) }
            }
            TvSwitchRow("Reabrir o último canal", "Ao abrir o app", s.reopenLast) { v ->
                SettingsStore.update { it.copy(reopenLast = v) }
            }
            TvSwitchRow("Usar link alternativo", "Se o canal falhar, tenta outro endereço do mesmo canal", s.autoFallback) { v ->
                SettingsStore.update { it.copy(autoFallback = v) }
            }
            TvIntChoiceRow("Tentativas de reconexão", listOf("Nenhuma", "1 vez", "2 vezes", "3 vezes", "5 vezes"), listOf(0, 1, 2, 3, 5), s.retries) { v ->
                SettingsStore.update { it.copy(retries = v) }
            }
        }

        TvSection("Canais") {
            TvChoiceRow("Ordenar por", listOf("Nome (A-Z)", "Ordem original da lista", "Mais assistidos", "Assistidos recentemente"), s.sortMode) { v ->
                SettingsStore.update { it.copy(sortMode = v) }
            }
            TvSwitchRow("Ocultar canais com bloqueio regional", "Marcados como Geo-blocked na lista", s.hideGeoBlocked) { v ->
                SettingsStore.update { it.copy(hideGeoBlocked = v) }
            }
            TvSwitchRow("Ocultar canais que não funcionam 24h", "Marcados como Not 24/7 na lista", s.hideNot247) { v ->
                SettingsStore.update { it.copy(hideNot247 = v) }
            }
            TvIntChoiceRow(
                "Atualizar a lista automaticamente",
                listOf("Nunca", "A cada 6 horas", "A cada 12 horas", "Uma vez por dia", "A cada 2 dias", "Uma vez por semana"),
                listOf(0, 6, 12, 24, 48, 168),
                s.autoRefreshHours
            ) { v -> SettingsStore.update { it.copy(autoRefreshHours = v) } }
            TvActionRow("Atualizar lista agora", "Última atualização: " + formatDateTime(s.lastRefresh)) { vm.refresh(true) }
            TvSwitchRow("Confirmar antes de excluir", "Pergunta antes de remover canais", s.confirmDelete) { v ->
                SettingsStore.update { it.copy(confirmDelete = v) }
            }
            TvActionRow("Excluir canais duplicados", "Mantém um de cada (mesmo nome ou link)") { vm.removeDuplicates() }
        }

        TvSection("Guia de programação") {
            TvSwitchRow("Ativar guia de programação", "Programa no ar, próximo programa e grade de horários", s.epgEnabled) { v ->
                SettingsStore.update { it.copy(epgEnabled = v) }
                if (v) vm.refreshEpg(false)
            }
            TvSwitchRow("Programa atual nos canais", "Mostra \"▶ nome do programa\" nos cartões", s.showNowOnCards) { v ->
                SettingsStore.update { it.copy(showNowOnCards = v) }
            }
            TvIntChoiceRow(
                "Atualizar o guia automaticamente",
                listOf("Nunca", "A cada 6 horas", "A cada 12 horas", "Uma vez por dia"),
                listOf(0, 6, 12, 24),
                s.epgRefreshHours
            ) { v -> SettingsStore.update { it.copy(epgRefreshHours = v) } }
            TvActionRow(
                if (loadingEpg) "Baixando guia..." else "Atualizar guia agora",
                "Última atualização: " + formatDateTime(s.lastEpgRefresh)
            ) { if (!loadingEpg) vm.refreshEpg(true) }
            TvActionRow("Links do guia (XMLTV)", Epg.urlList(s.epgUrls).size.toString() + " link(s). OK para editar") { editUrls = true }
            TvActionRow("Voltar ao link padrão do guia", "Guia do Brasil (epgshare01 e BrazilTVEPG)") {
                SettingsStore.update { it.copy(epgUrls = Epg.DEFAULT_URL) }
                vm.toast("Link padrão restaurado.")
            }
            TvActionRow("Limpar guia baixado", "Apaga os arquivos do guia salvos no aparelho", danger = true) {
                Epg.clearCache()
                vm.toast("Guia apagado.")
            }
        }

        TvSection("Fontes de canais") {
            TvActionRow("Pacotes de canais", "Veja cada lista por link e cada arquivo importado, e exclua o pacote todo") {
                packsOpen = true
            }
            if (sources.isEmpty()) {
                Text("Nenhuma fonte. Adicione um país ou uma lista M3U.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            sources.forEach { src ->
                TvSwitchRow(src.name, src.url, src.enabled) { v -> vm.setSourceEnabled(src.id, v) }
            }
            TvActionRow("Adicionar país", "Lista oficial de canais do iptv-org por país") { pickCountry = true }
            TvActionRow("Adicionar lista M3U por link", "Digite o endereço de uma lista") { customDialog = true }
            TvActionRow("Importar arquivo M3U do aparelho", "Escolhe o arquivo no sistema; só entram canais novos") {
                startImport()
            }
            TvActionRow("Restaurar fontes originais", "Volta às quatro fontes originais e traz de volta os canais excluídos e desativados") {
                Repo.resetSources()
                Repo.restoreAllChannels()
                vm.refresh(true)
            }
            TvActionRow("Resetar lista de canais", "Volta tudo ao inicial: fontes originais, sem canais importados nem escolhas", danger = true) {
                confirmResetList = true
            }
        }

        TvSection("Backup") {
            TvActionRow(
                "Exportar backup",
                "Salva fontes, favoritos, excluídos, nomes e canais importados num arquivo no aparelho"
            ) {
                val f = File(ctx.getExternalFilesDir(null), "backup-tvaberta.json")
                vm.backupTo("Backup salvo em " + f.path) { f.outputStream() }
            }
            TvActionRow("Restaurar backup", "Escolha o arquivo de backup (.json); ele substitui suas escolhas atuais") {
                startRestore()
            }
        }

        TvSection("Dados") {
            TvActionRow("Limpar listas baixadas", "Apaga as listas salvas; use Atualizar para baixar de novo") {
                Repo.clearListCache()
                vm.toast("Listas apagadas. Atualize para baixar de novo.")
            }
            TvActionRow("Zerar histórico de reprodução", "Zera os contadores de mais assistidos e recentes") {
                Repo.resetHistory()
                vm.toast("Histórico zerado.")
            }
            TvActionRow("Restaurar configurações padrão", "Não apaga canais, favoritos nem fontes", danger = true) {
                confirmReset = true
            }
        }

        TvSection("Sobre") {
            TvInfoRow("Versão", BuildConfig.VERSION_NAME)
            TvInfoRow("Compilado em", BuildConfig.BUILD_DATE)
            TvInfoRow("Compatibilidade", "Android TV 8.0 ou superior")
            TvInfoRow("Desenvolvedor", "Willian R Rocha")
            Text(
                "A TV Aberta é um reprodutor. O app não hospeda nem transmite canais: ele lê listas públicas (M3U) e " +
                    "reproduz os sinais abertos que as próprias emissoras disponibilizam na internet. A disponibilidade de " +
                    "cada canal depende da emissora e pode variar por região. Use apenas fontes legais: listas que oferecem " +
                    "canais pagos de graça costumam ser pirataria. O app não coleta dados pessoais.",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmResetList) {
        AlertDialog(
            onDismissRequest = { confirmResetList = false },
            title = { Text("Resetar a lista de canais?") },
            text = {
                Text(
                    "Volta ao inicial: só as quatro fontes originais (IPTV-org, CatTV, PublicDomainM3U filmes e séries). " +
                        "Apaga os canais que você importou ou adicionou, e também favoritos, canais desativados, " +
                        "excluídos, renomeados e grupos que você mudou."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmResetList = false
                    Repo.resetChannelList()
                    vm.refresh(true)
                    vm.toast("Lista de canais resetada.")
                }) { Text("Resetar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmResetList = false }) { Text("Cancelar") } }
        )
    }
    if (pickCountry) {
        val have = sources.map { it.id }.toSet()
        AlertDialog(
            onDismissRequest = { pickCountry = false },
            title = { Text("Adicionar país") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Countries.list.forEach { (code, name) ->
                        val already = code in have
                        TvButton(
                            if (already) "$name (já adicionado)" else name,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (!already) {
                                pickCountry = false
                                vm.addCountry(code, name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCountry = false }) { Text("Fechar") } }
        )
    }
    if (customDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        val valid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
        AlertDialog(
            onDismissRequest = { customDialog = false },
            title = { Text("Lista M3U por link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nome (opcional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url, onValueChange = { url = it },
                        label = { Text("Link da lista (.m3u)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = {
                    customDialog = false
                    vm.addCustomSource(name, url)
                }) { Text("Adicionar") }
            },
            dismissButton = { TextButton(onClick = { customDialog = false }) { Text("Cancelar") } }
        )
    }
    if (editUrls) {
        var text by remember { mutableStateOf(s.epgUrls) }
        AlertDialog(
            onDismissRequest = { editUrls = false },
            title = { Text("Links do guia") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Um link por linha (.xml ou .xml.gz)") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    editUrls = false
                    SettingsStore.update { it.copy(epgUrls = text.trim()) }
                    vm.refreshEpg(true)
                }) { Text("Salvar e baixar") }
            },
            dismissButton = { TextButton(onClick = { editUrls = false }) { Text("Cancelar") } }
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restaurar configurações?") },
            text = { Text("Todos os ajustes voltam ao padrão. Seus canais, favoritos e fontes não mudam.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    SettingsStore.reset()
                    vm.toast("Configurações restauradas.")
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } }
        )
    }
}
