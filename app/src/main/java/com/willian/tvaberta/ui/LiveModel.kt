package com.willian.tvaberta.ui

import com.willian.tvaberta.data.AppSettings
import com.willian.tvaberta.data.ChannelItem
import com.willian.tvaberta.data.GroupCount
import com.willian.tvaberta.data.Repo
import java.util.Locale

/**
 * Um grupo da lista de TV ao vivo: uma categoria, ou os atalhos Favoritos e Recentes.
 * [count] é o total do grupo; [items] só vem preenchido no grupo aberto (aos poucos, em páginas).
 */
class LiveGroup(
    val id: String,
    val label: String,
    val count: Int,
    val items: List<ChannelItem>,
    val isCategory: Boolean,
    val hasMore: Boolean = false,
    val children: List<LiveGroup> = emptyList(),
)

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

/** Contagens do cabeçalho da lista (categorias, total, listas de origem), vindas do banco. */
class LiveHead(val cats: List<GroupCount>, val total: Int, val origins: List<String>, val org: String?, val kind: Int?)

fun buildLiveHead(s: AppSettings, query: String, origin: String?, kind: Int?): LiveHead {
    val db = Repo.db
    val origins = db.origins(Repo.flt(s, kind = kind)).sortedBy { Repo.originName(it).lowercase(Locale.ROOT) }
    val org = origin?.takeIf { it in origins }
    val f = Repo.flt(s, org, query.trim().ifEmpty { null }, kind)
    return if (kind == 2) {
        val cats = db.seriesCounts(f).sortedBy { it.grp.lowercase(Locale.ROOT) }
        LiveHead(cats, db.seriesTotal(f), origins, org, kind)
    } else {
        val cats = db.groupCounts(f).sortedBy { catLabel(it.grp).lowercase(Locale.ROOT) }
        LiveHead(cats, db.totalCount(f), origins, org, kind)
    }
}

/** Nome da temporada para exibir. */
fun seasonLabel(n: Int): String = if (n <= 0) "Episódios" else "Temporada $n"

/** Ordena por título calculando a chave em minúsculas uma vez só por item (não a cada comparação). */
fun sortByTitle(list: List<ChannelItem>): List<ChannelItem> {
    if (list.size < 2) return list
    val keyed = list.map { Pair(it.title.lowercase(Locale.ROOT), it) }
    return keyed.sortedWith(compareBy { it.first }).map { it.second }
}
