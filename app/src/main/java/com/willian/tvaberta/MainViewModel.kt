package com.willian.tvaberta

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.willian.tvaberta.data.Channel
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Flt
import com.willian.tvaberta.data.ChannelState
import com.willian.tvaberta.data.Epg
import com.willian.tvaberta.data.Repo
import com.willian.tvaberta.data.SettingsStore
import com.willian.tvaberta.player.PlayerController
import com.willian.tvaberta.ui.LiveGroup
import com.willian.tvaberta.ui.LiveHead
import com.willian.tvaberta.ui.LiveModel
import com.willian.tvaberta.ui.buildLiveHead
import com.willian.tvaberta.ui.catLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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

    val query = MutableStateFlow("")
    val category = MutableStateFlow<String?>(null)
    val origin = MutableStateFlow<String?>(null)

    var tab by mutableIntStateOf(0)
    var settingsPage by mutableStateOf<String?>(null)
    var inPip by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var previewMode by mutableStateOf(false)
    var liveGroup by mutableStateOf<String?>(null)
    var renameTarget by mutableStateOf<ChannelItem?>(null)
    var pendingDelete by mutableStateOf<List<String>>(emptyList())

    private class LiveKey(val hideGeo: Boolean, val hideN247: Boolean, val sortMode: Int)

    private class LiveParams(val ready: Boolean, val key: LiveKey, val q: String, val org: String?)

    private class OpenPage(val id: String?, val items: List<com.willian.tvaberta.data.Channel>, val more: Boolean)

    private val pageSize = 300
    private val groupLimit = MutableStateFlow<Pair<String?, Int>>(Pair(null, pageSize))

    private val liveKey = SettingsStore.flow
        .map { LiveKey(it.hideGeoBlocked, it.hideNot247, it.sortMode) }
        .distinctUntilChanged { a, b -> a.hideGeo == b.hideGeo && a.hideN247 == b.hideN247 && a.sortMode == b.sortMode }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val liveParams = combine(Repo.ready, Repo.dataVersion, liveKey, query.debounce(200), origin) { r, _, k, q, o ->
        LiveParams(r, k, q.trim(), o)
    }

    private fun fltOf(p: LiveParams, src: String?): Flt =
        Flt(hideGeo = p.key.hideGeo, hideN247 = p.key.hideN247, src = src, q = p.q.ifEmpty { null })

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val liveHead: StateFlow<LiveHead?> = liveParams
        .mapLatest { p ->
            if (!p.ready) null
            else withContext(Dispatchers.Default) { buildLiveHead(SettingsStore.value, p.q, p.org) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Favoritos e recentes (atalhos no topo da lista). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val liveLight: StateFlow<Pair<List<com.willian.tvaberta.data.Channel>, List<com.willian.tvaberta.data.Channel>>?> =
        combine(liveParams, Repo.stateVersion) { p, _ -> p }
            .mapLatest { p ->
                if (!p.ready) null
                else withContext(Dispatchers.Default) {
                    val f = fltOf(p, p.org)
                    Pair(Repo.db.favorites(f, p.key.sortMode, 500), Repo.db.recents(f, 20))
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Canais do grupo aberto, em páginas (cada rolagem até o fim pede a próxima). */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val openPage: StateFlow<OpenPage?> =
        combine(liveParams, snapshotFlow { liveGroup }, groupLimit) { p, g, lim -> Triple(p, g, lim) }
            .mapLatest { (p, g, lim) ->
                if (!p.ready || g == null || !g.startsWith("c:")) {
                    OpenPage(g, emptyList(), false)
                } else withContext(Dispatchers.Default) {
                    val limit = if (lim.first == g) lim.second else pageSize
                    val f = fltOf(p, p.org)
                    val list = Repo.db.channelsInGroup(f, g.removePrefix("c:"), p.key.sortMode, limit + 1, 0)
                    OpenPage(g, if (list.size > limit) list.subList(0, limit) else list, list.size > limit)
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Grupos da tela de TV ao vivo, montados em segundo plano a partir do banco. */
    val liveModel: StateFlow<LiveModel> =
        combine(liveHead, liveLight, openPage, Repo.states) { h, l, o, st ->
            if (h == null) {
                LiveModel.EMPTY
            } else {
                val groups = ArrayList<LiveGroup>(h.cats.size + 2)
                val favs = l?.first ?: emptyList()
                if (favs.isNotEmpty()) groups.add(LiveGroup("fav", "Favoritos", favs.size, favs.map { Repo.item(it, st) }, false))
                val recents = l?.second ?: emptyList()
                if (recents.isNotEmpty()) {
                    groups.add(LiveGroup("rec", "Assistidos recentemente", recents.size, recents.map { Repo.item(it, st) }, false))
                }
                for (c in h.cats) {
                    val id = "c:" + c.grp
                    val open = o != null && o.id == id
                    groups.add(
                        LiveGroup(
                            id, catLabel(c.grp), c.n,
                            if (open) o!!.items.map { Repo.item(it, st) } else emptyList(),
                            true, open && o!!.more
                        )
                    )
                }
                LiveModel(groups, h.total, h.origins, h.org, true)
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.Eagerly, LiveModel.EMPTY)

    /** Pede a próxima página do grupo aberto. */
    fun loadMoreGroup() {
        val g = liveGroup ?: return
        val cur = groupLimit.value
        val base = if (cur.first == g) cur.second else pageSize
        groupLimit.value = Pair(g, base + pageSize)
    }

    /** Favoritos (lista completa da aba Favoritos), já com busca e ordem. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favorites: StateFlow<List<ChannelItem>> =
        combine(liveParams, Repo.stateVersion) { p, _ -> p }
            .mapLatest { p ->
                if (!p.ready) emptyList()
                else withContext(Dispatchers.Default) {
                    Repo.db.favorites(fltOf(p, null), p.key.sortMode, 3000).map { Repo.item(it) }
                }
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun currentFlt(src: String?): Flt {
        val s = SettingsStore.value
        return Repo.flt(s, src, query.value.trim().ifEmpty { null })
    }

    /** Chaves de todos os canais de um grupo da lista ao vivo (respeita busca e lista de origem). */
    suspend fun keysOfGroup(id: String): List<String> = withContext(Dispatchers.IO) {
        val f = currentFlt(origin.value)
        when {
            id == "fav" -> Repo.db.favorites(f, 0, 100000).map { it.key }
            id == "rec" -> Repo.db.recents(f, 20).map { it.key }
            id.startsWith("c:") -> Repo.db.groupKeys(f, id.removePrefix("c:"))
            else -> emptyList()
        }
    }

    /** Chaves de todos os canais de uma lista de origem. */
    suspend fun keysOfOrigin(src: String): List<String> = withContext(Dispatchers.IO) {
        Repo.db.srcKeys(Repo.flt(SettingsStore.value, src, null))
    }

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)

    init {
        viewModelScope.launch {
            Repo.ensureLoaded()
            val s = SettingsStore.value
            val limit = s.autoRefreshHours * 3_600_000L
            val stale = s.autoRefreshHours > 0 && System.currentTimeMillis() - s.lastRefresh > limit
            val missing = Repo.sources.value.any { it.enabled && !Repo.hasCache(it.id) }
            val empty = withContext(Dispatchers.IO) { Repo.db.count() == 0 }
            if (empty || stale || missing) {
                doRefresh(false)
            }
            if (s.reopenLast && s.lastChannelKey.isNotEmpty() && player.current == null) {
                val c = withContext(Dispatchers.IO) { Repo.byKey(s.lastChannelKey) }
                val st = Repo.states.value[s.lastChannelKey]
                if (c != null && st?.disabled != true && st?.deleted != true) {
                    playPreview(c, listOf(c))
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

    /** Reproduz em tela cheia (Favoritos, Guia). */
    fun play(ch: Channel, queue: List<Channel>) {
        inPip = false
        previewMode = false
        fullscreen = true
        player.play(ch, queue)
    }

    /** Reproduz na pré-visualização da tela de TV ao vivo. */
    fun playPreview(ch: Channel, queue: List<Channel>) {
        inPip = false
        previewMode = true
        fullscreen = false
        player.play(ch, queue)
    }

    fun openFullscreen() {
        if (player.current != null) fullscreen = true
    }

    fun closePlayer() {
        if (previewMode && fullscreen) {
            fullscreen = false
        } else {
            player.stop()
            previewMode = false
            fullscreen = false
        }
    }

    fun stopPreview() {
        player.stop()
        previewMode = false
        fullscreen = false
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
        viewModelScope.launch {
            val toDelete = withContext(Dispatchers.Default) { findDuplicates() }
            if (toDelete.isEmpty()) {
                toast("Nenhum canal duplicado encontrado.")
                return@launch
            }
            Repo.updateStates(toDelete) { it.copy(deleted = true, favorite = false) }
            val msg = if (toDelete.size == 1) "1 canal duplicado excluído." else toDelete.size.toString() + " canais duplicados excluídos."
            events.tryEmit(UiEvent(msg, "Desfazer") { restore(toDelete, false) })
        }
    }

    private fun findDuplicates(): List<String> {
        val st = Repo.states.value
        val keys = ArrayList<String>()
        val nUrls = ArrayList<Int>()
        var parent = IntArray(1024) { it }
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
        Repo.db.forEachForDedupe { key, nn, un, n ->
            val i = keys.size
            keys.add(key)
            nUrls.add(n)
            if (i >= parent.size) parent = parent.copyOf(parent.size * 2).also { for (j in parent.size until it.size) it[j] = j }
            val custom = st[key]?.customName
            val nk = if (custom.isNullOrBlank()) nn else Repo.norm(custom)
            if (nk.isNotEmpty()) {
                val j = byName[nk]
                if (j == null) byName[nk] = i else parent[find(i)] = find(j)
            }
            if (un.isNotEmpty()) {
                val j = byUrl[un]
                if (j == null) byUrl[un] = i else parent[find(i)] = find(j)
            }
        }
        val groups = HashMap<Int, MutableList<Int>>()
        for (i in keys.indices) groups.getOrPut(find(i)) { ArrayList() }.add(i)
        val toDelete = ArrayList<String>()
        for (g in groups.values) {
            if (g.size < 2) continue
            val best = g.sortedWith(
                compareByDescending<Int> { st[keys[it]]?.favorite == true }
                    .thenBy { st[keys[it]]?.disabled == true }
                    .thenByDescending { st[keys[it]]?.plays ?: 0 }
                    .thenByDescending { nUrls[it] }
            ).first()
            for (i in g) if (i != best) toDelete.add(keys[i])
        }
        return toDelete
    }

    /** Move canais para outro grupo (ou, com null, volta ao grupo original). Dá para desfazer. */
    fun moveToGroup(keys: List<String>, group: String?) {
        if (keys.isEmpty()) return
        val g = group?.trim()?.ifEmpty { null }
        val before = HashMap<String, String?>()
        for (k in keys) before[k] = Repo.states.value[k]?.customGroup
        Repo.updateStates(keys) { it.copy(customGroup = g) }
        val n = keys.size
        val msg = if (g == null) {
            if (n == 1) "Canal voltou ao grupo original." else n.toString() + " canais voltaram ao grupo original."
        } else {
            (if (n == 1) "Canal movido" else n.toString() + " canais movidos") + " para " + g + "."
        }
        events.tryEmit(UiEvent(msg, "Desfazer") {
            for ((k, v) in before) Repo.updateState(k) { it.copy(customGroup = v) }
        })
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
                    val cr = getApplication<Application>().contentResolver
                    var name: String? = try {
                        cr.query(uri, null, null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (i >= 0) c.getString(i) else null
                            } else null
                        }
                    } catch (e: Exception) {
                        null
                    }
                    name = (name ?: "").replace(Regex("(?i)\\.(m3u8?|txt)$"), "").trim()
                    if (name.isEmpty()) name = "Lista importada"
                    Repo.ensureLoaded()
                    val ins = cr.openInputStream(uri)
                    if (ins == null) null else Repo.importM3u(ins.reader(Charsets.UTF_8), name)
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

    // ------------------------------------------------------------------ backup

    fun backupTo(okMessage: String = "Backup salvo.", open: () -> java.io.OutputStream?) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    Repo.ensureLoaded()
                    val o = open()
                    if (o == null) false else {
                        o.use { Repo.exportBackup(it) }
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }
            toast(if (ok) okMessage else "Não foi possível salvar o backup.")
        }
    }

    fun restoreFrom(open: () -> java.io.InputStream?) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    val i = open()
                    if (i == null) false else i.use { Repo.importBackup(it) }
                } catch (e: Exception) {
                    false
                }
            }
            if (!ok) {
                toast("Esse arquivo não é um backup do TV Aberta.")
                return@launch
            }
            toast("Backup restaurado.")
            if (Repo.sources.value.any { it.enabled && !Repo.hasCache(it.id) }) doRefresh(false)
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
