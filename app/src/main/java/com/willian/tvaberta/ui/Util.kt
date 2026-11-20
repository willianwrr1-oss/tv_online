package com.willian.tvaberta.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.willian.tvaberta.data.Epg
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Rational
import com.willian.tvaberta.data.AppSettings
import com.willian.tvaberta.data.ChannelItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun enterPip(a: Activity) {
    if (!a.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    try {
        a.enterPictureInPictureMode(
            PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        )
    } catch (e: Exception) {
        // aparelho sem suporte ou PiP desativado nas configurações do Android
    }
}

private val categoryNames = mapOf(
    "general" to "Geral",
    "news" to "Notícias",
    "sports" to "Esportes",
    "kids" to "Infantil",
    "movies" to "Filmes",
    "music" to "Música",
    "entertainment" to "Entretenimento",
    "documentary" to "Documentários",
    "education" to "Educação",
    "religious" to "Religioso",
    "lifestyle" to "Estilo de vida",
    "comedy" to "Comédia",
    "culture" to "Cultura",
    "animation" to "Animação",
    "business" to "Negócios",
    "science" to "Ciência",
    "series" to "Séries",
    "weather" to "Clima",
    "travel" to "Viagem",
    "outdoor" to "Ao ar livre",
    "family" to "Família",
    "legislative" to "Legislativo",
    "public" to "Público",
    "relax" to "Relax",
    "shop" to "Compras",
    "classic" to "Clássicos",
    "auto" to "Automotivo",
    "cooking" to "Culinária",
    "interactive" to "Interativo",
    "undefined" to "Outros",
)

fun catLabel(raw: String): String = categoryNames[raw.trim().lowercase()] ?: raw

fun categoriesOf(items: List<ChannelItem>): List<String> =
    items.flatMap { it.ch.groups }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { catLabel(it.key) })
        .map { it.key }

fun sortItems(list: List<ChannelItem>, mode: Int): List<ChannelItem> = when (mode) {
    1 -> list
    2 -> list.sortedWith(
        compareByDescending<ChannelItem> { it.st.plays }.thenBy { it.title.lowercase() }
    )
    3 -> list.sortedWith(
        compareByDescending<ChannelItem> { it.st.lastPlayed }.thenBy { it.title.lowercase() }
    )
    else -> list.sortedBy { it.title.lowercase() }
}

/** O canal aparece nas listas principais (Canais e Favoritos)? */
fun isListed(it: ChannelItem, s: AppSettings): Boolean =
    !it.st.deleted && !it.st.disabled &&
        !(s.hideGeoBlocked && it.ch.geoBlocked) &&
        !(s.hideNot247 && it.ch.not247)

/** 75000 -> "1:15"; 3725000 -> "1:02:05". */
fun fmtTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L)) / 1000L
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatDateTime(ms: Long): String {
    if (ms <= 0L) return "nunca"
    return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(ms))
}


/** Nome do programa no ar (ou null se não houver guia). Atualiza quando o guia é recarregado. */
@Composable
fun nowTitle(item: ChannelItem, s: AppSettings): String? {
    if (!s.epgEnabled || !s.showNowOnCards) return null
    val v = Epg.version.collectAsState().value
    if (v < 0) return null
    return Epg.nowNext(item.ch, System.currentTimeMillis()).first?.title
}

/** Milissegundos -> "20:30" no fuso do aparelho. */
fun fmtClock(ms: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
