package com.willian.tvaberta

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.ChannelState
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import com.willian.tvaberta.player.PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UiEvent(
    val text: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val player = PlayerController(app)

    val items: StateFlow<List<ChannelItem>> =
        combine(Repo.channels, Repo.states) { chs, sts ->
            chs.map { ChannelItem(it, sts[it.key] ?: ChannelState()) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)

    var tab by mutableIntStateOf(0)
    var settingsPage by mutableStateOf<String?>(null)
    var inPip by mutableStateOf(false)
    var renameTarget by mutableStateOf<ChannelItem?>(null)
    var pendingDelete by mutableStateOf<List<String>>(emptyList())

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)

    init {
        viewModelScope.launch {
            Repo.reload()
            val s = SettingsStore.value
            val limit = s.autoRefreshHours * 3_600_000L
            val stale = s.autoRefreshHours > 0 && System.currentTimeMillis() - s.lastRefresh > limit
            val missing = Repo.sources.value.any { it.enabled && !Repo.hasCache(it.id) }
            if (Repo.channels.value.isEmpty() || stale || missing) {
                doRefresh(false)
            }
            if (s.reopenLast && s.lastChannelKey.isNotEmpty() && player.current == null) {
                val c = Repo.channels.value.firstOrNull { it.key == s.lastChannelKey }
                val st = Repo.states.value[s.lastChannelKey]
                if (c != null && st?.disabled != true && st?.deleted != true) {
                    play(c, listOf(c))
                }
            }
            // Guia de programação: usa o cache na hora e baixa de novo só se estiver velho.
            val g = SettingsStore.value
            if (g.epgEnabled) {
                val urls = Epg.urlList(g.epgUrls)
                Epg.loadFromCache(urls)
                val epgLimit = g.epgRefreshHours * 3_600_000L
                val epgStale = g.epgRefreshHours > 0 && System.currentTimeMillis() - g.lastEpgRefresh > epgLimit
                val epgMissing = urls.any { !Epg.hasCache(it) }
                if (epgStale || epgMissing) doRefreshEpg(false)
            }
        }
    }

    // ------------------------------------------------------------------ guia de programação

    fun refreshEpg(manual: Boolean) {
        viewModelScope.launch { doRefreshEpg(manual) }
    }

    private suspend fun doRefreshEpg(manual: Boolean) {
        val urls = Epg.urlList(SettingsStore.value.epgUrls)
        if (urls.isEmpty()) {
            if (manual) toast("Nenhum link de guia configurado. Veja Configurações > Guia de programação.")
            return
        }
        val ok = Epg.refresh(urls)
        if (ok > 0) {
            SettingsStore.update { it.copy(lastEpgRefresh = System.currentTimeMillis()) }
            if (manual) toast("Guia de programação atualizado.")
        } else if (manual) {
            toast("Não foi possível baixar o guia. Verifique o link e a conexão.")
        }
    }

    // ------------------------------------------------------------------ atualização das listas

    fun refresh(manual: Boolean) {
        viewModelScope.launch { doRefresh(manual) }
    }

    private suspend fun doRefresh(manual: Boolean) {
        if (Repo.sources.value.none { it.enabled }) {
            if (manual) toast("Nenhuma fonte de canais ativa. Ative uma em Configurações > Fontes.")
            return
        }
        val r = Repo.refresh()
        if (r.ok == 0 && r.fail == 0) return
        if (r.fail > 0 && r.ok == 0) {
            toast("Não foi possível baixar a lista. Verifique a conexão com a internet.")
        } else if (r.fail > 0) {
            toast("Lista atualizada, mas ${r.fail} fonte(s) falharam.")
        } else if (manual) {
            toast("Lista de canais atualizada.")
        }
    }

    // ------------------------------------------------------------------ reprodução

    fun play(ch: Channel, queue: List<Channel>) {
        inPip = false
        player.play(ch, queue)
    }

    fun closePlayer() {
        player.stop()
    }

    // ------------------------------------------------------------------ ações nos canais

    fun toast(text: String) {
        events.tryEmit(UiEvent(text))
    }

    fun toggleFavorite(key: String) {
        Repo.updateState(key) { it.copy(favorite = !it.favorite) }
    }

    fun setDisabled(keys: List<String>, disabled: Boolean, withUndo: Boolean = true) {
        Repo.updateStates(keys) { it.copy(disabled = disabled) }
        val n = keys.size
        val msg = if (disabled) {
            if (n == 1) "Canal desativado." else "$n canais desativados."
        } else {
            if (n == 1) "Canal ativado." else "$n canais ativados."
        }
        if (withUndo) {
            events.tryEmit(UiEvent(msg, "Desfazer") { Repo.updateStates(keys) { it.copy(disabled = !disabled) } })
        } else {
            toast(msg)
        }
    }

    /** Pede confirmação (se estiver ligada nos ajustes) e exclui. */
    fun requestDelete(keys: List<String>) {
        if (keys.isEmpty()) return
        if (SettingsStore.value.confirmDelete) pendingDelete = keys else delete(keys)
    }

    fun delete(keys: List<String>) {
        Repo.updateStates(keys) { it.copy(deleted = true, favorite = false) }
        val n = keys.size
        val msg = if (n == 1) "Canal excluído." else "$n canais excluídos."
        events.tryEmit(UiEvent(msg, "Desfazer") { restore(keys, false) })
    }

    fun restore(keys: List<String>, announce: Boolean = true) {
        Repo.updateStates(keys) { it.copy(deleted = false) }
        if (announce) {
            toast(if (keys.size == 1) "Canal restaurado." else "${keys.size} canais restaurados.")
        }
    }

    /**
     * Acha canais repetidos (mesmo nome ou mesmo link) e exclui as cópias, deixando um de cada.
     * Fica o favorito, depois o ativo, depois o mais assistido. Dá para desfazer.
     */
    fun removeDuplicates() {
        val list = items.value.filter { !it.st.deleted }
        val parent = IntArray(list.size) { it }
        fun find(x: Int): Int {
            var r = x
            while (parent[r] != r) r = parent[r]
            var c = x
            while (parent[c] != r) {
                val n = parent[c]
                parent[c] = r
                c = n
            }
            return r
        }
        val byName = HashMap<String, Int>()
        val byUrl = HashMap<String, Int>()
        for ((i, ci) in list.withIndex()) {
            val nk = Repo.norm(ci.title)
            if (nk.isNotEmpty()) {
                val j = byName[nk]
                if (j == null) byName[nk] = i else parent[find(i)] = find(j)
            }
            for (u in ci.ch.urls) {
                val uk = Repo.normUrl(u)
                val j = byUrl[uk]
                if (j == null) byUrl[uk] = i else parent[find(i)] = find(j)
            }
        }
        val groups = HashMap<Int, MutableList<ChannelItem>>()
        for (i in list.indices) groups.getOrPut(find(i)) { mutableListOf() }.add(list[i])
        val toDelete = ArrayList<String>()
        for (g in groups.values) {
            if (g.size < 2) continue
            val best = g.sortedWith(
                compareByDescending<ChannelItem> { it.st.favorite }
                    .thenBy { it.st.disabled }
                    .thenByDescending { it.st.plays }
                    .thenByDescending { it.ch.urls.size }
            ).first()
            for (c in g) if (c.ch.key != best.ch.key) toDelete.add(c.ch.key)
        }
        if (toDelete.isEmpty()) {
            toast("Nenhum canal duplicado encontrado.")
            return
        }
        Repo.updateStates(toDelete) { it.copy(deleted = true, favorite = false) }
        val msg = if (toDelete.size == 1) "1 canal duplicado excluído." else toDelete.size.toString() + " canais duplicados excluídos."
        events.tryEmit(UiEvent(msg, "Desfazer") { restore(toDelete, false) })
    }

    fun rename(key: String, name: String) {
        Repo.updateState(key) { it.copy(customName = name.trim().ifEmpty { null }) }
    }

    fun addChannel(name: String, url: String, group: String, logo: String) {
        Repo.addManual(name, url, group, logo)
        toast("Canal adicionado.")
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            val res: Pair<Int, Int>? = withContext(Dispatchers.IO) {
                try {
                    val text = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    if (text == null) null else Repo.importM3u(text)
                } catch (e: Exception) {
                    null
                }
            }
            if (res == null) {
                toast("Não foi possível ler o arquivo.")
            } else if (res.first == 0) {
                toast("Nenhum canal novo: " + res.second + " já existiam.")
            } else {
                toast(res.first.toString() + " canais novos importados, " + res.second + " ignorados (já existiam).")
            }
        }
    }

    // ------------------------------------------------------------------ fontes e dados

    fun addCountry(code: String, name: String) {
        Repo.addSource(code, name, com.willian.tvaberta.data.Countries.countryUrl(code))
        refresh(true)
    }

    fun addCustomSource(name: String, url: String) {
        Repo.addSource("u" + System.currentTimeMillis(), name.ifBlank { "Lista própria" }, url.trim())
        refresh(true)
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        Repo.setSourceEnabled(id, enabled)
        if (enabled && !Repo.hasCache(id)) refresh(true)
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
