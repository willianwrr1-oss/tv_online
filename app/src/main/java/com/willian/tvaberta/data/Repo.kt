package com.willian.tvaberta.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Guarda tudo o que o app sabe sobre canais: as listas baixadas (cache em arquivos), as escolhas do
 * usuário (desativar, excluir, favoritar, renomear) e os canais adicionados à mão.
 */
object Repo {
    private lateinit var ctx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels

    private val _states = MutableStateFlow<Map<String, ChannelState>>(emptyMap())
    val states: StateFlow<Map<String, ChannelState>> = _states

    private val _sources = MutableStateFlow<List<Source>>(emptyList())
    val sources: StateFlow<List<Source>> = _sources

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing

    private var manual: List<Channel> = emptyList()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(c: Context) {
        ctx = c.applicationContext
        _sources.value = loadSources()
        if (sourcesDirty) persistSources()
        _states.value = loadStates()
        manual = loadManual()
    }

    private fun listsDir(): File {
        val d = File(ctx.filesDir, "lists")
        if (!d.exists()) d.mkdirs()
        return d
    }

    // ---------------------------------------------------------------- leitura das listas

    suspend fun reload() = withContext(Dispatchers.Default) {
        val map = LinkedHashMap<String, Channel>()
        for (src in _sources.value) {
            if (!src.enabled) continue
            val f = File(listsDir(), src.id + ".m3u")
            if (!f.exists()) continue
            val parsed = try {
                M3uParser.parse(f.readText(), src.id)
            } catch (e: Exception) {
                emptyList()
            }
            for (c in parsed) merge(map, c)
        }
        for (c in manual) merge(map, c)
        _channels.value = map.values.toList()
    }

    private fun merge(map: MutableMap<String, Channel>, c: Channel) {
        val ex = map[c.key]
        if (ex == null) {
            map[c.key] = c
        } else {
            map[c.key] = ex.copy(urls = (ex.urls + c.urls).distinct())
        }
    }

    /** Baixa de novo todas as listas ativas. Devolve quantas deram certo e quantas falharam. */
    suspend fun refresh(): RefreshResult {
        if (_refreshing.value) return RefreshResult(0, 0)
        _refreshing.value = true
        var ok = 0
        var fail = 0
        try {
            withContext(Dispatchers.IO) {
                for (src in _sources.value) {
                    if (!src.enabled) continue
                    try {
                        val req = Request.Builder()
                            .url(src.url)
                            .header("User-Agent", "TVAberta/1.0")
                            .build()
                        client.newCall(req).execute().use { r ->
                            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
                            val body = r.body?.string() ?: throw IOException("vazio")
                            if (!body.contains("#EXTM3U")) throw IOException("não é uma lista M3U")
                            File(listsDir(), src.id + ".m3u").writeText(body)
                        }
                        ok++
                    } catch (e: Exception) {
                        fail++
                    }
                }
            }
            reload()
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
        scope.launch { reload() }
    }

    // ---------------------------------------------------------------- escolhas do usuário

    fun updateStates(keys: Collection<String>, f: (ChannelState) -> ChannelState) {
        if (keys.isEmpty()) return
        _states.update { m ->
            val mm = HashMap<String, ChannelState>(m)
            for (k in keys) {
                val n = f(mm[k] ?: ChannelState())
                if (n == ChannelState()) mm.remove(k) else mm[k] = n
            }
            mm
        }
        persistStates()
    }

    fun updateState(key: String, f: (ChannelState) -> ChannelState) = updateStates(listOf(key), f)

    fun resetHistory() {
        val keys = _states.value.keys.toList()
        updateStates(keys) { it.copy(plays = 0, lastPlayed = 0L) }
    }

    private fun persistStates() {
        scope.launch {
            synchronized(this@Repo) {
                val o = JSONObject()
                for ((k, v) in _states.value) {
                    val e = JSONObject()
                    if (v.disabled) e.put("d", true)
                    if (v.deleted) e.put("x", true)
                    if (v.favorite) e.put("f", true)
                    if (!v.customName.isNullOrBlank()) e.put("n", v.customName)
                    if (v.plays > 0) e.put("p", v.plays)
                    if (v.lastPlayed > 0L) e.put("l", v.lastPlayed)
                    o.put(k, e)
                }
                writeAtomic(File(ctx.filesDir, "states.json"), o.toString())
            }
        }
    }

    private fun loadStates(): Map<String, ChannelState> {
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
                )
            }
            m
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ---------------------------------------------------------------- fontes (listas)

