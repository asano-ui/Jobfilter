package com.example.jobfilter

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.location.Geocoder
import android.location.Location
import java.util.Locale
import org.json.JSONArray

data class Job(
    val tag: String,
    val pickup: String,
    val drop: String,
    val base: Double,
    val bonus: Double,
    val scheduled: Boolean
) {
    val price: Double get() = base + bonus
}

object Parser {
    // Tajuk: "[Delivery] Balakong > Taman Anggun Rawang" (tag boleh tiada)
    private val titleRe = Regex("""^\s*(?:\[([^\]]+)]\s*)?(.+?)\s*>\s*(.+?)\s*$""")
    // Teks: "Immediate RM43.20 + RM6.00 Hurry up!" / "Scheduled Order RM77.20 Hurry up!"
    private val rmRe = Regex("""RM\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)
    private val spaceRe = Regex("""\s+""")

    private fun clean(s: String) = s.replace('\u00A0', ' ').replace(spaceRe, " ").trim()

    fun parse(title: String?, text: String?): Job? {
        val t = clean(title ?: return null)
        val body = clean(text ?: "")
        val m = titleRe.find(t) ?: return null
        val amounts = rmRe.findAll(body).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.toList()
        if (amounts.isEmpty()) return null
        val tag = m.groupValues[1].ifBlank { "Delivery" }
        val scheduled = tag.contains("schedul", true) || body.contains("schedul", true)
        return Job(tag, clean(m.groupValues[2]), clean(m.groupValues[3]), amounts[0], amounts.drop(1).sum(), scheduled)
    }
}

/** Import senarai nama tempat dari CSV (contoh: notifikasi.csv atau nama_tempat.csv) */
object CsvImport {
    private val cellRe = Regex("""^\s*(?:\[[^\]]+]\s*)?(.+?)\s*>\s*(.+?)\s*$""")

    private fun split(line: String, d: Char): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var q = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && q && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> q = !q
                c == d && !q -> { out.add(sb.toString().trim()); sb.setLength(0) }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString().trim())
        return out
    }

    fun parse(lines: List<String>): Pair<List<String>, List<String>> {
        val ls = lines.filter { it.isNotBlank() }
        if (ls.isEmpty()) return Pair(emptyList(), emptyList())
        val d = if (ls[0].count { it == ';' } > ls[0].count { it == ',' }) ';' else ','
        val rows = ls.map { split(it, d) }
        val pc = HashMap<String, Int>(); val dc = HashMap<String, Int>()
        fun add(m: HashMap<String, Int>, s: String) { if (s.isNotBlank()) m[s.trim()] = (m[s.trim()] ?: 0) + 1 }

        val h = rows[0].map { it.lowercase() }
        val pi = h.indexOfFirst { it.contains("pick") }
        val di = h.indexOfFirst { it.contains("drop") }
        if (pi >= 0 && di >= 0) {
            for (r in rows.drop(1)) { r.getOrNull(pi)?.let { add(pc, it) }; r.getOrNull(di)?.let { add(dc, it) } }
        } else {
            for (r in rows) for (c in r) cellRe.find(c)?.let { add(pc, it.groupValues[1]); add(dc, it.groupValues[2]) }
        }
        if (pc.isEmpty() && dc.isEmpty()) {
            for (r in rows) r.firstOrNull()?.let { add(pc, it); add(dc, it) }
        }
        fun sorted(m: HashMap<String, Int>) = m.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }
        return Pair(sorted(pc), sorted(dc))
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private fun getList(k: String): List<String> { val a = JSONArray(sp.getString(k, "[]")); return List(a.length()) { a.getString(it) } }
    private fun setList(k: String, l: Collection<String>) = sp.edit().putString(k, JSONArray(l).toString()).apply()

    var enabled get() = sp.getBoolean("enabled", true); set(v) = sp.edit().putBoolean("enabled", v).apply()
    var allowDelivery get() = sp.getBoolean("delivery", true); set(v) = sp.edit().putBoolean("delivery", v).apply()
    var allowScheduled get() = sp.getBoolean("scheduled", true); set(v) = sp.edit().putBoolean("scheduled", v).apply()
    var minRm get() = sp.getFloat("minRm", 0f); set(v) = sp.edit().putFloat("minRm", v).apply()
    var maxRm get() = sp.getFloat("maxRm", 0f); set(v) = sp.edit().putFloat("maxRm", v).apply() // 0 = tiada had
    var speak get() = sp.getBoolean("speak", true); set(v) = sp.edit().putBoolean("speak", v).apply()
    var notify get() = sp.getBoolean("notify", true); set(v) = sp.edit().putBoolean("notify", v).apply()
    var premiumOn get() = sp.getBoolean("premOn", false); set(v) = sp.edit().putBoolean("premOn", v).apply()
    var premPerKm get() = sp.getFloat("premKm", 10f); set(v) = sp.edit().putFloat("premKm", v).apply() // RM per km, 0 = tak guna
    var premAbs get() = sp.getFloat("premAbs", 0f); set(v) = sp.edit().putFloat("premAbs", v).apply()  // RM mutlak, 0 = tak guna
    var roadFactor get() = sp.getFloat("roadF", 1.3f); set(v) = sp.edit().putFloat("roadF", v).apply()
    var speechRate get() = sp.getFloat("rate", 1.0f); set(v) = sp.edit().putFloat("rate", v).apply() // 0.5x - 2.5x
    var favBypass get() = sp.getBoolean("favBypass", false); set(v) = sp.edit().putBoolean("favBypass", v).apply()

    var pickupNames: List<String> get() = getList("pNames"); set(v) = setList("pNames", v)
    var dropNames: List<String> get() = getList("dNames"); set(v) = setList("dNames", v)
    var selPickup: Set<String> get() = getList("pSel").toSet(); set(v) = setList("pSel", v)
    var selDrop: Set<String> get() = getList("dSel").toSet(); set(v) = setList("dSel", v)
    var ignPickup: Set<String> get() = getList("pIgn").toSet(); set(v) = setList("pIgn", v)
    var ignDrop: Set<String> get() = getList("dIgn").toSet(); set(v) = setList("dIgn", v)
    var favPickup: Set<String> get() = getList("pFav").toSet(); set(v) = setList("pFav", v)
    var favDrop: Set<String> get() = getList("dFav").toSet(); set(v) = setList("dFav", v)
}

data class LogRow(
    val t: Long, val tag: String, val p: String, val d: String,
    val rm: Double, val bonus: Double, val sched: Boolean,
    val ok: Boolean, val fav: Boolean, val reason: String,
    val km: Double, val prem: Boolean
)

/** Log disimpan dalam SQLite: lulus dan diabaikan dihad asing-asing (3000 setiap satu), jadi job lulus tak tenggelam. */
class LogDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "log.db", null, 2) {
    companion object {
        @Volatile private var inst: LogDb? = null
        fun get(ctx: Context): LogDb = inst ?: synchronized(this) { inst ?: LogDb(ctx).also { inst = it } }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE log(id INTEGER PRIMARY KEY AUTOINCREMENT, t INTEGER, tag TEXT, p TEXT, d TEXT, rm REAL, bonus REAL, sched INTEGER, ok INTEGER, fav INTEGER, reason TEXT, km REAL DEFAULT 0, prem INTEGER DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_ok ON log(ok, id)")
        db.execSQL("CREATE TABLE geo(name TEXT PRIMARY KEY, ok INTEGER, lat REAL, lng REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {
        if (o < 2) {
            db.execSQL("ALTER TABLE log ADD COLUMN km REAL DEFAULT 0")
            db.execSQL("ALTER TABLE log ADD COLUMN prem INTEGER DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS geo(name TEXT PRIMARY KEY, ok INTEGER, lat REAL, lng REAL)")
        }
    }

    /** [status(1=ok,0=gagal), lat, lng]; null = belum pernah dicari */
    fun geoGet(key: String): DoubleArray? =
        readableDatabase.rawQuery("SELECT ok,lat,lng FROM geo WHERE name=?", arrayOf(key)).use {
            if (it.moveToFirst()) doubleArrayOf(it.getInt(0).toDouble(), it.getDouble(1), it.getDouble(2)) else null
        }

    fun geoPut(key: String, ok: Boolean, lat: Double, lng: Double) {
        val cv = ContentValues().apply { put("name", key); put("ok", if (ok) 1 else 0); put("lat", lat); put("lng", lng) }
        writableDatabase.insertWithOnConflict("geo", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun geoCounts(): Pair<Int, Int> {
        fun c(w: String) = readableDatabase.rawQuery("SELECT COUNT(*) FROM geo WHERE ok=$w", null).use { it.moveToFirst(); it.getInt(0) }
        return Pair(c("1"), c("0"))
    }

    fun add(j: Job, reason: String?, fav: Boolean, km: Double?, prem: Boolean) {
        val cv = ContentValues().apply {
            put("t", System.currentTimeMillis()); put("tag", j.tag); put("p", j.pickup); put("d", j.drop)
            put("rm", j.price); put("bonus", j.bonus); put("sched", if (j.scheduled) 1 else 0)
            put("ok", if (reason == null) 1 else 0); put("fav", if (fav) 1 else 0); put("reason", reason ?: "")
            put("km", km ?: 0.0); put("prem", if (prem) 1 else 0)
        }
        val id = writableDatabase.insert("log", null, cv)
        if (id % 200 == 0L) for (ok in 0..1) {
            writableDatabase.execSQL("DELETE FROM log WHERE ok=$ok AND id NOT IN (SELECT id FROM log WHERE ok=$ok ORDER BY id DESC LIMIT 3000)")
        }
    }

    fun query(onlyOk: Boolean, limit: Int): List<LogRow> {
        val sql = "SELECT t,tag,p,d,rm,bonus,sched,ok,fav,reason,km,prem FROM log " +
            (if (onlyOk) "WHERE ok=1 " else "") + "ORDER BY id DESC LIMIT $limit"
        val out = ArrayList<LogRow>()
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) out.add(
                LogRow(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getDouble(4), c.getDouble(5),
                    c.getInt(6) == 1, c.getInt(7) == 1, c.getInt(8) == 1, c.getString(9), c.getDouble(10), c.getInt(11) == 1)
            )
        }
        return out
    }

    fun count(onlyOk: Boolean): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM log" + (if (onlyOk) " WHERE ok=1" else ""), null).use {
            it.moveToFirst(); it.getInt(0)
        }

    /** [lulus, diabaikan, mahal-lulus] sejak masa tertentu */
    fun stats(since: Long): IntArray =
        readableDatabase.rawQuery(
            "SELECT COALESCE(SUM(ok=1),0), COALESCE(SUM(ok=0),0), COALESCE(SUM(ok=1 AND prem=1),0) FROM log WHERE t>=?",
            arrayOf(since.toString())
        ).use { it.moveToFirst(); intArrayOf(it.getInt(0), it.getInt(1), it.getInt(2)) }

    fun clear() { writableDatabase.execSQL("DELETE FROM log") }
}

object JobFilter {
    private fun hit(set: Set<String>, s: String) = set.any { s.contains(it, true) }

    fun isFav(j: Job, p: Prefs) = hit(p.favPickup, j.pickup) || hit(p.favDrop, j.drop)

    /** null = lulus; selain itu = sebab diabaikan */
    fun check(j: Job, p: Prefs, premium: Boolean = false): String? {
        if (j.scheduled && !p.allowScheduled) return "Scheduled diabaikan"
        if (!j.scheduled && !p.allowDelivery) return "Delivery diabaikan"
        if (hit(p.ignPickup, j.pickup)) return "Pickup dalam senarai abaikan"
        if (hit(p.ignDrop, j.drop)) return "Drop dalam senarai abaikan"
        if (premium) return null   // job mahal: lepas julat RM dan senarai dipilih
        if (p.favBypass && isFav(j, p)) return null
        if (j.price < p.minRm) return "RM bawah minimum"
        if (p.maxRm > 0f && j.price > p.maxRm) return "RM atas maksimum"
        val sp = p.selPickup
        if (sp.isNotEmpty() && !hit(sp, j.pickup)) return "Pickup tak dipilih"
        val sd = p.selDrop
        if (sd.isNotEmpty() && !hit(sd, j.drop)) return "Drop tak dipilih"
        return null
    }
}

/** Betulkan sebutan TTS secara automatik: "ch" -> "c" (ejaan Melayu), contoh Cheras -> Ceras. */
object Speech {
    private val chRe = Regex("ch", RegexOption.IGNORE_CASE)

    fun fix(text: String): String =
        text.replace(chRe) { m -> if (m.value[0].isUpperCase()) "C" else "c" }
}

/** Anggaran jarak pickup-drop: Geocoder (nama tempat -> koordinat, disimpan dalam cache) x faktor jalan. */
object Geo {
    @Suppress("DEPRECATION")
    fun lookup(ctx: Context, name: String): DoubleArray? {
        val db = LogDb.get(ctx)
        val key = name.trim().lowercase()
        db.geoGet(key)?.let { return if (it[0] == 1.0) doubleArrayOf(it[1], it[2]) else null }
        if (!Geocoder.isPresent()) return null
        return try {
            val r = Geocoder(ctx, Locale("ms", "MY")).getFromLocationName("$name, Malaysia", 1, 0.8, 99.5, 7.5, 119.5)
            if (r.isNullOrEmpty()) { db.geoPut(key, false, 0.0, 0.0); null }
            else { db.geoPut(key, true, r[0].latitude, r[0].longitude); doubleArrayOf(r[0].latitude, r[0].longitude) }
        } catch (e: Exception) { null } // masalah rangkaian: jangan simpan sebagai gagal
    }

    fun distanceKm(ctx: Context, a: String, b: String, factor: Double): Double? {
        val p = lookup(ctx, a) ?: return null
        val q = lookup(ctx, b) ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(p[0], p[1], q[0], q[1], out)
        return out[0] / 1000.0 * factor
    }
}
