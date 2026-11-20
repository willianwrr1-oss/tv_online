package com.willian.tvaberta.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.willian.tvaberta.BuildConfig
import com.willian.tvaberta.MainViewModel
import com.willian.tvaberta.data.Countries
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val page = vm.settingsPage
    Column(Modifier.fillMaxSize()) {
        if (page == null) {
            ScreenHeader("Configurações", "Ajuste o app do seu jeito")
            SettingsHome(vm)
        } else {
            val title = when (page) {
                "aparencia" -> "Aparência"
                "reproducao" -> "Reprodução"
                "canais" -> "Canais"
                "fontes" -> "Fontes de canais"
                "guia" -> "Guia de programação"
                "dados" -> "Dados e cache"
                else -> "Sobre"
            }
            SubHeader(title) { vm.settingsPage = null }
            when (page) {
                "aparencia" -> AppearancePage()
                "reproducao" -> PlaybackPage()
                "canais" -> ChannelsPage(vm)
                "fontes" -> SourcesPage(vm)
                "guia" -> GuidePage(vm)
                "dados" -> DataPage(vm)
                else -> AboutPage()
            }
        }
    }
}

@Composable
private fun SubHeader(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
        }
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsHome(vm: MainViewModel) {
    SimpleScroll {
        SettingsSection("Preferências") {
            NavRow("Aparência", "Tema, cores, grade e lista", Icons.Default.Palette) { vm.settingsPage = "aparencia" }
            RowDivider()
            NavRow("Reprodução", "Vídeo, qualidade, buffer, PiP", Icons.Default.PlayCircle) { vm.settingsPage = "reproducao" }
            RowDivider()
            NavRow("Canais", "Ordem, filtros, atualização", Icons.Default.Tune) { vm.settingsPage = "canais" }
        }
        SettingsSection("Conteúdo") {
            NavRow("Fontes de canais", "Países e listas M3U", Icons.Default.Public) { vm.settingsPage = "fontes" }
            RowDivider()
            NavRow("Guia de programação", "Programa no ar, grade e fontes do guia", Icons.Default.Schedule) { vm.settingsPage = "guia" }
            RowDivider()
            NavRow("Dados e cache", "Logos, listas e restauração", Icons.Default.Storage) { vm.settingsPage = "dados" }
        }
        SettingsSection("App") {
            NavRow("Sobre", "Versão, aviso legal e desenvolvedor", Icons.Default.Info) { vm.settingsPage = "sobre" }
        }
    }
}

// ------------------------------------------------------------------------------------ aparência

