package com.willian.tvaberta.data

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Arquivo M3U importado: os canais dele ficam marcados no banco com o id do pacote. */
data class ImportPack(val id: String, val name: String, val time: Long)

/** Pacote exibido na tela: uma fonte (link/país), um arquivo importado ou "meus canais". */
data class PackInfo(val id: String, val name: String, val kind: String, val count: Int, val time: Long)

/**
 * Guarda tudo o que o app sabe sobre canais. O catálogo fica num banco SQLite (aguenta centenas de
 * milhares de canais); as escolhas do usuário ficam em memória e no banco; as listas baixadas ficam
 * em arquivos de cache.
 */
object Repo {
    private const val MAX_M3U_BYTES = 512L * 1024L * 1024L

    /** Copia em fluxo, limitando o tamanho para evitar consumo excessivo de armazenamento. */
    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("Arquivo M3U excede o limite de 512 MB")
            output.write(buffer, 0, n)
        }
        output.flush()
    }
    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbExec = Executors.newSingleThreadExecutor()
    private val loadLock = Mutex()
    private val wlock = Any()
    private var loaded = false

    private val dbLazy = lazy { ChannelDb(ctx) }
    val db: ChannelDb get() = dbLazy.value

    private val _states = MutableStateFlow<Map<String, ChannelState>>(emptyMap())
    val states: StateFlow<Map<String, ChannelState>> = _states

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready

    private val _packs = MutableStateFlow<List<ImportPack>>(emptyList())
    val packs: StateFlow<List<ImportPack>> = _packs

    /** Sobe quando o catálogo ou a visibilidade dos canais muda (refaz grupos e contagens). */
    private val _dataVersion = MutableStateFlow(0)
    val dataVersion: StateFlow<Int> = _dataVersion

    /** Sobe a cada escolha gravada (favoritos, recentes, contagem de reproduções). */
    private val _stateVersion = MutableStateFlow(0)
    val stateVersion: StateFlow<Int> = _stateVersion

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Registro curto do que o app encontrou ao abrir e do que fez (aparece em Ajustes > Cache > Diagnóstico). */
    val diag = MutableStateFlow("")

    fun note(msg: String) {
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        diag.update { (it + t + "  " + msg + "\n").takeLast(4000) }
    }

    fun init(c: Context) {
        ctx = c.applicationContext
        _sources.value = loadSources()
        if (sourcesDirty) persistSources()
    }

    private fun listsDir(): File {
        val d = File(ctx.filesDir, "lists")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun bumpData() {
        _dataVersion.update { it + 1 }
    }

    // ---------------------------------------------------------------- abertura e migração

    /** Abre o banco; na primeira vez traz os dados do formato antigo (listas em cache, canais à mão, escolhas). */
    suspend fun ensureLoaded() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            withContext(Dispatchers.IO) {
                val prefs = ctx.getSharedPreferences("repo", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("db_migrated", false)) {
                    try {
                        migrate()
                    } catch (e: Exception) {
                        // migração é feita uma vez só; o que falhar volta a vir das listas em cache
                    }
                    prefs.edit().putBoolean("db_migrated", true).apply()
                } else {
                    _packs.value = loadPacks()
                }
                // Banco vazio (ex.: migração incompleta): reconstrói a partir das listas já baixadas.
                if (db.count() == 0) {
                    for (src in _sources.value) {
                        if (!src.enabled) continue
                        val f = File(listsDir(), src.id + ".m3u")
                        if (f.exists()) {
                            try {
                                importSourceFile(src.id, f)
                            } catch (e: Exception) {
                                // lista com problema: segue com as outras
                            }
                        }
                    }
                }
                _states.value = db.loadStates()
                try {
                    note("Banco aberto: " + db.count() + " canais, arquivo de " + (ctx.getDatabasePath("channels.db").length() / 1024) +
                        " KB. Fontes: " + _sources.value.size + ", com cópia salva: " + _sources.value.count { hasCache(it.id) })
                } catch (e: Exception) {
                    note("Banco aberto, mas a contagem falhou: " + e.message)
                }
            }
            loaded = true
            _ready.value = true
            bumpData()
        }
    }

    private fun migrate() {
      synchronized(wlock) {
        // 1) listas baixadas
        for (src in _sources.value) {
            if (!src.enabled) continue
            val f = File(listsDir(), src.id + ".m3u")
            if (f.exists()) {
                try {
                    importSourceFile(src.id, f)
                } catch (e: Exception) {
                    // segue com as outras listas
                }
            }
        }
        // 2) canais à mão e pacotes de arquivos importados
        val packOf = HashMap<String, String>()
        val oldPacks = ArrayList<ImportPack>()
        val pf = File(ctx.filesDir, "packs.json")
        if (pf.exists()) {
            try {
                val arr = JSONArray(pf.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.getString("id")
                    oldPacks.add(ImportPack(id, o.optString("name", "Importado"), o.optLong("time", 0L)))
                    val ka = o.optJSONArray("keys")
                    if (ka != null) for (j in 0 until ka.length()) packOf[ka.getString(j)] = id
                }
            } catch (e: Exception) {
                // pacotes antigos ilegíveis: os canais viram "Meus canais"
            }
        }
        val mf = File(ctx.filesDir, "manual.json")
        if (mf.exists()) {
            try {
                val arr = JSONArray(mf.readText())
                db.inTx {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val groups = ArrayList<String>()
                        val ga = o.optJSONArray("groups")
                        if (ga != null) for (j in 0 until ga.length()) groups.add(ga.getString(j))
                        val urls = ArrayList<String>()
                        val ua = o.optJSONArray("urls")
                        if (ua != null) for (j in 0 until ua.length()) urls.add(ua.getString(j))
                        if (urls.isEmpty()) continue
                        val key = o.getString("key")
                        db.put(
                            Channel(
                                key = key,
                                name = o.getString("name"),
                                groups = if (groups.isEmpty()) listOf("Manual") else groups,
                                logo = o.optString("logo", "").ifBlank { null },
                                urls = urls,
                                userAgent = o.optString("ua", "").ifBlank { null },
                                referrer = o.optString("ref", "").ifBlank { null },
                                quality = o.optString("q", "").ifBlank { null },
                                geoBlocked = false,
                                not247 = false,
                                sourceId = "manual",
                            ),
                            packOf[key], false
                        )
                    }
                }
                db.releaseStatements()
            } catch (e: Exception) {
                // arquivo antigo ilegível
            }
        }
        _packs.value = oldPacks
        persistPacks()
        // 3) escolhas do usuário
        try {
            val old = loadStatesOld()
            if (old.isNotEmpty()) {
                val grp = old.filter { it.value.customGroup != null }.keys
                db.saveStates(old, grp)
            }
        } catch (e: Exception) {
            // escolhas antigas ilegíveis
        }
      }
    }

    private fun loadStatesOld(): Map<String, ChannelState> {
        val f = File(ctx.filesDir, "states.json")
        if (!f.exists()) return emptyMap()
        return try {
            val o = JSONObject(f.readText())
            val m = HashMap<String, ChannelState>()
            for (k in o.keys()) {
                val e = o.getJSONObject(k)
                m[k] = ChannelState(
                    disabled = e.optBoolean("d", false),
                    deleted = e.optBoolean("x", false),
                    favorite = e.optBoolean("f", false),
                    customName = e.optString("n", "").ifBlank { null },
                    plays = e.optInt("p", 0),
                    lastPlayed = e.optLong("l", 0L),
                    customGroup = e.optString("g", "").ifBlank { null },
                )
            }
            m
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ---------------------------------------------------------------- leitura das listas

    /** Lê um arquivo M3U de uma fonte para dentro do banco (troca o que havia dessa fonte). */
    private fun importSourceFile(id: String, f: File) {
        synchronized(wlock) {
            var parsed = 0
            try {
                f.bufferedReader().use { r ->
                    db.inTx {
                        db.deleteSrc(id)
                        M3uParser.parseLines(r.lineSequence(), id, "") { c ->
                            parsed++
                            db.put(c, null, true)
                        }
                        if (parsed == 0) throw IOException("A lista M3U não contém canais válidos")
                        db.reapplyCustomGroups()
                    }
                }
            } finally {
                db.releaseStatements()
            }
        }
    }

    /** Refaz o catálogo a partir das listas em cache. */
    suspend fun reload() = withContext(Dispatchers.IO) {
        for (src in _sources.value) {
            if (!src.enabled) {
                db.deleteSrc(src.id)
                continue
            }
            val f = File(listsDir(), src.id + ".m3u")
            if (f.exists()) {
                try {
                    importSourceFile(src.id, f)
                } catch (e: Exception) {
                    // lista com problema: segue com as outras
                }
            }
        }
        bumpData()
    }

    /** Baixa de novo todas as listas ativas. Devolve quantas deram certo e quantas falharam. */
    suspend fun refresh(): RefreshResult {
        if (_refreshing.value) return RefreshResult(0, 0)
        _refreshing.value = true
        var ok = 0
        var fail = 0
        try {
            ensureLoaded()
            withContext(Dispatchers.IO) {
                for (src in _sources.value) {
                    currentCoroutineContext().ensureActive()
                    if (!src.enabled) continue
                    val tmp = File(listsDir(), src.id + ".tmp")
                    try {
                        val req = Request.Builder()
                            .url(src.url)
                            .header("User-Agent", "TVAberta/1.0")
                            .build()
                        client.newCall(req).execute().use { r ->
                            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
                            val body = r.body ?: throw IOException("vazio")
                            val length = body.contentLength()
                            if (length > MAX_M3U_BYTES) throw IOException("Arquivo M3U excede o limite de 512 MB")
                            body.byteStream().use { input ->
                                tmp.outputStream().use { output -> copyLimited(input, output, MAX_M3U_BYTES) }
                            }
                        }
                        val head = tmp.bufferedReader().use { r ->
                            val buf = CharArray(4000)
                            val n = r.read(buf)
                            if (n > 0) String(buf, 0, n) else ""
                        }
                        if (!head.contains("#EXTM3U")) throw IOException("não é uma lista M3U")

                        currentCoroutineContext().ensureActive()
                        // Importa o temporário antes de substituir o cache anterior.
                        importSourceFile(src.id, tmp)
                        val target = File(listsDir(), src.id + ".m3u")
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        bumpData()
                        ok++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        fail++
                        note("Falha ao atualizar " + src.name + ": " + (e.message ?: e.javaClass.simpleName))
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                }
            }
            if (ok > 0) {
                SettingsStore.update { it.copy(lastRefresh = System.currentTimeMillis()) }
            }
        } finally {
            _refreshing.value = false
        }
        return RefreshResult(ok, fail)
    }

    fun clearListCache() {
        listsDir().listFiles()?.forEach { it.delete() }
        scope.launch {
            ensureLoaded()
            withContext(Dispatchers.IO) {
                for (src in _sources.value) db.deleteSrc(src.id)
            }
            bumpData()
        }
    }

    // ---------------------------------------------------------------- escolhas do usuário

    fun updateStates(keys: Collection<String>, f: (ChannelState) -> ChannelState) {
        if (keys.isEmpty()) return
        val changes = HashMap<String, ChannelState?>()
        val groupChanged = HashSet<String>()
        var heavy = false
        synchronized(this) {
            val old = _states.value
            val mm = HashMap<String, ChannelState>(old)
            for (k in keys) {
                val o = old[k] ?: ChannelState()
                val n = f(o)
                if (n == o) continue
                if (n == ChannelState()) {
                    mm.remove(k)
                    changes[k] = null
                } else {
                    mm[k] = n
                    changes[k] = n
                }
                if (o.customGroup != n.customGroup) groupChanged.add(k)
                if (o.disabled != n.disabled || o.deleted != n.deleted || o.customGroup != n.customGroup ||
                    o.customName != n.customName
                ) heavy = true
            }
            if (changes.isEmpty()) return
            _states.value = mm
        }
        val h = heavy
        dbExec.execute {
            try {
                db.saveStates(changes, groupChanged)
            } catch (e: Exception) {
                // gravação é "melhor esforço"
            }
            if (h) bumpData()
            _stateVersion.update { it + 1 }
        }
    }

    fun updateState(key: String, f: (ChannelState) -> ChannelState) = updateStates(listOf(key), f)

    /** Volta a lista de canais ao estado inicial: fontes originais, sem canais manuais e sem escolhas. */
    fun resetChannelList() {
        resetSources()
        _packs.value = emptyList()
        persistPacks()
        _states.value = emptyMap()
        scope.launch {
            ensureLoaded()
            withContext(Dispatchers.IO) { db.clearAll() }
            reload()
        }
    }

    fun resetHistory() {
        val keys = _states.value.filter { it.value.plays > 0 || it.value.lastPlayed > 0L }.keys.toList()
        updateStates(keys) { it.copy(plays = 0, lastPlayed = 0L) }
    }

    // ---------------------------------------------------------------- fontes (listas)

    fun addSource(id: String, name: String, url: String) {
        if (_sources.value.any { it.id == id || it.url == url }) return
        _sources.update { it + Source(id, name, url, true) }
        persistSources()
    }

    /** Traz de volta todos os canais excluídos ou desativados (não mexe em favoritos nem em nomes). */
    fun restoreAllChannels() {
        val keys = _states.value.filter { it.value.deleted || it.value.disabled }.keys.toList()
        updateStates(keys) { it.copy(deleted = false, disabled = false) }
    }

    /** Volta a lista de fontes para as quatro originais; apaga as listas baixadas das outras. */
    fun resetSources() {
        val keep = DefaultSources.list.map { it.id }.toSet()
        val gone = _sources.value.filter { it.id !in keep }
        gone.forEach { File(listsDir(), it.id + ".m3u").delete() }
        _sources.value = DefaultSources.list
        persistSources()
        scope.launch {
            ensureLoaded()
            withContext(Dispatchers.IO) { gone.forEach { db.deleteSrc(it.id) } }
            reload()
        }
    }

    fun removeSource(id: String) {
        _sources.update { list -> list.filter { it.id != id } }
        File(listsDir(), "$id.m3u").delete()
        persistSources()
        scope.launch {
            ensureLoaded()
            withContext(Dispatchers.IO) { db.inTx { db.deleteSrc(id) } }
            bumpData()
        }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        _sources.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        persistSources()
        scope.launch {
            ensureLoaded()
            withContext(Dispatchers.IO) {
                if (!enabled) {
                    db.inTx { db.deleteSrc(id) }
                } else {
                    val f = File(listsDir(), "$id.m3u")
                    if (f.exists()) importSourceFile(id, f)
                }
            }
            bumpData()
        }
    }

    /** Nome mostrado na guia de origem: fonte cadastrada, arquivo importado ou "Meus canais". */
    fun originName(sourceId: String): String = when {
        sourceId == "manual" || sourceId.startsWith("imp:") -> "Minha lista"
        else -> _sources.value.firstOrNull { it.id == sourceId }?.name ?: sourceId
    }

    fun hasCache(id: String): Boolean = File(listsDir(), "$id.m3u").exists()

    private fun persistSources() {
        val snap = _sources.value
        scope.launch {
            synchronized(this@Repo) {
                val arr = JSONArray()
                for (s in snap) {
                    arr.put(
                        JSONObject()
                            .put("id", s.id)
                            .put("name", s.name)
                            .put("url", s.url)
                            .put("enabled", s.enabled)
                    )
                }
                writeAtomic(File(ctx.filesDir, "sources.json"), arr.toString())
            }
        }
    }

    private var sourcesDirty = false

    private fun loadSources(): List<Source> {
        var list: List<Source> = emptyList()
        val f = File(ctx.filesDir, "sources.json")
        if (f.exists()) {
            try {
                val arr = JSONArray(f.readText())
                val parsed = ArrayList<Source>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    parsed.add(Source(o.getString("id"), o.getString("name"), o.getString("url"), o.optBoolean("enabled", true)))
                }
                list = parsed
            } catch (e: Exception) {
                // arquivo corrompido: recomeça pelas fontes padrão
            }
        }
        // Entrega as fontes padrão uma única vez por versão (não recoloca o que o usuário apagar depois).
        val prefs = ctx.getSharedPreferences("repo", Context.MODE_PRIVATE)
        if (prefs.getInt("defaults_version", 0) < DefaultSources.VERSION) {
            val ids = list.map { it.id }.toSet()
            val urls = list.map { it.url }.toSet()
            val add = DefaultSources.list.filter { it.id !in ids && it.url !in urls }
            if (add.isNotEmpty()) {
                list = list + add
                sourcesDirty = true
            }
            prefs.edit().putInt("defaults_version", DefaultSources.VERSION).apply()
        }
        return list
    }

    // ---------------------------------------------------------------- canais manuais e importados

    fun addManual(name: String, url: String, group: String, logo: String) {
        val key = "m:" + UUID.randomUUID().toString().take(8)
        val ch = Channel(
            key = key,
            name = name.trim(),
            groups = listOf(group.trim().ifEmpty { "Manual" }),
            logo = logo.trim().ifEmpty { null },
            urls = listOf(url.trim()),
            userAgent = null,
            referrer = null,
            quality = null,
            geoBlocked = false,
            not247 = false,
            sourceId = "manual",
        )
        scope.launch {
            ensureLoaded()
            synchronized(wlock) {
                db.inTx { db.put(ch, null, false) }
                db.releaseStatements()
            }
            bumpData()
        }
    }

    fun norm(s: String): String = ChannelDb.norm(s)

    fun normUrl(u: String): String = ChannelDb.normUrl(u)

    /**
     * Importa uma lista M3U ignorando o que o app já tem (mesmo tvg-id, mesmo nome ou mesmo link,
     * em qualquer fonte, ativa ou não). Lê aos poucos, sem carregar o arquivo inteiro.
     * Devolve (novos, ignorados).
     */
    fun importM3u(reader: Reader, listName: String): Pair<Int, Int> {
        val packId = System.currentTimeMillis().toString()
        var added = 0
        var skipped = 0
        val seen = HashSet<String>()
        synchronized(wlock) {
            reader.buffered().use { r ->
                db.inTx {
                    M3uParser.parseLines(r.lineSequence(), "manual", "m:") { c ->
                        if (!seen.add(c.key)) {
                            db.put(c, packId, true)
                        } else {
                            val nn = ChannelDb.norm(c.name)
                            val un = ChannelDb.normUrl(c.urls.firstOrNull() ?: "")
                            val idl = ChannelDb.idOf(c.key)
                            if (db.exists(nn, un, idl)) {
                                skipped++
                            } else if (db.put(c, packId, false)) {
                                added++
                            } else {
                                skipped++
                            }
                        }
                    }
                }
            }
            db.releaseStatements()
        }
        if (added > 0) {
            _packs.value = _packs.value + ImportPack(packId, listName, System.currentTimeMillis())
            persistPacks()
            bumpData()
        }
        return Pair(added, skipped)
    }

    fun importM3u(text: String, listName: String): Pair<Int, Int> = importM3u(java.io.StringReader(text), listName)

    private fun persistPacks() {
        val snap = _packs.value
        scope.launch {
            synchronized(this@Repo) {
                val arr = JSONArray()
                for (p in snap) {
                    val o = JSONObject()
                    o.put("id", p.id)
                    o.put("name", p.name)
                    o.put("time", p.time)
                    arr.put(o)
                }
                writeAtomic(File(ctx.filesDir, "packs2.json"), arr.toString())
            }
        }
    }

    private fun loadPacks(): List<ImportPack> {
        val f = File(ctx.filesDir, "packs2.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val list = ArrayList<ImportPack>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(ImportPack(o.getString("id"), o.optString("name", "Importado"), o.optLong("time", 0L)))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Todos os pacotes de canais: fontes (link/país), arquivos importados e os canais "à mão". */
    fun packInfos(): List<PackInfo> {
        val out = ArrayList<PackInfo>()
        for (src in _sources.value) {
            out.add(PackInfo("src:" + src.id, src.name, "source", db.countSrc(src.id), 0L))
        }
        for (p in _packs.value) {
            out.add(PackInfo("pk:" + p.id, p.name, "file", db.countPack(p.id), p.time))
        }
        val rest = db.countManualUnpacked()
        if (rest > 0) out.add(PackInfo("rest", "Meus canais (à mão ou importados antes)", "rest", rest, 0L))
        return out
    }

    /** Alguns canais do pacote, para mostrar o conteúdo. */
    fun packChannels(info: PackInfo, limit: Int): List<Channel> = when (info.kind) {
        "source" -> db.listSrc(info.id.removePrefix("src:"), limit)
        "file" -> db.listPack(info.id.removePrefix("pk:"), limit)
        else -> db.listManualUnpacked(limit)
    }

    /** Exclui o pacote inteiro de uma vez. Fonte: remove a fonte. Arquivo: apaga os canais dele. */
    fun deletePack(info: PackInfo) {
        if (info.kind == "source") {
            removeSource(info.id.removePrefix("src:"))
            return
        }
        scope.launch {
            ensureLoaded()
            val kill: List<String> = withContext(Dispatchers.IO) {
                if (info.kind == "file") {
                    val pid = info.id.removePrefix("pk:")
                    val ks = db.keysOfPack(pid)
                    db.inTx { db.deletePack(pid) }
                    _packs.update { l -> l.filter { it.id != pid } }
                    persistPacks()
                    ks
                } else {
                    val ks = db.keysOfManualUnpacked()
                    db.inTx { db.deleteManualUnpacked() }
                    ks
                }
            }
            if (kill.isNotEmpty()) updateStates(kill) { ChannelState() }
            bumpData()
        }
    }

    // ---------------------------------------------------------------- consultas auxiliares

    fun flt(s: AppSettings, src: String? = null, q: String? = null, kind: Int? = null): Flt =
        Flt(hideGeo = s.hideGeoBlocked, hideN247 = s.hideNot247, src = src, q = q, kind = kind)

    /** Canal + escolhas atuais (nome, grupo e favorito vêm da tabela de escolhas em memória). */
    fun item(ch: Channel, map: Map<String, ChannelState> = _states.value): ChannelItem {
        val st = map[ch.key] ?: ChannelState()
        val g = st.customGroup
        return ChannelItem(if (g != null) ch.copy(groups = listOf(g)) else ch, st)
    }

    fun byKey(key: String): Channel? = try {
        db.byKey(key)
    } catch (e: Exception) {
        null
    }

    // ---------------------------------------------------------------- backup

    /** Grava fontes, pacotes, canais à mão e todas as escolhas num arquivo JSON. */
    fun exportBackup(out: OutputStream) {
        val w = JsonWriter(out.bufferedWriter(Charsets.UTF_8))
        w.beginObject()
        w.name("app").value("tvaberta")
        w.name("version").value(1L)
        w.name("sources").beginArray()
        for (s in _sources.value) {
            w.beginObject()
            w.name("id").value(s.id)
            w.name("name").value(s.name)
            w.name("url").value(s.url)
            w.name("enabled").value(s.enabled)
            w.endObject()
        }
        w.endArray()
        w.name("packs").beginArray()
        for (p in _packs.value) {
            w.beginObject()
            w.name("id").value(p.id)
            w.name("name").value(p.name)
            w.name("time").value(p.time)
            w.endObject()
        }
        w.endArray()
        w.name("manual").beginArray()
        db.forEachManual { c, pack ->
            w.beginObject()
            w.name("key").value(c.key)
            w.name("name").value(c.name)
            w.name("groups").beginArray()
            for (g in c.groups) w.value(g)
            w.endArray()
            w.name("urls").beginArray()
            for (u in c.urls) w.value(u)
            w.endArray()
            if (c.logo != null) w.name("logo").value(c.logo)
            if (c.userAgent != null) w.name("ua").value(c.userAgent)
            if (c.referrer != null) w.name("ref").value(c.referrer)
            if (c.quality != null) w.name("q").value(c.quality)
            if (pack != null) w.name("pack").value(pack)
            w.endObject()
        }
        w.endArray()
        w.name("states").beginObject()
        for ((k, v) in _states.value) {
            w.name(k).beginObject()
            if (v.disabled) w.name("d").value(true)
            if (v.deleted) w.name("x").value(true)
            if (v.favorite) w.name("f").value(true)
            if (!v.customName.isNullOrBlank()) w.name("n").value(v.customName)
            if (v.plays > 0) w.name("p").value(v.plays.toLong())
            if (v.lastPlayed > 0L) w.name("l").value(v.lastPlayed)
            if (!v.customGroup.isNullOrBlank()) w.name("g").value(v.customGroup)
            w.endObject()
        }
        w.endObject()
        w.endObject()
        w.flush()
        w.close()
    }

    /**
     * Restaura um backup: troca fontes, canais à mão e escolhas pelas do arquivo. Depois recarrega as
     * listas do cache (quem não tiver cache precisa de "Atualizar agora"). Devolve false se o arquivo
     * não for um backup do app.
     */
    suspend fun importBackup(input: InputStream): Boolean = withContext(Dispatchers.IO) {
        ensureLoaded()
        val srcs = ArrayList<Source>()
        val packs = ArrayList<ImportPack>()
        val manualList = ArrayList<Pair<Channel, String?>>()
        val st = HashMap<String, ChannelState>()
        var isBackup = false
        try {
            val r = JsonReader(input.bufferedReader(Charsets.UTF_8))
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "app" -> isBackup = r.nextString() == "tvaberta"
                    "sources" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var id = ""
                            var name = ""
                            var url = ""
                            var en = true
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "id" -> id = r.nextString()
                                    "name" -> name = r.nextString()
                                    "url" -> url = r.nextString()
                                    "enabled" -> en = r.nextBoolean()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (id.isNotEmpty() && url.isNotEmpty()) srcs.add(Source(id, name, url, en))
                        }
                        r.endArray()
                    }
                    "packs" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var id = ""
                            var name = "Importado"
                            var time = 0L
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "id" -> id = r.nextString()
                                    "name" -> name = r.nextString()
                                    "time" -> time = r.nextLong()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (id.isNotEmpty()) packs.add(ImportPack(id, name, time))
                        }
                        r.endArray()
                    }
                    "manual" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var key = ""
                            var name = ""
                            val groups = ArrayList<String>()
                            val urls = ArrayList<String>()
                            var logo: String? = null
                            var ua: String? = null
                            var ref: String? = null
                            var q: String? = null
                            var pack: String? = null
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "key" -> key = r.nextString()
                                    "name" -> name = r.nextString()
                                    "groups" -> {
                                        r.beginArray()
                                        while (r.hasNext()) groups.add(r.nextString())
                                        r.endArray()
                                    }
                                    "urls" -> {
                                        r.beginArray()
                                        while (r.hasNext()) urls.add(r.nextString())
                                        r.endArray()
                                    }
                                    "logo" -> logo = r.nextString()
                                    "ua" -> ua = r.nextString()
                                    "ref" -> ref = r.nextString()
                                    "q" -> q = r.nextString()
                                    "pack" -> pack = r.nextString()
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            if (key.isNotEmpty() && urls.isNotEmpty()) {
                                manualList.add(
                                    Pair(
                                        Channel(
                                            key, name, if (groups.isEmpty()) listOf("Manual") else groups, logo,
                                            urls, ua, ref, q, false, false, "manual"
                                        ),
                                        pack
                                    )
                                )
                            }
                        }
                        r.endArray()
                    }
                    "states" -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val k = r.nextName()
                            var s = ChannelState()
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "d" -> s = s.copy(disabled = r.nextBoolean())
                                    "x" -> s = s.copy(deleted = r.nextBoolean())
                                    "f" -> s = s.copy(favorite = r.nextBoolean())
                                    "n" -> s = s.copy(customName = r.nextString())
                                    "p" -> s = s.copy(plays = r.nextInt())
                                    "l" -> s = s.copy(lastPlayed = r.nextLong())
                                    "g" -> s = s.copy(customGroup = r.nextString())
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            st[k] = s
                        }
                        r.endObject()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        } catch (e: Exception) {
            return@withContext false
        }
        if (!isBackup) return@withContext false

        _sources.value = if (srcs.isEmpty()) _sources.value else srcs
        persistSources()
        _packs.value = packs
        persistPacks()
        synchronized(wlock) {
            db.inTx {
                db.deleteManualAll()
                for ((c, pack) in manualList) db.put(c, pack, false)
                db.clearStates()
            }
            db.releaseStatements()
        }
        val grp = st.filter { it.value.customGroup != null }.keys
        db.saveStates(st, grp)
        _states.value = st
        reload()
        _stateVersion.update { it + 1 }
        true
    }

    private fun writeAtomic(target: File, text: String) {
        try {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) {
                target.writeText(text)
                tmp.delete()
            }
        } catch (e: Exception) {
            // gravação é "melhor esforço"
        }
    }
}
