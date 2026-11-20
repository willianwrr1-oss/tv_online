package com.willian.tvaberta.data

/** Um canal de TV (pode ter mais de um endereço; os extras servem de reserva). */
data class Channel(
    val key: String,
    val name: String,
    val groups: List<String>,
    val logo: String?,
    val urls: List<String>,
    val userAgent: String?,
    val referrer: String?,
    val quality: String?,
    val geoBlocked: Boolean,
    val not247: Boolean,
    val sourceId: String,
)

/** O que o usuário decidiu sobre um canal. Só o que foge do padrão é gravado. */
data class ChannelState(
    val disabled: Boolean = false,
    val deleted: Boolean = false,
    val favorite: Boolean = false,
    val customName: String? = null,
    val plays: Int = 0,
    val lastPlayed: Long = 0L,
)

/** Uma lista M3U de onde os canais são lidos. */
data class Source(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
)

data class ChannelItem(val ch: Channel, val st: ChannelState) {
    val title: String
        get() = st.customName?.takeIf { it.isNotBlank() } ?: ch.name
}

data class RefreshResult(val ok: Int, val fail: Int)

enum class PlayStatus { IDLE, BUFFERING, PLAYING, PAUSED, ERROR }

/** Fontes já cadastradas no app. Suba VERSION para entregar fontes novas a quem já tem o app instalado. */
object DefaultSources {
    const val VERSION = 2

    val list: List<Source> = listOf(
        Source("br", "IPTV-org – Brasil", "https://iptv-org.github.io/iptv/countries/br.m3u", true),
        // Mistura emissoras públicas com marcas de TV paga retransmitidas por terceiros: começa desligada.
        Source(
            "cattv_br",
            "CatTV Playlist – Brasil",
            "https://raw.githubusercontent.com/SamuelSantos-R/cattv-playlist/main/playlist/br.m3u",
            false
        ),
        Source(
            "pd_movies",
            "PublicDomainM3U – Filmes",
            "https://raw.githubusercontent.com/OnlineM3U/publicdomainm3u/main/movies.m3u",
            true
        ),
        Source(
            "pd_series",
            "PublicDomainM3U – Séries",
            "https://raw.githubusercontent.com/OnlineM3U/publicdomainm3u/main/series.m3u",
            true
        ),
    )
}

object Countries {
    const val BASE = "https://iptv-org.github.io/iptv"

    val list: List<Pair<String, String>> = listOf(
        "br" to "Brasil",
        "pt" to "Portugal",
        "us" to "Estados Unidos",
        "ar" to "Argentina",
        "uy" to "Uruguai",
        "py" to "Paraguai",
        "cl" to "Chile",
        "co" to "Colômbia",
        "pe" to "Peru",
        "mx" to "México",
        "ve" to "Venezuela",
        "ec" to "Equador",
        "bo" to "Bolívia",
        "es" to "Espanha",
        "fr" to "França",
        "it" to "Itália",
        "de" to "Alemanha",
        "gb" to "Reino Unido",
        "ca" to "Canadá",
        "ao" to "Angola",
        "mz" to "Moçambique",
        "cv" to "Cabo Verde",
        "jp" to "Japão",
        "kr" to "Coreia do Sul",
        "in" to "Índia",
        "au" to "Austrália",
        "tr" to "Turquia",
        "ru" to "Rússia",
    )

    fun countryUrl(code: String) = "$BASE/countries/$code.m3u"

    const val PORTUGUESE_URL = "$BASE/languages/por.m3u"
}
