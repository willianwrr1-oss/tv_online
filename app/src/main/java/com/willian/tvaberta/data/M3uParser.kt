package com.willian.tvaberta.data

/**
 * Lê listas M3U/M3U8 de canais. Aceita os atributos usuais (#EXTINF com tvg-id, tvg-logo,
 * group-title), os cabeçalhos #EXTVLCOPT (user-agent e referrer) e o estilo Kodi "url|Header=valor".
 * Só entram endereços http/https. Canais repetidos (mesma chave) viram um canal com links de reserva.
 */
object M3uParser {
    const val DEFAULT_UA =
        "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    private val attrRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
    private val qualityRegex = Regex("\\((\\d{3,4}[pi])\\)")
    private val tagRegex = Regex("\\[[^\\]]*\\]")
    private val spaceRegex = Regex("\\s+")

    fun parse(text: String, sourceId: String, keyPrefix: String = ""): List<Channel> {
        val out = LinkedHashMap<String, Channel>()
        parseLines(text.lineSequence(), sourceId, keyPrefix) { c ->
            val existing = out[c.key]
            if (existing != null) out[c.key] = existing.copy(urls = (existing.urls + c.urls).distinct())
            else out[c.key] = c
        }
        return out.values.toList()
    }

    /** Lê linha a linha (sem guardar o arquivo na memória) e entrega cada canal; repetidos chegam repetidos. */
    fun parseLines(lines: Sequence<String>, sourceId: String, keyPrefix: String, emit: (Channel) -> Unit) {
        var pending = false
        var attrs: Map<String, String> = emptyMap()
        var title = ""
        var ua: String? = null
        var ref: String? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("#EXTINF", ignoreCase = true)) {
                pending = true
                ua = null
                ref = null
                val parsedAttrs = HashMap<String, String>()
                var lastAttrEnd = -1
                for (match in attrRegex.findAll(line)) {
                    parsedAttrs[match.groupValues[1].lowercase()] = match.groupValues[2]
                    lastAttrEnd = match.range.last
                }
                attrs = parsedAttrs
                val from = if (lastAttrEnd >= 0) lastAttrEnd + 1 else maxOf(0, line.indexOf(':'))
                val comma = line.indexOf(',', from)
                title = if (comma >= 0) line.substring(comma + 1).trim() else ""
            } else if (line.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val kv = line.substringAfter(':')
                val k = kv.substringBefore('=').trim().lowercase()
                val v = kv.substringAfter('=', "").trim()
                if (k == "http-user-agent") {
                    ua = v
                } else if (k == "http-referrer" || k == "http-referer") {
                    ref = v
                }
            } else if (line.startsWith("#")) {
                continue
            } else if (pending) {
                pending = false
                val url = line.substringBefore('|').trim()
                if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                    continue
                }
                // Algumas listas trazem user-agent e referrer como atributos do #EXTINF.
                var u = ua ?: attrs["http-user-agent"]?.takeIf { it.isNotBlank() }
                var r = ref ?: (attrs["http-referrer"] ?: attrs["http-referer"])?.takeIf { it.isNotBlank() }
                val headerPart = line.substringAfter('|', "")
                if (headerPart.isNotEmpty()) {
                    for (p in headerPart.split('&')) {
                        val hk = p.substringBefore('=').trim().lowercase()
                        val hv = p.substringAfter('=', "").trim()
                        if (hk == "user-agent" && hv.isNotEmpty()) u = hv
                        if ((hk == "referer" || hk == "referrer") && hv.isNotEmpty()) r = hv
                    }
                }

                val groups = (attrs["group-title"] ?: "")
                    .split(';')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (groups.any { it.equals("XXX", ignoreCase = true) }) continue

                val geo = title.contains("[Geo-blocked]", ignoreCase = true)
                val n247 = title.contains("[Not 24/7]", ignoreCase = true)
                val quality = qualityRegex.find(title)?.groupValues?.get(1)
                    ?: attrs["tvg-quality"]?.trim()?.takeIf { it.isNotEmpty() }
                var display = title.replace(tagRegex, "").replace(qualityRegex, "")
                display = display.replace(spaceRegex, " ").trim()
                if (display.isEmpty()) display = (attrs["tvg-name"] ?: "").trim()
                if (display.isEmpty()) display = "Canal"

                val id = (attrs["tvg-id"] ?: "").trim()
                val key = keyPrefix + if (id.isNotEmpty()) "id:$id" else "n:" + display.lowercase()
                val logo = attrs["tvg-logo"]?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) }

                emit(
                    Channel(
                        key = key,
                        name = display,
                        groups = if (groups.isEmpty()) listOf("Undefined") else groups,
                        logo = logo,
                        urls = listOf(url),
                        userAgent = u,
                        referrer = r,
                        quality = quality,
                        geoBlocked = geo,
                        not247 = n247,
                        sourceId = sourceId,
                    )
                )
            }
        }
    }
}
