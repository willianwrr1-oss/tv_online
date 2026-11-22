package com.willian.tvaberta.data

import android.content.Context
import android.util.Xml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class Programme(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String?,
    val category: String?,
)

/**
 * Guia de programação (EPG) no formato XMLTV. Baixa um ou mais arquivos, guarda em cache e lê só
 * a janela útil (de 12 h atrás até 48 h à frente). A ligação com os canais é feita pelo tvg-id
 * (sem o sufixo @SD/@HD) e, se não achar, pelo nome do canal.
 */
object Epg {
    private const val MAX_EPG_BYTES = 512L * 1024L * 1024L

    /** Copia o download em blocos, sem manter o arquivo inteiro na memória. */
    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw IOException("Arquivo EPG excede o limite de 512 MB")
            output.write(buffer, 0, n)
        }
        output.flush()
    }

    // O link antigo (iptv-com/epg) saiu do ar (HTTP 404). Hoje: guia do Brasil do epgshare01 e, de reserva, o BrazilTVEPG.
    const val OLD_DEAD_MARK = "iptv-com/epg"
    const val DEFAULT_URL = "https://epgshare01.online/epgshare01/epg_ripper_BR1.xml.gz\n" +
        "https://raw.githubusercontent.com/limaalef/BrazilTVEPG/refs/heads/main/epg.xml"
    const val SUGGESTED_URL = "https://iptv-epg.org/files/epg-br.xml"

    private lateinit var ctx: Context

    /** Motivo da última falha de download, para mostrar ao usuário. */
    @Volatile
    var lastError: String? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    @Volatile
    private var byId: Map<String, List<Programme>> = emptyMap()

    @Volatile
    private var nameToId: Map<String, String> = emptyMap()

    private val cache = ConcurrentHashMap<String, List<Programme>>()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun init(c: Context) {
        ctx = c.applicationContext
    }

    fun urlList(raw: String): List<String> =
        raw.split('\n').map { it.trim() }.filter { it.startsWith("http://") || it.startsWith("https://") }

    private fun dir(): File {
        val d = File(ctx.filesDir, "epg")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun fileFor(url: String) = File(dir(), "g" + Integer.toHexString(url.hashCode()) + ".xml")

    fun hasCache(url: String): Boolean = fileFor(url).exists()

    fun clearCache() {
        dir().listFiles()?.forEach { it.delete() }
        byId = emptyMap()
        nameToId = emptyMap()
        cache.clear()
        _version.value = _version.value + 1
    }

    // ------------------------------------------------------------------ download e leitura

    /** Baixa os guias. Devolve quantos deram certo. */
    suspend fun refresh(urls: List<String>): Int {
        if (_loading.value) return 0
        _loading.value = true
        var ok = 0
        try {
            withContext(Dispatchers.IO) {
                for (u in urls) {
                    val tmp = File(dir(), "download.tmp")
                    try {
                        val req = Request.Builder().url(u).header("User-Agent", "TVAberta/1.0").build()
                        client.newCall(req).execute().use { r ->
                            if (!r.isSuccessful) throw IOException("HTTP " + r.code)
                            val body = r.body ?: throw IOException("vazio")
                            val length = body.contentLength()
                            if (length > MAX_EPG_BYTES) throw IOException("Arquivo EPG excede o limite de 512 MB")
                            body.byteStream().use { input ->
                                tmp.outputStream().use { out -> copyLimited(input, out, MAX_EPG_BYTES) }
                            }
                        }
                        val target = fileFor(u)
                        if (!tmp.renameTo(target)) {
                            tmp.copyTo(target, overwrite = true)
                            tmp.delete()
                        }
                        ok++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = (e.message ?: e.javaClass.simpleName)
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                }
            }
            loadFromCache(urls)
        } finally {
            _loading.value = false
        }
        return ok
    }

    suspend fun loadFromCache(urls: List<String>) = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val from = now - 12L * 3_600_000L
        val to = now + 48L * 3_600_000L
        val ids = HashMap<String, MutableList<Programme>>()
        val names = HashMap<String, String>()
        for (u in urls) {
            val f = fileFor(u)
            if (!f.exists()) continue
            try {
                parseFile(f, from, to, ids, names)
            } catch (e: Exception) {
                // guia com defeito: fica o que foi lido até aqui
            }
        }
        val out = HashMap<String, List<Programme>>()
        for ((k, l) in ids) {
            out[k] = l.sortedBy { it.start }.distinctBy { it.start }
        }
        byId = out
        nameToId = names
        cache.clear()
        _version.value = _version.value + 1
    }

    private fun parseFile(
        f: File,
        from: Long,
        to: Long,
        ids: MutableMap<String, MutableList<Programme>>,
        names: MutableMap<String, String>
    ) {
        val raw = f.inputStream().buffered()
        raw.mark(4)
        val b1 = raw.read()
        val b2 = raw.read()
        raw.reset()
        val input: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
        input.use { stream ->
            val p = Xml.newPullParser()
            p.setInput(stream, null)
            val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

            var channelId: String? = null
            var inProg = false
            var progChannel: String? = null
            var pStart = 0L
            var pStop = 0L
            var pTitle: String? = null
            var pDesc: String? = null
            var pCat: String? = null

            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "channel" -> channelId = p.getAttributeValue(null, "id")
                        "display-name" -> {
                            val id = channelId
                            if (id != null) {
                                val t = p.nextText()
                                val n = normName(t)
                                if (n.isNotEmpty() && !names.containsKey(n)) names[n] = id
                            }
                        }
                        "programme" -> {
                            inProg = true
                            progChannel = p.getAttributeValue(null, "channel")
                            pStart = parseTime(fmt, p.getAttributeValue(null, "start"))
                            pStop = parseTime(fmt, p.getAttributeValue(null, "stop"))
                            pTitle = null
                            pDesc = null
                            pCat = null
                        }
                        "title" -> if (inProg && pTitle == null) pTitle = p.nextText().trim()
                        "desc" -> if (inProg && pDesc == null) pDesc = p.nextText().trim().take(400)
                        "category" -> if (inProg && pCat == null) pCat = p.nextText().trim()
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (p.name) {
                        "channel" -> channelId = null
                        "programme" -> {
                            val ch = progChannel
                            val title = pTitle
                            if (inProg && ch != null && !title.isNullOrEmpty() && pStop > from && pStart < to && pStop > pStart) {
                                ids.getOrPut(normId(ch)) { ArrayList() }.add(
                                    Programme(pStart, pStop, title, pDesc?.takeIf { it.isNotEmpty() }, pCat?.takeIf { it.isNotEmpty() })
                                )
                            }
                            inProg = false
                        }
                    }
                }
                event = p.next()
            }
        }
    }

    /** "20260111200000 -0300" -> milissegundos. Sem fuso, assume UTC. */
    private fun parseTime(fmt: SimpleDateFormat, s: String?): Long {
        if (s == null) return 0L
        val t = s.trim()
        val digitCount = t.takeWhile { it.isDigit() }.length
        if (digitCount < 12) return 0L
        val digits = t.substring(0, digitCount).padEnd(14, '0').take(14)
        val rest = t.substring(digitCount).trim()
        val tz = if (rest.length >= 5 && (rest[0] == '+' || rest[0] == '-')) {
            TimeZone.getTimeZone("GMT" + rest.substring(0, 3) + ":" + rest.substring(3, 5))
        } else {
            TimeZone.getTimeZone("UTC")
        }
        fmt.timeZone = tz
        return try {
            fmt.parse(digits)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    // ------------------------------------------------------------------ consulta

    private fun normId(s: String): String = s.trim().lowercase().substringBefore('@')

    private fun normName(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        val sb = StringBuilder()
        for (c in d) {
            if (c.isLetterOrDigit()) sb.append(c)
        }
        var r = sb.toString()
        for (suf in listOf("fhd", "hd", "sd")) {
            if (r.endsWith(suf) && r.length > suf.length + 2) {
                r = r.removeSuffix(suf)
                break
            }
        }
        return r
    }

    /** O tvg-id do canal (a chave começa com "id:" quando a lista trouxe um). */
    private fun tvgIdOf(ch: Channel): String {
        val k = ch.key.removePrefix("m:")
        return if (k.startsWith("id:")) k.removePrefix("id:") else ""
    }

    fun programmes(ch: Channel): List<Programme> {
        val cached = cache[ch.key]
        if (cached != null) return cached
        var list: List<Programme>? = null
        val id = tvgIdOf(ch)
        if (id.isNotEmpty()) list = byId[normId(id)]
        if (list == null) {
            val xid = nameToId[normName(ch.name)]
            if (xid != null) list = byId[normId(xid)]
        }
        val res = list ?: emptyList()
        cache[ch.key] = res
        return res
    }

    /** Programa no ar e o seguinte. */
    fun nowNext(ch: Channel, now: Long): Pair<Programme?, Programme?> {
        val list = programmes(ch)
        if (list.isEmpty()) return Pair(null, null)
        val cur = list.firstOrNull { it.start <= now && it.stop > now }
        val after = cur?.stop ?: now
        val next = list.firstOrNull { it.start >= after && it !== cur }
        return Pair(cur, next)
    }
}
