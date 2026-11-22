package com.willian.tvaberta.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Locale

/** Quantidade de canais em um grupo (categoria). */
class GroupCount(val grp: String, val n: Int)

/** Filtros comuns às consultas de lista. */
class Flt(
    val hideGeo: Boolean = false,
    val hideN247: Boolean = false,
    val src: String? = null,
    val q: String? = null,
    val kind: Int? = null,
)

/**
 * Catálogo de canais em SQLite: fica no disco, então o app aguenta centenas de milhares de canais
 * sem encher a memória. As escolhas do usuário (favoritos, excluídos...) ficam na tabela "st".
 *
 * Tabelas: ch (canais), cg (canal x grupo), st (escolhas do usuário).
 */
class ChannelDb(ctx: Context) : SQLiteOpenHelper(ctx, "channels.db", null, 2) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE ch(k TEXT PRIMARY KEY, name TEXT NOT NULL, nl TEXT NOT NULL, nn TEXT NOT NULL, " +
                "logo TEXT, urls TEXT NOT NULL, ua TEXT, ref TEXT, q TEXT, geo INTEGER NOT NULL, " +
                "n247 INTEGER NOT NULL, src TEXT NOT NULL, grps TEXT NOT NULL, kind INTEGER NOT NULL, " +
                "idl TEXT NOT NULL, un TEXT NOT NULL, pack TEXT, series TEXT, sn INTEGER NOT NULL DEFAULT 0, ep INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL("CREATE INDEX ch_src ON ch(src)")
        db.execSQL("CREATE INDEX ch_nl ON ch(nl)")
        db.execSQL("CREATE INDEX ch_nn ON ch(nn)")
        db.execSQL("CREATE INDEX ch_un ON ch(un)")
        db.execSQL("CREATE INDEX ch_idl ON ch(idl)")
        db.execSQL("CREATE INDEX ch_pack ON ch(pack)")
        db.execSQL("CREATE INDEX ch_kind ON ch(kind)")
        db.execSQL("CREATE INDEX ch_series ON ch(series)")
        db.execSQL("CREATE TABLE cg(k TEXT NOT NULL, grp TEXT NOT NULL)")
        db.execSQL("CREATE INDEX cg_grp ON cg(grp, k)")
        db.execSQL("CREATE INDEX cg_k ON cg(k)")
        db.execSQL(
            "CREATE TABLE st(k TEXT PRIMARY KEY, d INTEGER NOT NULL, x INTEGER NOT NULL, f INTEGER NOT NULL, " +
                "cg TEXT, cn TEXT, p INTEGER NOT NULL, lp INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ch ADD COLUMN series TEXT")
            db.execSQL("ALTER TABLE ch ADD COLUMN sn INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE ch ADD COLUMN ep INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS ch_kind ON ch(kind)")
            db.execSQL("CREATE INDEX IF NOT EXISTS ch_series ON ch(series)")
            val rows = ArrayList<Array<String>>()
            db.rawQuery("SELECT k,name,grps FROM ch WHERE kind=2", null).use { c ->
                while (c.moveToNext()) rows.add(arrayOf(c.getString(0), c.getString(1), c.getString(2)))
            }
            val up = db.compileStatement("UPDATE ch SET series=?, sn=?, ep=? WHERE k=?")
            for (r in rows) {
                val (ser, sn, ep) = parseSeries(r[1], r[2].split(GSEP).filter { it.isNotEmpty() })
                up.clearBindings()
                up.bindString(1, ser)
                up.bindLong(2, sn.toLong())
                up.bindLong(3, ep.toLong())
                up.bindString(4, r[0])
                up.executeUpdateDelete()
            }
            up.close()
        }
    }

    // ------------------------------------------------------------------ gravação

    inline fun <T> inTx(block: () -> T): T {
        val d = writableDatabase
        d.beginTransaction()
        try {
            val r = block()
            d.setTransactionSuccessful()
            return r
        } finally {
            d.endTransaction()
        }
    }

    private var insStmt: android.database.sqlite.SQLiteStatement? = null
    private var cgStmt: android.database.sqlite.SQLiteStatement? = null

    private fun stmts() {
        val d = writableDatabase
        if (insStmt == null) {
            insStmt = d.compileStatement(
                "INSERT OR IGNORE INTO ch(k,name,nl,nn,logo,urls,ua,ref,q,geo,n247,src,grps,kind,idl,un,pack,series,sn,ep) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
            )
            cgStmt = d.compileStatement("INSERT INTO cg(k,grp) VALUES(?,?)")
        }
    }

    /** Fecha as instruções compiladas (chamar ao terminar uma importação grande). */
    fun releaseStatements() {
        insStmt?.close()
        cgStmt?.close()
        insStmt = null
        cgStmt = null
    }

    /**
     * Grava um canal. Se a chave já existe e merge = true, junta os links do novo como reserva.
     * Devolve true se o canal é novo. Deve ser chamado dentro de inTx.
     */
    fun put(c: Channel, pack: String?, merge: Boolean): Boolean {
        stmts()
        val st = insStmt!!
        st.clearBindings()
        st.bindString(1, c.key)
        st.bindString(2, c.name)
        st.bindString(3, c.name.lowercase(Locale.ROOT))
        st.bindString(4, norm(c.name))
        if (c.logo != null) st.bindString(5, c.logo)
        st.bindString(6, c.urls.joinToString("\n"))
        if (c.userAgent != null) st.bindString(7, c.userAgent)
        if (c.referrer != null) st.bindString(8, c.referrer)
        if (c.quality != null) st.bindString(9, c.quality)
        st.bindLong(10, if (c.geoBlocked) 1 else 0)
        st.bindLong(11, if (c.not247) 1 else 0)
        st.bindString(12, c.sourceId)
        st.bindString(13, c.groups.joinToString(GSEP))
        val kind = classify(c.name, c.groups, c.urls.firstOrNull() ?: "")
        st.bindLong(14, kind.toLong())
        st.bindString(15, idOf(c.key))
        st.bindString(16, normUrl(c.urls.firstOrNull() ?: ""))
        if (pack != null) st.bindString(17, pack)
        if (kind == 2) {
            val (ser, sn, ep) = parseSeries(c.name, c.groups)
            st.bindString(18, ser)
            st.bindLong(19, sn.toLong())
            st.bindLong(20, ep.toLong())
        } else {
            st.bindLong(19, 0)
            st.bindLong(20, 0)
        }
        val rid = st.executeInsert()
        if (rid != -1L) {
            val g = cgStmt!!
            for (grp in c.groups) {
                g.clearBindings()
                g.bindString(1, c.key)
                g.bindString(2, grp)
                g.executeInsert()
            }
            return true
        }
        if (merge) {
            val extra = c.urls.firstOrNull() ?: return false
            writableDatabase.execSQL(
                "UPDATE ch SET urls = urls || char(10) || ? WHERE k = ? AND instr(char(10) || urls || char(10), char(10) || ? || char(10)) = 0",
                arrayOf<Any>(extra, c.key, extra)
            )
        }
        return false
    }

    fun deleteWhere(whereCh: String, args: Array<String>) {
        val d = writableDatabase
        val a = arrayOf<Any>(*args)
        d.execSQL("DELETE FROM cg WHERE k IN (SELECT k FROM ch WHERE $whereCh)", a)
        d.execSQL("DELETE FROM ch WHERE $whereCh", a)
    }

    fun deleteSrc(src: String) = deleteWhere("src = ?", arrayOf(src))

    fun deleteNonManual() = deleteWhere("src <> 'manual'", emptyArray())

    fun deletePack(pack: String) = deleteWhere("pack = ?", arrayOf(pack))

    fun deleteManualAll() = deleteWhere("src = 'manual'", emptyArray())

    fun deleteManualUnpacked() = deleteWhere("src = 'manual' AND pack IS NULL", emptyArray())

    fun clearAll() {
        val d = writableDatabase
        d.execSQL("DELETE FROM cg")
        d.execSQL("DELETE FROM ch")
        d.execSQL("DELETE FROM st")
    }

    fun setPack(keys: List<String>, pack: String) {
        val d = writableDatabase
        d.beginTransaction()
        try {
            val s = d.compileStatement("UPDATE ch SET pack = ? WHERE k = ?")
            for (k in keys) {
                s.clearBindings()
                s.bindString(1, pack)
                s.bindString(2, k)
                s.executeUpdateDelete()
            }
            s.close()
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    // ------------------------------------------------------------------ escolhas do usuário

    fun loadStates(): Map<String, ChannelState> {
        val m = HashMap<String, ChannelState>()
        readableDatabase.rawQuery("SELECT k,d,x,f,cg,cn,p,lp FROM st", null).use { c ->
            while (c.moveToNext()) {
                m[c.getString(0)] = ChannelState(
                    disabled = c.getInt(1) == 1,
                    deleted = c.getInt(2) == 1,
                    favorite = c.getInt(3) == 1,
                    customGroup = if (c.isNull(4)) null else c.getString(4),
                    customName = if (c.isNull(5)) null else c.getString(5),
                    plays = c.getInt(6),
                    lastPlayed = c.getLong(7),
                )
            }
        }
        return m
    }

    /** Grava as escolhas alteradas. Valor null = voltou ao padrão (apaga a linha). */
    fun saveStates(changes: Map<String, ChannelState?>, groupChanged: Set<String>) {
        if (changes.isEmpty()) return
        val d = writableDatabase
        d.beginTransaction()
        try {
            val rep = d.compileStatement("INSERT OR REPLACE INTO st(k,d,x,f,cg,cn,p,lp) VALUES(?,?,?,?,?,?,?,?)")
            val del = d.compileStatement("DELETE FROM st WHERE k = ?")
            for ((k, v) in changes) {
                if (v == null) {
                    del.clearBindings()
                    del.bindString(1, k)
                    del.executeUpdateDelete()
                } else {
                    rep.clearBindings()
                    rep.bindString(1, k)
                    rep.bindLong(2, if (v.disabled) 1 else 0)
                    rep.bindLong(3, if (v.deleted) 1 else 0)
                    rep.bindLong(4, if (v.favorite) 1 else 0)
                    if (v.customGroup != null) rep.bindString(5, v.customGroup)
                    if (v.customName != null) rep.bindString(6, v.customName)
                    rep.bindLong(7, v.plays.toLong())
                    rep.bindLong(8, v.lastPlayed)
                    rep.executeInsert()
                }
            }
            rep.close()
            del.close()
            // Grupo efetivo: quem foi movido fica só no grupo escolhido; quem voltou, nos grupos originais.
            for (k in groupChanged) {
                val v = changes[k]
                val cgName = v?.customGroup
                val groups: List<String> = if (cgName != null) {
                    listOf(cgName)
                } else {
                    var orig = ""
                    d.rawQuery("SELECT grps FROM ch WHERE k=?", arrayOf(k)).use { c ->
                        if (c.moveToFirst()) orig = c.getString(0)
                    }
                    orig.split(GSEP).filter { it.isNotEmpty() }
                }
                d.execSQL("DELETE FROM cg WHERE k=?", arrayOf<Any>(k))
                for (g in groups) d.execSQL("INSERT INTO cg(k,grp) VALUES(?,?)", arrayOf<Any>(k, g))
            }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
    }

    /** Depois de recarregar uma lista, devolve aos canais movidos o grupo que o usuário escolheu. */
    fun reapplyCustomGroups() {
        val d = writableDatabase
        d.execSQL("DELETE FROM cg WHERE k IN (SELECT k FROM st WHERE cg IS NOT NULL)")
        d.execSQL("INSERT INTO cg(k,grp) SELECT k, cg FROM st WHERE cg IS NOT NULL AND k IN (SELECT k FROM ch)")
    }

    fun clearStates() {
        writableDatabase.execSQL("DELETE FROM st")
    }

    // ------------------------------------------------------------------ consultas

    private fun escapeLike(s: String): String =
        s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    private fun likeCond(q: String, args: MutableList<String>): String {
        val p = "%" + escapeLike(q.trim().lowercase(Locale.ROOT)) + "%"
        args.add(p)
        args.add(p)
        return " AND (c.nl LIKE ? ESCAPE '\\' OR LOWER(COALESCE(s.cn,'')) LIKE ? ESCAPE '\\')"
    }

    private fun where(f: Flt, args: MutableList<String>): String {
        val sb = StringBuilder("COALESCE(s.x,0)=0 AND COALESCE(s.d,0)=0")
        if (f.hideGeo) sb.append(" AND c.geo=0")
        if (f.hideN247) sb.append(" AND c.n247=0")
        if (f.src != null) {
            sb.append(" AND c.src=?")
            args.add(f.src)
        }
        if (!f.q.isNullOrBlank()) sb.append(likeCond(f.q, args))
        if (f.kind != null) sb.append(" AND c.kind=").append(f.kind)
        return sb.toString()
    }

    private val cols = "c.k,c.name,c.logo,c.urls,c.ua,c.ref,c.q,c.geo,c.n247,c.src,c.grps"
    private val from = "FROM ch c LEFT JOIN st s ON s.k=c.k"
    private val fromS = "FROM st s CROSS JOIN ch c ON c.k=s.k"

    private fun toChannel(c: Cursor): Channel {
        val groups = c.getString(10).split(GSEP).filter { it.isNotEmpty() }
        return Channel(
            key = c.getString(0),
            name = c.getString(1),
            groups = if (groups.isEmpty()) listOf("Undefined") else groups,
            logo = if (c.isNull(2)) null else c.getString(2),
            urls = c.getString(3).split("\n").filter { it.isNotEmpty() },
            userAgent = if (c.isNull(4)) null else c.getString(4),
            referrer = if (c.isNull(5)) null else c.getString(5),
            quality = if (c.isNull(6)) null else c.getString(6),
            geoBlocked = c.getInt(7) == 1,
            not247 = c.getInt(8) == 1,
            sourceId = c.getString(9),
        )
    }

    private fun list(sql: String, args: List<String>): List<Channel> {
        val out = ArrayList<Channel>()
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(toChannel(c))
        }
        return out
    }

    private fun scalar(sql: String, args: List<String>): Int =
        readableDatabase.rawQuery(sql, args.toTypedArray()).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun count(): Int = scalar("SELECT COUNT(*) FROM ch", emptyList())

    fun totalCount(f: Flt): Int {
        val a = ArrayList<String>()
        val w = where(f, a)
        return scalar("SELECT COUNT(*) $from WHERE $w", a)
    }

    fun origins(f: Flt): List<String> {
        val a = ArrayList<String>()
        val w = where(f, a)
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT DISTINCT c.src $from WHERE $w", a.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun groupCounts(f: Flt): List<GroupCount> {
        val a = ArrayList<String>()
        val w = where(f, a)
        val sql = "SELECT g.grp, COUNT(*) FROM cg g CROSS JOIN ch c ON c.k=g.k LEFT JOIN st s ON s.k=c.k WHERE $w GROUP BY g.grp"
        val out = ArrayList<GroupCount>()
        readableDatabase.rawQuery(sql, a.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(GroupCount(c.getString(0), c.getInt(1)))
        }
        return out
    }

    private fun order(mode: Int): String = when (mode) {
        1 -> "c.rowid"
        2 -> "COALESCE(s.p,0) DESC, c.nl"
        3 -> "COALESCE(s.lp,0) DESC, c.nl"
        else -> "c.nl"
    }

    private val fromG = "FROM cg g CROSS JOIN ch c ON c.k=g.k LEFT JOIN st s ON s.k=c.k"

    fun channelsInGroup(f: Flt, group: String, sortMode: Int, limit: Int, offset: Int): List<Channel> {
        val a = ArrayList<String>()
        val w = where(f, a)
        a.add(group)
        return list("SELECT $cols $fromG WHERE $w AND g.grp=? ORDER BY ${order(sortMode)} LIMIT $limit OFFSET $offset", a)
    }

    fun groupKeys(f: Flt, group: String): List<String> {
        val a = ArrayList<String>()
        val w = where(f, a)
        a.add(group)
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT c.k $fromG WHERE $w AND g.grp=?", a.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    // ------------------------------------------------------------------ séries

    fun seriesCounts(f: Flt): List<GroupCount> {
        val a = ArrayList<String>()
        val w = where(f, a)
        val out = ArrayList<GroupCount>()
        readableDatabase.rawQuery(
            "SELECT c.series, COUNT(*) $from WHERE $w AND c.series IS NOT NULL GROUP BY c.series", a.toTypedArray()
        ).use { c -> while (c.moveToNext()) out.add(GroupCount(c.getString(0), c.getInt(1))) }
        return out
    }

    fun seriesTotal(f: Flt): Int {
        val a = ArrayList<String>()
        val w = where(f, a)
        return scalar("SELECT COUNT(DISTINCT c.series) $from WHERE $w AND c.series IS NOT NULL", a)
    }

    /** Temporadas de uma série: (número da temporada, quantidade de episódios). Temporada 0 = sem número. */
    fun seasonCounts(f: Flt, series: String): List<Pair<Int, Int>> {
        val a = ArrayList<String>()
        val w = where(f, a)
        a.add(series)
        val out = ArrayList<Pair<Int, Int>>()
        readableDatabase.rawQuery(
            "SELECT c.sn, COUNT(*) $from WHERE $w AND c.series=? GROUP BY c.sn ORDER BY c.sn", a.toTypedArray()
        ).use { c -> while (c.moveToNext()) out.add(Pair(c.getInt(0), c.getInt(1))) }
        return out
    }

    fun episodes(f: Flt, series: String, season: Int, limit: Int, offset: Int): List<Channel> {
        val a = ArrayList<String>()
        val w = where(f, a)
        a.add(series)
        return list(
            "SELECT $cols $from WHERE $w AND c.series=? AND c.sn=$season ORDER BY c.ep, c.nl LIMIT $limit OFFSET $offset", a
        )
    }

    /** Chaves dos episódios de uma série (season = null: a série toda). */
    fun seriesKeys(f: Flt, series: String, season: Int?): List<String> {
        val a = ArrayList<String>()
        var w = where(f, a) + " AND c.series=?"
        a.add(series)
        if (season != null) w += " AND c.sn=$season"
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT c.k $from WHERE $w", a.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun srcKeys(f: Flt): List<String> {
        val a = ArrayList<String>()
        val w = where(f, a)
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT c.k $from WHERE $w", a.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    fun favCount(f: Flt): Int {
        val a = ArrayList<String>()
        val w = where(Flt(false, false, f.src, f.q, f.kind), a)
        return scalar("SELECT COUNT(*) $fromS WHERE $w AND s.f=1", a)
    }

    fun favorites(f: Flt, sortMode: Int, limit: Int): List<Channel> {
        val a = ArrayList<String>()
        // Favorito é escolha do usuário: não some por causa dos filtros "ocultar bloqueados por região / fora do ar 24h".
        val w = where(Flt(false, false, f.src, f.q, f.kind), a)
        return list("SELECT $cols $fromS WHERE $w AND s.f=1 ORDER BY ${order(sortMode)} LIMIT $limit", a)
    }

    fun recentCount(f: Flt): Int {
        val a = ArrayList<String>()
        val w = where(f, a)
        return scalar("SELECT COUNT(*) $fromS WHERE $w AND s.lp>0", a)
    }

    fun recents(f: Flt, limit: Int): List<Channel> {
        val a = ArrayList<String>()
        val w = where(f, a)
        return list("SELECT $cols $fromS WHERE $w AND s.lp>0 ORDER BY s.lp DESC LIMIT $limit", a)
    }

    fun byKey(key: String): Channel? =
        list("SELECT $cols $from WHERE c.k = ?", listOf(key)).firstOrNull()

    /** Canais ao vivo (tipo 0) em ordem de nome, um a um, até a função devolver false. */
    fun forEachLive(f: Flt, cb: (Channel) -> Boolean) {
        val a = ArrayList<String>()
        val w = where(f, a)
        readableDatabase.rawQuery("SELECT $cols $from WHERE $w ORDER BY c.nl", a.toTypedArray()).use { c ->
            while (c.moveToNext()) {
                if (!cb(toChannel(c))) break
            }
        }
    }

    // ------------------------------------------------------------------ Gerenciar

    private fun manageFrom(tab: Int) = if (tab == 0) from else fromS

    private fun manageWhere(tab: Int, q: String, args: MutableList<String>): String {
        val base = when (tab) {
            0 -> "COALESCE(s.x,0)=0 AND COALESCE(s.d,0)=0"
            1 -> "s.x=0 AND s.d=1"
            else -> "s.x=1"
        }
        return if (q.isBlank()) base else base + likeCond(q, args)
    }

    fun manageCount(tab: Int, q: String): Int {
        val a = ArrayList<String>()
        val w = manageWhere(tab, q, a)
        return scalar("SELECT COUNT(*) ${manageFrom(tab)} WHERE $w", a)
    }

    fun manageList(tab: Int, q: String, limit: Int): List<Channel> {
        val a = ArrayList<String>()
        val w = manageWhere(tab, q, a)
        return list("SELECT $cols ${manageFrom(tab)} WHERE $w ORDER BY c.nl LIMIT $limit", a)
    }

    fun manageKeys(tab: Int, q: String): List<String> {
        val a = ArrayList<String>()
        val w = manageWhere(tab, q, a)
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT c.k ${manageFrom(tab)} WHERE $w", a.toTypedArray()).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }

    // ------------------------------------------------------------------ pacotes e duplicados

    fun countSrc(src: String): Int =
        scalar("SELECT COUNT(*) $from WHERE c.src=? AND COALESCE(s.x,0)=0", listOf(src))

    fun countPack(pack: String): Int =
        scalar("SELECT COUNT(*) $from WHERE c.pack=? AND COALESCE(s.x,0)=0", listOf(pack))

    fun countManualUnpacked(): Int =
        scalar("SELECT COUNT(*) $from WHERE c.src='manual' AND c.pack IS NULL AND COALESCE(s.x,0)=0", emptyList())

    fun listSrc(src: String, limit: Int): List<Channel> =
        list("SELECT $cols $from WHERE c.src=? AND COALESCE(s.x,0)=0 ORDER BY c.nl LIMIT $limit", listOf(src))

    fun listPack(pack: String, limit: Int): List<Channel> =
        list("SELECT $cols $from WHERE c.pack=? AND COALESCE(s.x,0)=0 ORDER BY c.nl LIMIT $limit", listOf(pack))

    fun listManualUnpacked(limit: Int): List<Channel> =
        list("SELECT $cols $from WHERE c.src='manual' AND c.pack IS NULL AND COALESCE(s.x,0)=0 ORDER BY c.nl LIMIT $limit", emptyList())

    /** Existe canal com o mesmo nome, link ou tvg-id (em qualquer fonte)? */
    fun exists(nn: String, un: String, idl: String): Boolean {
        val sql = if (idl.isEmpty()) "SELECT 1 FROM ch WHERE nn=? OR un=? LIMIT 1"
        else "SELECT 1 FROM ch WHERE nn=? OR un=? OR idl=? LIMIT 1"
        val args = if (idl.isEmpty()) arrayOf(nn, un) else arrayOf(nn, un, idl)
        return readableDatabase.rawQuery(sql, args).use { it.moveToFirst() }
    }

    /** Percorre os canais não excluídos com os dados usados para achar repetidos. */
    fun forEachForDedupe(cb: (key: String, nn: String, un: String, nUrls: Int) -> Unit) {
        readableDatabase.rawQuery(
            "SELECT c.k, c.nn, c.un, c.urls $from WHERE COALESCE(s.x,0)=0", null
        ).use { c ->
            while (c.moveToNext()) {
                val urls = c.getString(3)
                var n = 1
                for (ch in urls) if (ch == '\n') n++
                cb(c.getString(0), c.getString(1), c.getString(2), n)
            }
        }
    }

    fun keysOfPack(pack: String): List<String> = keyList("SELECT k FROM ch WHERE pack=?", arrayOf(pack))

    fun keysOfManualUnpacked(): List<String> =
        keyList("SELECT k FROM ch WHERE src='manual' AND pack IS NULL", emptyArray())

    private fun keyList(sql: String, args: Array<String>): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery(sql, args).use { c -> while (c.moveToNext()) out.add(c.getString(0)) }
        return out
    }

    /** Todos os canais manuais (para o backup), um a um, com o pacote de cada um. */
    fun forEachManual(cb: (Channel, String?) -> Unit) {
        readableDatabase.rawQuery("SELECT $cols, c.pack $from WHERE c.src='manual'", null).use { c ->
            while (c.moveToNext()) cb(toChannel(c), if (c.isNull(11)) null else c.getString(11))
        }
    }

    companion object {
        const val GSEP = "\u0001"

        private val reMarks = Regex("\\p{M}+")
        private val reNonAlnum = Regex("[^a-z0-9]+")

        fun norm(s: String): String =
            java.text.Normalizer.normalize(s.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replace(reMarks, "")
                .replace(reNonAlnum, "")

        fun normUrl(u: String): String =
            u.trim().lowercase(Locale.ROOT).removePrefix("https://").removePrefix("http://").trimEnd('/')

        /** tvg-id em minúsculas (vazio se o canal não tem). */
        fun idOf(key: String): String {
            val k = key.removePrefix("m:")
            return if (k.startsWith("id:")) k.substring(3).lowercase(Locale.ROOT) else ""
        }

        private val seriesRe = Regex("\\b(s\\d{1,2}\\s?[ex]\\d{1,3}|t\\d{1,2}\\s?[ex]?p?\\d{1,3}|\\d{1,2}x\\d{1,3})\\b")

        private val reSeries = Regex(
            "^(.*?)[\\s._\\-–:]*\\b(?:s(\\d{1,2})\\s?[ex]\\s?(\\d{1,3})|t(\\d{1,2})\\s?[ex]\\s?(\\d{1,3})|(\\d{1,2})x(\\d{1,3}))\\b",
            RegexOption.IGNORE_CASE
        )
        private val reEp = Regex(
            "^(.*?)[\\s._\\-–:]*\\b(?:ep|epis[oó]dio|episode|cap[ií]tulo|cap)\\.?\\s?(\\d{1,3})\\b",
            RegexOption.IGNORE_CASE
        )

        /** (nome da série, temporada, episódio) adivinhados pelo nome; temporada 0 = desconhecida. */
        fun parseSeries(name: String, groups: List<String>): Triple<String, Int, Int> {
            val m = reSeries.find(name)
            if (m != null) {
                val g = m.groupValues
                val title = g[1].trim().ifEmpty { name.trim() }
                val sn = (g[2].ifEmpty { g[4].ifEmpty { g[6] } }).toIntOrNull() ?: 0
                val ep = (g[3].ifEmpty { g[5].ifEmpty { g[7] } }).toIntOrNull() ?: 0
                return Triple(title, sn, ep)
            }
            val e = reEp.find(name)
            if (e != null) {
                val title = e.groupValues[1].trim().ifEmpty { name.trim() }
                return Triple(title, 1, e.groupValues[2].toIntOrNull() ?: 0)
            }
            val gen = setOf("serie", "series", "série", "séries", "undefined", "seriado", "seriados")
            val grp = groups.firstOrNull { it.lowercase(Locale.ROOT) !in gen }
            val dash = name.indexOf(" - ")
            val title = when {
                dash > 0 -> name.substring(0, dash).trim()
                grp != null -> grp
                else -> name.trim()
            }
            return Triple(title, 0, 0)
        }

        /** 0 = ao vivo, 1 = filme, 2 = série (palpite pelo nome, grupo e link). */
        fun classify(name: String, groups: List<String>, url: String): Int {
            val g = groups.joinToString(" ").lowercase(Locale.ROOT)
            val n = name.lowercase(Locale.ROOT)
            val u = url.lowercase(Locale.ROOT).substringBefore('?')
            if (g.contains("serie") || g.contains("série") || g.contains("seriado") || g.contains("temporada") ||
                seriesRe.containsMatchIn(n)
            ) return 2
            if (g.contains("filme") || g.contains("movie") || g.contains("cinema") || g.contains("película") ||
                g.contains("pelicula") || g.contains("vod") ||
                u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") || u.endsWith(".mov")
            ) return 1
            return 0
        }
    }
}
