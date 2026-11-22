package com.willian.tvaberta.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class AppSettings(
    // Aparência
    val theme: Int = 2,               // 0 sistema, 1 claro, 2 escuro
    val accent: Int = 0,
    val pureBlack: Boolean = false,
    val gridLayout: Boolean = true,
    val gridColumns: Int = 2,
    val showLogos: Boolean = true,
    val showBadges: Boolean = true,
    val showCategoryChips: Boolean = true,
    val compactList: Boolean = false,
    // Reprodução
    val resizeMode: Int = 0,          // 0 ajustar, 1 preencher, 2 zoom
    val maxQuality: Int = 0,          // 0 auto, 1 1080p, 2 720p, 3 480p, 4 360p
    val bufferMode: Int = 1,          // 0 baixa latência, 1 equilibrado, 2 estável
    val keepScreenOn: Boolean = true,
    val pipOnLeave: Boolean = false,
    val landscapeOnPlay: Boolean = false,
    val startMuted: Boolean = false,
    val autoFallback: Boolean = true,
    val retries: Int = 2,
    val blockMobile: Boolean = false,
    val overlaySeconds: Int = 6,
    val swipeZap: Boolean = false,
    val reopenLast: Boolean = false,
    // Canais
    val sortMode: Int = 0,            // 0 nome, 1 ordem original, 2 mais assistidos, 3 recentes
    val hideGeoBlocked: Boolean = true,
    val hideNot247: Boolean = false,
    val autoRefreshHours: Int = 24,
    val confirmDelete: Boolean = true,
    // Guia de programação
    val epgEnabled: Boolean = true,
    val showNowOnCards: Boolean = true,
    val epgRefreshHours: Int = 12,
    val epgUrls: String = Epg.DEFAULT_URL,   // um link por linha
    // Internos
    val lastChannelKey: String = "",
    val lastRefresh: Long = 0L,
    val lastEpgRefresh: Long = 0L,
)

object SettingsStore {
    private lateinit var sp: SharedPreferences
    private val _flow = MutableStateFlow(AppSettings())
    val flow: StateFlow<AppSettings> = _flow
    val value: AppSettings get() = _flow.value

    fun init(ctx: Context) {
        sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _flow.value = load()
    }

    fun update(block: (AppSettings) -> AppSettings) {
        _flow.update(block)
        save(_flow.value)
    }

    fun reset() {
        val keepKey = _flow.value.lastChannelKey
        val keepRefresh = _flow.value.lastRefresh
        val keepEpg = _flow.value.lastEpgRefresh
        update { AppSettings(lastChannelKey = keepKey, lastRefresh = keepRefresh, lastEpgRefresh = keepEpg) }
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = sp.getInt("theme", d.theme),
            accent = sp.getInt("accent", d.accent),
            pureBlack = sp.getBoolean("pureBlack", d.pureBlack),
            gridLayout = sp.getBoolean("gridLayout", d.gridLayout),
            gridColumns = sp.getInt("gridColumns", d.gridColumns),
            showLogos = sp.getBoolean("showLogos", d.showLogos),
            showBadges = sp.getBoolean("showBadges", d.showBadges),
            showCategoryChips = sp.getBoolean("showCategoryChips", d.showCategoryChips),
            compactList = sp.getBoolean("compactList", d.compactList),
            resizeMode = sp.getInt("resizeMode", d.resizeMode),
            maxQuality = sp.getInt("maxQuality", d.maxQuality),
            bufferMode = sp.getInt("bufferMode", d.bufferMode),
            keepScreenOn = sp.getBoolean("keepScreenOn", d.keepScreenOn),
            pipOnLeave = sp.getBoolean("pipOnLeave", d.pipOnLeave),
            landscapeOnPlay = sp.getBoolean("landscapeOnPlay", d.landscapeOnPlay),
            startMuted = sp.getBoolean("startMuted", d.startMuted),
            autoFallback = sp.getBoolean("autoFallback", d.autoFallback),
            retries = sp.getInt("retries", d.retries),
            blockMobile = sp.getBoolean("blockMobile", d.blockMobile),
            overlaySeconds = sp.getInt("overlaySeconds", d.overlaySeconds),
            swipeZap = sp.getBoolean("swipeZap", d.swipeZap),
            reopenLast = sp.getBoolean("reopenLast", d.reopenLast),
            sortMode = sp.getInt("sortMode", d.sortMode),
            hideGeoBlocked = sp.getBoolean("hideGeoBlocked", d.hideGeoBlocked),
            hideNot247 = sp.getBoolean("hideNot247", d.hideNot247),
            autoRefreshHours = sp.getInt("autoRefreshHours", d.autoRefreshHours),
            confirmDelete = sp.getBoolean("confirmDelete", d.confirmDelete),
            epgEnabled = sp.getBoolean("epgEnabled", d.epgEnabled),
            showNowOnCards = sp.getBoolean("showNowOnCards", d.showNowOnCards),
            epgRefreshHours = sp.getInt("epgRefreshHours", d.epgRefreshHours),
            epgUrls = (sp.getString("epgUrls", d.epgUrls) ?: d.epgUrls).let {
                if (it.contains(Epg.OLD_DEAD_MARK)) d.epgUrls else it
            },
            lastChannelKey = sp.getString("lastChannelKey", d.lastChannelKey) ?: "",
            lastRefresh = sp.getLong("lastRefresh", d.lastRefresh),
            lastEpgRefresh = sp.getLong("lastEpgRefresh", d.lastEpgRefresh),
        )
    }

    private fun save(s: AppSettings) {
        sp.edit()
            .putInt("theme", s.theme)
            .putInt("accent", s.accent)
            .putBoolean("pureBlack", s.pureBlack)
            .putBoolean("gridLayout", s.gridLayout)
            .putInt("gridColumns", s.gridColumns)
            .putBoolean("showLogos", s.showLogos)
            .putBoolean("showBadges", s.showBadges)
            .putBoolean("showCategoryChips", s.showCategoryChips)
            .putBoolean("compactList", s.compactList)
            .putInt("resizeMode", s.resizeMode)
            .putInt("maxQuality", s.maxQuality)
            .putInt("bufferMode", s.bufferMode)
            .putBoolean("keepScreenOn", s.keepScreenOn)
            .putBoolean("pipOnLeave", s.pipOnLeave)
            .putBoolean("landscapeOnPlay", s.landscapeOnPlay)
            .putBoolean("startMuted", s.startMuted)
            .putBoolean("autoFallback", s.autoFallback)
            .putInt("retries", s.retries)
            .putBoolean("blockMobile", s.blockMobile)
            .putInt("overlaySeconds", s.overlaySeconds)
            .putBoolean("swipeZap", s.swipeZap)
            .putBoolean("reopenLast", s.reopenLast)
            .putInt("sortMode", s.sortMode)
            .putBoolean("hideGeoBlocked", s.hideGeoBlocked)
            .putBoolean("hideNot247", s.hideNot247)
            .putInt("autoRefreshHours", s.autoRefreshHours)
            .putBoolean("confirmDelete", s.confirmDelete)
            .putBoolean("epgEnabled", s.epgEnabled)
            .putBoolean("showNowOnCards", s.showNowOnCards)
            .putInt("epgRefreshHours", s.epgRefreshHours)
            .putString("epgUrls", s.epgUrls)
            .putString("lastChannelKey", s.lastChannelKey)
            .putLong("lastRefresh", s.lastRefresh)
            .putLong("lastEpgRefresh", s.lastEpgRefresh)
            .apply()
    }
}
