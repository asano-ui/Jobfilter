package com.example.jobfilter

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Job(val tag: String, val pickup: String, val drop: String, val price: Double) {
    val isScheduled get() = tag.contains("schedul", true)
}

object Parser {
    // Tajuk: "[Delivery] Balakong > Taman Anggun Rawang"
    private val titleRe = Regex("""^\s*\[([^\]]+)]\s*(.+?)\s*>\s*(.+?)\s*$""")
    // Teks: "Immediate RM91.20 Hurry up!"
    private val rmRe = Regex("""RM\s*([0-9][0-9,]*(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String?): Job? {
        val m = titleRe.find(title ?: return null) ?: return null
        val rm = rmRe.find(text ?: "") ?: rmRe.find(title) ?: return null
        val price = rm.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        return Job(m.groupValues[1].trim(), m.groupValues[2].trim(), m.groupValues[3].trim(), price)
    }
}

/** Import senarai nama tempat dari CSV (contoh: notifikasi.csv) */
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

    /** return (senarai pickup, senarai drop), disusun ikut paling kerap muncul */
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
        if (pi >= 0 && di >= 0) {                       // ada lajur pickup & drop
            for (r in rows.drop(1)) { r.getOrNull(pi)?.let { add(pc, it) }; r.getOrNull(di)?.let { add(dc, it) } }
        } else {                                        // cari "A > B" dalam mana-mana sel
            for (r in rows) for (c in r) cellRe.find(c)?.let { add(pc, it.groupValues[1]); add(dc, it.groupValues[2]) }
        }
        if (pc.isEmpty() && dc.isEmpty()) {             // senarai biasa: satu nama setiap baris
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

    // senarai dari CSV + yang dipilih (kosong = benarkan semua)
    var pickupNames: List<String> get() = getList("pNames"); set(v) = setList("pNames", v)
    var dropNames: List<String> get() = getList("dNames"); set(v) = setList("dNames", v)
    var selPickup: Set<String> get() = getList("pSel").toSet(); set(v) = setList("pSel", v)
    var selDrop: Set<String> get() = getList("dSel").toSet(); set(v) = setList("dSel", v)

    fun addLog(job: Job, reason: String?) {
        val arr = JSONArray(sp.getString("log", "[]"))
        val o = JSONObject().put("t", System.currentTimeMillis()).put("tag", job.tag)
            .put("p", job.pickup).put("d", job.drop).put("rm", job.price).put("r", reason ?: "")
        val out = JSONArray().put(o)
        for (i in 0 until minOf(arr.length(), 99)) out.put(arr.get(i))
        sp.edit().putString("log", out.toString()).apply()
    }
    fun logs(): JSONArray = JSONArray(sp.getString("log", "[]"))
    fun clearLog() = sp.edit().remove("log").apply()
}

object JobFilter {
    /** null = lulus; selain itu = sebab diabaikan */
    fun check(j: Job, p: Prefs): String? {
        if (j.isScheduled && !p.allowScheduled) return "Scheduled diabaikan"
        if (!j.isScheduled && !p.allowDelivery) return "Delivery diabaikan"
        if (j.price < p.minRm) return "RM bawah minimum"
        if (p.maxRm > 0f && j.price > p.maxRm) return "RM atas maksimum"
        val sp = p.selPickup
        if (sp.isNotEmpty() && sp.none { j.pickup.contains(it, true) }) return "Pickup tak dipilih"
        val sd = p.selDrop
        if (sd.isNotEmpty() && sd.none { j.drop.contains(it, true) }) return "Drop tak dipilih"
        return null
    }
}
