package com.willian.tvaberta.ui

import com.willian.tvaberta.data.AppSettings
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.Repo
import java.util.Locale

/** Um grupo da lista de TV ao vivo: uma categoria, ou os atalhos Favoritos e Recentes. */
class LiveGroup(val id: String, val label: String, val items: List<ChannelItem>, val isCategory: Boolean)

/** Tudo que a tela de TV ao vivo mostra, calculado fora da thread da tela. */
class LiveModel(
    val groups: List<LiveGroup>,
    val total: Int,
    val origins: List<String>,
    val org: String?,
    val loaded: Boolean,
) {
    companion object {
        val EMPTY = LiveModel(emptyList(), 0, emptyList(), null, false)
    }
}

/** Ordena por título calculando a chave em minúsculas uma vez só por item (não a cada comparação). */
fun sortByTitle(list: List<ChannelItem>): List<ChannelItem> {
    if (list.size < 2) return list
    val keyed = list.map { Pair(it.title.lowercase(Locale.ROOT), it) }
    return keyed.sortedWith(compareBy { it.first }).map { it.second }
}

private fun sortFast(list: List<ChannelItem>, mode: Int): List<ChannelItem> {
    if (list.size < 2) return list
    return when (mode) {
        1 -> list
        2 -> list.map { Pair(it.title.lowercase(Locale.ROOT), it) }
            .sortedWith(compareByDescending<Pair<String, ChannelItem>> { it.second.st.plays }.thenBy { it.first })
            .map { it.second }
        3 -> list.map { Pair(it.title.lowercase(Locale.ROOT), it) }
            .sortedWith(compareByDescending<Pair<String, ChannelItem>> { it.second.st.lastPlayed }.thenBy { it.first })
            .map { it.second }
        else -> sortByTitle(list)
    }
}

/** Filtra, agrupa e ordena em uma passada só: pensado para dezenas de milhares de canais. */
fun buildLiveModel(all: List<ChannelItem>, s: AppSettings, query: String, origin: String?): LiveModel {
    val listed = all.filter { isListed(it, s) }
    val origins = listed.map { it.ch.sourceId }.distinct().sortedBy { Repo.originName(it).lowercase() }
    val org = origin?.takeIf { it in origins }
    val q = query.trim()
    val base = listed.filter { item ->
        (org == null || item.ch.sourceId == org) && (q.isEmpty() || item.title.contains(q, ignoreCase = true))
    }
    // Ordena a lista inteira uma vez; cada categoria já recebe os canais na ordem certa.
    val sorted = sortFast(base, s.sortMode)
    val byCat = HashMap<String, MutableList<ChannelItem>>()
    for (item in sorted) {
        for (g in item.ch.groups) {
            byCat.getOrPut(g) { ArrayList() }.add(item)
        }
    }
    val groups = ArrayList<LiveGroup>()
    val favs = sorted.filter { it.st.favorite }
    if (favs.isNotEmpty()) groups.add(LiveGroup("fav", "Favoritos", favs, false))
    val recents = base.filter { it.st.lastPlayed > 0L }.sortedByDescending { it.st.lastPlayed }.take(20)
    if (recents.isNotEmpty()) groups.add(LiveGroup("rec", "Assistidos recentemente", recents, false))
    val cats = byCat.keys.map { Pair(catLabel(it).lowercase(Locale.ROOT), it) }.sortedBy { it.first }
    for ((_, c) in cats) {
        groups.add(LiveGroup("c:" + c, catLabel(c), byCat[c] ?: emptyList(), true))
    }
    return LiveModel(groups, base.size, origins, org, true)
}