    fun addSource(id: String, name: String, url: String) {
        if (_sources.value.any { it.id == id || it.url == url }) return
        _sources.update { it + Source(id, name, url, true) }
        persistSources()
    }

    fun removeSource(id: String) {
        _sources.update { list -> list.filter { it.id != id } }
        File(listsDir(), "$id.m3u").delete()
        persistSources()
        scope.launch { reload() }
    }

    fun setSourceEnabled(id: String, enabled: Boolean) {
        _sources.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
        persistSources()
        scope.launch { reload() }
    }

    fun hasCache(id: String): Boolean = File(listsDir(), "$id.m3u").exists()

    private fun persistSources() {
        scope.launch {
            synchronized(this@Repo) {
                val arr = JSONArray()
                for (s in _sources.value) {
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

    // ---------------------------------------------------------------- canais manuais

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
        manual = manual + ch
        persistManual()
        scope.launch { reload() }
    }

    /** Importa um arquivo M3U do aparelho como canais manuais. Devolve quantos canais novos entraram. */
    fun norm(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "")

    fun normUrl(u: String): String =
        u.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')

    /**
     * Importa uma lista M3U ignorando o que o app já tem (mesmo tvg-id, mesmo nome ou mesmo link,
     * em qualquer fonte, ativa ou não). Devolve (novos, ignorados).
     */
    fun importM3u(text: String): Pair<Int, Int> {
        val parsed = M3uParser.parse(text, "manual", keyPrefix = "m:")
        val have = _channels.value + manual
        val names = HashSet<String>()
        val ids = HashSet<String>()
        val urls = HashSet<String>()
        for (c in have) {
            names.add(norm(c.name))
            if (c.key.contains("id:")) ids.add(c.key.substringAfter("id:").lowercase())
            for (u in c.urls) urls.add(normUrl(u))
        }
        val add = ArrayList<Channel>()
        var skipped = 0
        for (c in parsed) {
            val id = if (c.key.contains("id:")) c.key.substringAfter("id:").lowercase() else null
            val dup = norm(c.name) in names ||
                (id != null && id in ids) ||
                c.urls.any { normUrl(it) in urls }
            if (dup) {
                skipped++
            } else {
                add.add(c)
                names.add(norm(c.name))
                if (id != null) ids.add(id)
                for (u in c.urls) urls.add(normUrl(u))
            }
        }
        if (add.isNotEmpty()) {
            manual = manual + add
            persistManual()
            scope.launch { reload() }
        }
        return Pair(add.size, skipped)
    }

    private fun persistManual() {
        val snap = manual
        scope.launch {
            synchronized(this@Repo) {
                val arr = JSONArray()
                for (c in snap) {
                    val o = JSONObject()
                    o.put("key", c.key)
                    o.put("name", c.name)
                    o.put("groups", JSONArray(c.groups))
                    o.put("urls", JSONArray(c.urls))
                    if (c.logo != null) o.put("logo", c.logo)
                    if (c.userAgent != null) o.put("ua", c.userAgent)
                    if (c.referrer != null) o.put("ref", c.referrer)
                    if (c.quality != null) o.put("q", c.quality)
                    arr.put(o)
                }
                writeAtomic(File(ctx.filesDir, "manual.json"), arr.toString())
            }
        }
    }

    private fun loadManual(): List<Channel> {
        val f = File(ctx.filesDir, "manual.json")
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            val list = ArrayList<Channel>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val groups = ArrayList<String>()
                val ga = o.optJSONArray("groups")
                if (ga != null) for (j in 0 until ga.length()) groups.add(ga.getString(j))
                val urls = ArrayList<String>()
                val ua = o.optJSONArray("urls")
                if (ua != null) for (j in 0 until ua.length()) urls.add(ua.getString(j))
                if (urls.isEmpty()) continue
                list.add(
                    Channel(
                        key = o.getString("key"),
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
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
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