@Composable
private fun AppearancePage() {
    val s by SettingsStore.flow.collectAsState()
    SimpleScroll {
        SettingsSection("Tema") {
            ChoiceRow("Tema", listOf("Seguir o sistema", "Claro", "Escuro"), s.theme) { v ->
                SettingsStore.update { it.copy(theme = v) }
            }
            RowDivider()
            AccentPicker(s.accent) { v -> SettingsStore.update { it.copy(accent = v) } }
            RowDivider()
            SwitchRow("Preto puro", "Fundo totalmente preto no tema escuro (economiza bateria em telas OLED)", s.pureBlack) { v ->
                SettingsStore.update { it.copy(pureBlack = v) }
            }
        }
        SettingsSection("Lista de canais") {
            ChoiceRow("Exibição", listOf("Grade", "Lista"), if (s.gridLayout) 0 else 1) { v ->
                SettingsStore.update { it.copy(gridLayout = v == 0) }
            }
            RowDivider()
            IntChoiceRow("Colunas da grade", listOf("2 colunas", "3 colunas", "4 colunas"), listOf(2, 3, 4), s.gridColumns) { v ->
                SettingsStore.update { it.copy(gridColumns = v) }
            }
            RowDivider()
            SwitchRow("Lista compacta", "Linhas menores no modo lista", s.compactList) { v ->
                SettingsStore.update { it.copy(compactList = v) }
            }
            RowDivider()
            SwitchRow("Mostrar logos", "Desligue para economizar dados", s.showLogos) { v ->
                SettingsStore.update { it.copy(showLogos = v) }
            }
            RowDivider()
            SwitchRow("Mostrar selos de qualidade", "Ex.: 720p, 1080p", s.showBadges) { v ->
                SettingsStore.update { it.copy(showBadges = v) }
            }
            RowDivider()
            SwitchRow("Mostrar categorias no topo", "Faixa de filtros por categoria", s.showCategoryChips) { v ->
                SettingsStore.update { it.copy(showCategoryChips = v) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ reprodução

@Composable
private fun PlaybackPage() {
    val s by SettingsStore.flow.collectAsState()
    SimpleScroll {
        SettingsSection("Vídeo") {
            ChoiceRow("Proporção da imagem", listOf("Ajustar à tela", "Preencher (pode esticar)", "Zoom (corta as bordas)"), s.resizeMode) { v ->
                SettingsStore.update { it.copy(resizeMode = v) }
            }
            RowDivider()
            ChoiceRow("Qualidade máxima", listOf("Automática", "1080p", "720p", "480p", "360p"), s.maxQuality) { v ->
                SettingsStore.update { it.copy(maxQuality = v) }
            }
            RowDivider()
            ChoiceRow(
                "Buffer",
                listOf("Baixa latência (mais rápido, pode travar)", "Equilibrado", "Estável (mais lento para abrir)"),
                s.bufferMode
            ) { v -> SettingsStore.update { it.copy(bufferMode = v) } }
        }
        SettingsSection("Tela") {
            SwitchRow("Manter a tela ligada", "Durante a reprodução", s.keepScreenOn) { v ->
                SettingsStore.update { it.copy(keepScreenOn = v) }
            }
            RowDivider()
            SwitchRow("Abrir em tela cheia (horizontal)", "Gira a tela ao abrir um canal", s.landscapeOnPlay) { v ->
                SettingsStore.update { it.copy(landscapeOnPlay = v) }
            }
            RowDivider()
            SwitchRow("Imagem em imagem (PiP)", "Continua em janela flutuante ao sair do app", s.pipOnLeave) { v ->
                SettingsStore.update { it.copy(pipOnLeave = v) }
            }
            RowDivider()
            IntChoiceRow("Esconder controles após", listOf("3 segundos", "5 segundos", "8 segundos", "12 segundos"), listOf(3, 5, 8, 12), s.overlaySeconds) { v ->
                SettingsStore.update { it.copy(overlaySeconds = v) }
            }
        }
        SettingsSection("Controle") {
            SwitchRow("Deslizar para trocar de canal", "Deslize para cima ou para baixo sobre o vídeo", s.swipeZap) { v ->
                SettingsStore.update { it.copy(swipeZap = v) }
            }
            RowDivider()
            SwitchRow("Iniciar sem som", "Abre cada canal no mudo", s.startMuted) { v ->
                SettingsStore.update { it.copy(startMuted = v) }
            }
            RowDivider()
            SwitchRow("Reabrir o último canal", "Ao abrir o app", s.reopenLast) { v ->
                SettingsStore.update { it.copy(reopenLast = v) }
            }
        }
        SettingsSection("Conexão") {
            SwitchRow("Usar link alternativo", "Se o canal falhar, tenta outro endereço do mesmo canal", s.autoFallback) { v ->
                SettingsStore.update { it.copy(autoFallback = v) }
            }
            RowDivider()
            IntChoiceRow("Tentativas de reconexão", listOf("Nenhuma", "1 vez", "2 vezes", "3 vezes", "5 vezes"), listOf(0, 1, 2, 3, 5), s.retries) { v ->
                SettingsStore.update { it.copy(retries = v) }
            }
            RowDivider()
            SwitchRow("Bloquear em dados móveis", "Só reproduz no Wi-Fi ou cabo", s.blockMobile) { v ->
                SettingsStore.update { it.copy(blockMobile = v) }
            }
        }
    }
}

// ------------------------------------------------------------------------------------ canais

@Composable
private fun ChannelsPage(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    var confirmRestore by remember { mutableStateOf(false) }
    SimpleScroll {
        SettingsSection("Lista") {
            ChoiceRow("Ordenar por", listOf("Nome (A-Z)", "Ordem original da lista", "Mais assistidos", "Assistidos recentemente"), s.sortMode) { v ->
                SettingsStore.update { it.copy(sortMode = v) }
            }
            RowDivider()
            SwitchRow("Ocultar canais com bloqueio regional", "Marcados como Geo-blocked na lista", s.hideGeoBlocked) { v ->
                SettingsStore.update { it.copy(hideGeoBlocked = v) }
            }
            RowDivider()
            SwitchRow("Ocultar canais que não funcionam 24h", "Marcados como Not 24/7 na lista", s.hideNot247) { v ->
                SettingsStore.update { it.copy(hideNot247 = v) }
            }
        }
        SettingsSection("Atualização") {
            IntChoiceRow(
                "Atualizar a lista automaticamente",
                listOf("Nunca", "A cada 6 horas", "A cada 12 horas", "Uma vez por dia", "A cada 2 dias", "Uma vez por semana"),
                listOf(0, 6, 12, 24, 48, 168),
                s.autoRefreshHours
            ) { v -> SettingsStore.update { it.copy(autoRefreshHours = v) } }
            RowDivider()
            ActionRow("Atualizar agora", "Última atualização: " + formatDateTime(s.lastRefresh)) { vm.refresh(true) }
        }
        SettingsSection("Exclusão") {
            SwitchRow("Confirmar antes de excluir", "Pergunta antes de remover canais", s.confirmDelete) { v ->
                SettingsStore.update { it.copy(confirmDelete = v) }
            }
            RowDivider()
            ActionRow("Restaurar todos os canais excluídos", "Os canais voltam para a lista") { confirmRestore = true }
            RowDivider()
            ActionRow("Reativar todos os canais desativados") {
                val keys = vm.items.value.filter { it.st.disabled && !it.st.deleted }.map { it.ch.key }
                if (keys.isEmpty()) vm.toast("Nenhum canal desativado.") else vm.setDisabled(keys, false, withUndo = false)
            }
        }
    }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restaurar canais excluídos?") },
            text = { Text("Todos os canais que você excluiu voltam para a lista.") },
            confirmButton = {
                TextButton(onClick = {
                    val keys = vm.items.value.filter { it.st.deleted }.map { it.ch.key }
                    confirmRestore = false
                    if (keys.isEmpty()) vm.toast("Nenhum canal excluído.") else vm.restore(keys)
                }) { Text("Restaurar") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancelar") } }
        )
    }
}

// ------------------------------------------------------------------------------------ fontes

@Composable
private fun SourcesPage(vm: MainViewModel) {
    val sources by Repo.sources.collectAsState()
    val refreshing by Repo.refreshing.collectAsState()
    var pickCountry by remember { mutableStateOf(false) }
    var customDialog by remember { mutableStateOf(false) }

    SimpleScroll {
        SettingsSection("Listas ativas") {
            if (sources.isEmpty()) {
                Text(
                    "Nenhuma fonte. Adicione um país ou uma lista M3U abaixo.",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            sources.forEachIndexed { i, src ->
                if (i > 0) RowDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(src.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            src.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    Switch(checked = src.enabled, onCheckedChange = { vm.setSourceEnabled(src.id, it) })
                    IconButton(onClick = {
                        Repo.removeSource(src.id)
                        vm.toast("Fonte removida.")
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remover fonte")
                    }
                }
            }
        }
        SettingsSection("Adicionar") {
            ActionRow("Adicionar país", "Canais abertos de outro país") { pickCountry = true }
            RowDivider()
            ActionRow("Adicionar canais em português", "Lista por idioma, de todos os países") {
                Repo.addSource("por", "Português (todos os países)", Countries.PORTUGUESE_URL)
                vm.refresh(true)
            }
            RowDivider()
            ActionRow("Adicionar lista M3U por link", "Uma lista sua ou de outra fonte pública") { customDialog = true }
        }
        SettingsSection("Atualização") {
            ActionRow(
                if (refreshing) "Atualizando…" else "Atualizar agora",
                "Baixa de novo todas as listas ativas"
            ) { if (!refreshing) vm.refresh(true) }
        }
        Text(
            "As listas vêm de projetos abertos que reúnem links públicos (iptv-org, CatTV, PublicDomainM3U). " +
                "O app não confere se cada link é autorizado pela emissora: a CatTV mistura canais públicos com " +
                "marcas de TV paga, por isso começa desligada. Alguns canais saem do ar ou ficam restritos a certas " +
                "regiões; use desativar ou excluir para limpar a lista.",
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (pickCountry) {
        AlertDialog(
            onDismissRequest = { pickCountry = false },
            title = { Text("Escolha um país") },
            text = {
                DialogList {
                    Countries.list.forEach { (code, name) ->
                        val already = sources.any { it.id == code }
                        Text(
                            if (already) "$name (já adicionado)" else name,
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !already) {
                                    pickCountry = false
                                    vm.addCountry(code, name)
                                }
                                .padding(vertical = 12.dp),
                            color = if (already) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
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
                Column {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nome (opcional)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
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
}

// ------------------------------------------------------------------------------------ dados

@Composable
private fun DataPage(vm: MainViewModel) {
    val ctx = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    SimpleScroll {
        SettingsSection("Cache") {
            ActionRow("Limpar cache de logos", "Libera espaço; os logos são baixados de novo quando precisar") {
                ctx.imageLoader.memoryCache?.clear()
                ctx.imageLoader.diskCache?.clear()
                vm.toast("Cache de logos limpo.")
            }
            RowDivider()
            ActionRow("Limpar listas baixadas", "Apaga as listas salvas; use Atualizar para baixar de novo") {
                Repo.clearListCache()
                vm.toast("Listas apagadas. Toque em atualizar para baixar de novo.")
            }
        }
        SettingsSection("Histórico") {
            ActionRow("Zerar histórico de reprodução", "Zera os contadores de mais assistidos e recentes") {
                Repo.resetHistory()
                vm.toast("Histórico zerado.")
            }
        }
        SettingsSection("Restauração") {
            ActionRow("Restaurar configurações padrão", "Não apaga canais, favoritos nem fontes", danger = true) {
                confirmReset = true
            }
        }
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

// ------------------------------------------------------------------------------------ sobre

@Composable
private fun AboutPage() {
    SimpleScroll {
        SettingsSection("Aplicativo") {
            InfoRow("Versão", BuildConfig.VERSION_NAME)
            RowDivider()
            InfoRow("Compilado em", BuildConfig.BUILD_DATE)
            RowDivider()
            InfoRow("Compatibilidade", "Android 8.0 ou superior")
            RowDivider()
            InfoRow("Desenvolvedor", "Willian R Rocha")
        }
        SettingsSection("Aviso") {
            Text(
                "A TV Aberta é um reprodutor. O app não hospeda nem transmite canais: ele lê listas públicas " +
                    "(M3U) e reproduz os sinais abertos que as próprias emissoras disponibilizam na internet. " +
                    "A disponibilidade de cada canal depende da emissora e pode variar por região.\n\n" +
                    "Use apenas fontes legais. Listas que oferecem canais pagos de graça costumam ser pirataria " +
                    "e podem trazer riscos de segurança.\n\n" +
                    "O app não coleta dados pessoais. A internet é usada apenas para baixar as listas de " +
                    "canais, os logos e a transmissão. Favoritos e ajustes ficam só no seu aparelho.",
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.LiveTv, null,
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 24.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}


// ------------------------------------------------------------------------------------ guia

@Composable
private fun GuidePage(vm: MainViewModel) {
    val s by SettingsStore.flow.collectAsState()
    val loading by Epg.loading.collectAsState()
    var editUrls by remember { mutableStateOf(false) }
    SimpleScroll {
        SettingsSection("Exibição") {
            SwitchRow("Ativar guia de programação", "Mostra o programa no ar e a grade de horários", s.epgEnabled) { v ->
                SettingsStore.update { it.copy(epgEnabled = v) }
                if (v) vm.refreshEpg(false)
            }
            RowDivider()
            SwitchRow("Programa atual nos canais", "Mostra \"▶ nome do programa\" nos cartões e na lista", s.showNowOnCards) { v ->
                SettingsStore.update { it.copy(showNowOnCards = v) }
            }
        }
        SettingsSection("Atualização") {
            IntChoiceRow(
                "Atualizar o guia automaticamente",
                listOf("Nunca", "A cada 6 horas", "A cada 12 horas", "Uma vez por dia"),
                listOf(0, 6, 12, 24),
                s.epgRefreshHours
            ) { v -> SettingsStore.update { it.copy(epgRefreshHours = v) } }
            RowDivider()
            ActionRow(
                if (loading) "Baixando guia..." else "Atualizar agora",
                "Última atualização: " + formatDateTime(s.lastEpgRefresh)
            ) { if (!loading) vm.refreshEpg(true) }
        }
        SettingsSection("Fonte do guia") {
            ActionRow("Links do guia (XMLTV)", Epg.urlList(s.epgUrls).size.toString() + " link(s) configurado(s). Toque para editar") {
                editUrls = true
            }
            RowDivider()
            ActionRow("Voltar ao link padrão", "Usa o guia do projeto iptv-com/epg para o Brasil") {
                SettingsStore.update { it.copy(epgUrls = Epg.DEFAULT_URL) }
                vm.toast("Link padrão restaurado. Toque em Atualizar agora.")
            }
            Text(
                "O guia é ligado ao canal pelo tvg-id ou, se não achar, pelo nome. Nem todo canal terá programação.",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        SettingsSection("Cache") {
            ActionRow("Limpar guia baixado", "Apaga os arquivos do guia salvos no aparelho", danger = true) {
                Epg.clearCache()
                vm.toast("Guia apagado.")
            }
        }
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
}
