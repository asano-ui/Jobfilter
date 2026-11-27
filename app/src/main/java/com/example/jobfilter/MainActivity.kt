package com.example.jobfilter

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var swEnabled: Switch
    private lateinit var cbDelivery: CheckBox
    private lateinit var cbScheduled: CheckBox
    private lateinit var swSpeak: Switch
    private lateinit var swNotify: Switch
    private lateinit var swFavBypass: Switch
    private lateinit var etMin: EditText
    private lateinit var etMax: EditText
    private lateinit var btnPickup: Button
    private lateinit var btnDrop: Button
    private lateinit var btnIgnPickup: Button
    private lateinit var btnIgnDrop: Button
    private lateinit var tvImport: TextView
    private lateinit var sbMin: SeekBar
    private lateinit var sbMax: SeekBar
    private lateinit var tvRange: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLogHead: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnLogOk: Button
    private lateinit var btnLogAll: Button
    private var syncing = false
    private var logOnlyOk = true
    private var logLimit = 50
    private lateinit var swPremium: Switch
    private lateinit var etPerKm: EditText
    private lateinit var etPremAbs: EditText
    private lateinit var etFactor: EditText
    private lateinit var btnGeo: Button
    private lateinit var tvGeo: TextView
    private lateinit var sbRate: SeekBar
    private lateinit var tvRate: TextView
    private var geoRunning = false
    private var geoStop = false

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun label(t: String) = TextView(this).apply { text = t; textSize = 15f; setPadding(0, dp(14), 0, dp(2)) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(24)) }
        setContentView(ScrollView(this).apply { addView(root) })

        tvStatus = TextView(this).apply { textSize = 14f }
        root.addView(tvStatus)
        root.addView(Button(this).apply {
            text = "Buka Notification Access"
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        })

        swEnabled = Switch(this).apply { text = "App aktif"; isChecked = prefs.enabled }
        root.addView(swEnabled)

        root.addView(label("Jenis order"))
        cbDelivery = CheckBox(this).apply { text = "Delivery (Immediate)"; isChecked = prefs.allowDelivery }
        cbScheduled = CheckBox(this).apply { text = "Scheduled"; isChecked = prefs.allowScheduled }
        root.addView(cbDelivery); root.addView(cbScheduled)

        root.addView(label("Julat nilai RM (termasuk bonus)"))
        tvRange = TextView(this).apply { textSize = 18f; setPadding(0, 0, 0, dp(4)) }
        root.addView(tvRange)
        sbMin = SeekBar(this).apply { max = 100 }   // 0..1000, langkah RM10
        sbMax = SeekBar(this).apply { max = 100 }   // hujung kanan = tiada had
        root.addView(TextView(this).apply { text = "Minimum"; textSize = 12f }); root.addView(sbMin)
        root.addView(TextView(this).apply { text = "Maksimum (hujung kanan = tiada had)"; textSize = 12f }); root.addView(sbMax)
        root.addView(TextView(this).apply { text = "Atau taip nilai tepat:"; textSize = 12f; setPadding(0, dp(8), 0, 0) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        etMin = numField("Minimum", prefs.minRm); etMax = numField("Maksimum", prefs.maxRm)
        row.addView(etMin, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(etMax, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        sbMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                if (user) { if (sbMax.progress < 100 && p > sbMax.progress) sbMin.progress = sbMax.progress; updateRangeUi(true) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        sbMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) {
                if (user) {
                    if (p < 1) sbMax.progress = 1
                    if (sbMax.progress < sbMin.progress) sbMax.progress = sbMin.progress
                    updateRangeUi(true)
                }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val tw = object : TextWatcher {
            override fun afterTextChanged(e: Editable?) { updateRangeUi(false) }
            override fun beforeTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(x: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        etMin.addTextChangedListener(tw); etMax.addTextChangedListener(tw)

        root.addView(label("Nama tempat (dari CSV)"))
        root.addView(Button(this).apply {
            text = "Import CSV"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"
                }, 42)
            }
        })
        tvImport = TextView(this).apply { textSize = 13f }
        root.addView(tvImport)
        btnPickup = Button(this).apply {
            setOnClickListener {
                pickDialog("Pilih Pickup", prefs.pickupNames, prefs.selPickup, prefs.favPickup) { s, f ->
                    prefs.selPickup = s; prefs.favPickup = f; refresh()
                }
            }
        }
        btnDrop = Button(this).apply {
            setOnClickListener {
                pickDialog("Pilih Drop", prefs.dropNames, prefs.selDrop, prefs.favDrop) { s, f ->
                    prefs.selDrop = s; prefs.favDrop = f; refresh()
                }
            }
        }
        btnIgnPickup = Button(this).apply {
            setOnClickListener {
                pickDialog("Abaikan Pickup", prefs.pickupNames, prefs.ignPickup, null) { s, _ -> prefs.ignPickup = s; refresh() }
            }
        }
        btnIgnDrop = Button(this).apply {
            setOnClickListener {
                pickDialog("Abaikan Drop", prefs.dropNames, prefs.ignDrop, null) { s, _ -> prefs.ignDrop = s; refresh() }
            }
        }
        root.addView(btnPickup); root.addView(btnDrop)
        root.addView(TextView(this).apply {
            text = "Tiada yang dipilih = semua tempat dibenarkan. Dalam senarai pilih, tekan lama satu nama untuk jadikan ★ favorit."
            textSize = 12f
        })
        root.addView(btnIgnPickup); root.addView(btnIgnDrop)
        swFavBypass = Switch(this).apply { text = "Favorit sentiasa lulus (abaikan julat RM dan senarai dipilih)"; isChecked = prefs.favBypass }
        root.addView(swFavBypass)

        swSpeak = Switch(this).apply { text = "Baca guna suara"; isChecked = prefs.speak }
        swNotify = Switch(this).apply { text = "Notifikasi sendiri (job lulus)"; isChecked = prefs.notify }
        root.addView(label("Job mahal (sentiasa baca)"))
        swPremium = Switch(this).apply { text = "Sentiasa baca job mahal"; isChecked = prefs.premiumOn }
        root.addView(swPremium)
        root.addView(TextView(this).apply {
            text = "Mahal = RM per km mencapai had (contoh anggaran 10 km bayar RM100 = RM10/km), atau RM melebihi had mutlak. " +
                "Job mahal lepas julat RM dan senarai dipilih, tapi bukan senarai abaikan atau jenis order. " +
                "Jarak ialah anggaran: garis lurus antara koordinat tempat x faktor jalan."
            textSize = 12f
        })
        root.addView(TextView(this).apply { text = "RM per km  |  RM mutlak (0 = tak guna)  |  Faktor jalan"; textSize = 12f; setPadding(0, dp(8), 0, 0) })
        val prow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        etPerKm = numField("RM/km", prefs.premPerKm); etPremAbs = numField("RM", prefs.premAbs); etFactor = numField("1.3", prefs.roadFactor)
        prow.addView(etPerKm, LinearLayout.LayoutParams(0, -2, 1f)); prow.addView(etPremAbs, LinearLayout.LayoutParams(0, -2, 1f)); prow.addView(etFactor, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(prow)
        tvGeo = TextView(this).apply { textSize = 12f }
        root.addView(tvGeo)
        btnGeo = Button(this).apply {
            text = "Muat koordinat tempat"
            setOnClickListener {
                if (geoRunning) { geoStop = true; return@setOnClickListener }
                val names = (prefs.pickupNames + prefs.dropNames).distinct()
                if (names.isEmpty()) { toast("Import CSV dulu"); return@setOnClickListener }
                geoRunning = true; geoStop = false; text = "Henti"
                Thread {
                    var done = 0
                    for (n in names) {
                        if (geoStop) break
                        if (LogDb.get(this@MainActivity).geoGet(n.trim().lowercase()) == null) { Geo.lookup(this@MainActivity, n); Thread.sleep(150) }
                        done++
                        if (done % 5 == 0) runOnUiThread { tvGeo.text = "Memuat koordinat: $done / ${names.size}" }
                    }
                    runOnUiThread { geoRunning = false; text = "Muat koordinat tempat"; refresh() }
                }.start()
            }
        }
        root.addView(btnGeo)

        root.addView(label("")); root.addView(swSpeak); root.addView(swNotify)

        tvRate = TextView(this).apply { textSize = 14f; setPadding(0, dp(10), 0, 0) }
        sbRate = SeekBar(this).apply {
            max = 20   // 0.5x .. 2.5x, langkah 0.1
            progress = Math.round((prefs.speechRate - 0.5f) / 0.1f).coerceIn(0, 20)
        }
        fun rateText() { tvRate.text = "Kelajuan suara: " + String.format(Locale.US, "%.1f", 0.5f + sbRate.progress * 0.1f) + "x" }
        rateText()
        sbRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { rateText() }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(tvRate); root.addView(sbRate)

        root.addView(Button(this).apply { text = "Simpan"; setOnClickListener { save(); toast("Disimpan") } })

        root.addView(label("Log"))
        tvLogHead = TextView(this).apply { textSize = 12f }
        root.addView(tvLogHead)
        val logBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnLogOk = Button(this).apply { setOnClickListener { logOnlyOk = true; logLimit = 50; refresh() } }
        btnLogAll = Button(this).apply { setOnClickListener { logOnlyOk = false; logLimit = 50; refresh() } }
        logBar.addView(btnLogOk, LinearLayout.LayoutParams(0, -2, 1f))
        logBar.addView(btnLogAll, LinearLayout.LayoutParams(0, -2, 1f))
        logBar.addView(Button(this).apply { text = "Muat semula"; setOnClickListener { refresh() } }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(logBar)
        tvLog = TextView(this).apply { textSize = 13f }
        root.addView(tvLog)
        root.addView(Button(this).apply { text = "Muat lagi"; setOnClickListener { logLimit += 50; refresh() } })
        root.addView(Button(this).apply { text = "Kosongkan log"; setOnClickListener { LogDb.get(this@MainActivity).clear(); refresh() } })
        updateRangeUi(false)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (req != 42 || res != RESULT_OK) return
        Thread {
            try {
                val lines = contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readLines() }
                val (p, d) = CsvImport.parse(lines)
                runOnUiThread {
                    if (p.isEmpty() && d.isEmpty()) { toast("Tiada nama tempat dijumpai dalam CSV"); return@runOnUiThread }
                    prefs.pickupNames = p; prefs.dropNames = d
                    prefs.selPickup = prefs.selPickup.filter { it in p }.toSet()
                    prefs.selDrop = prefs.selDrop.filter { it in d }.toSet()
                    prefs.ignPickup = prefs.ignPickup.filter { it in p }.toSet()
                    prefs.ignDrop = prefs.ignDrop.filter { it in d }.toSet()
                    prefs.favPickup = prefs.favPickup.filter { it in p }.toSet()
                    prefs.favDrop = prefs.favDrop.filter { it in d }.toSet()
                    toast("Import: ${p.size} pickup, ${d.size} drop"); refresh()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Gagal baca CSV: ${e.message}") }
            }
        }.start()
    }

    private fun updateRangeUi(fromSeek: Boolean) {
        if (syncing) return
        syncing = true
        if (fromSeek) {
            etMin.setText(if (sbMin.progress == 0) "" else (sbMin.progress * 10).toString())
            etMax.setText(if (sbMax.progress == 100) "" else (sbMax.progress * 10).toString())
        } else {
            val mn = etMin.text.toString().toFloatOrNull() ?: 0f
            val mx = etMax.text.toString().toFloatOrNull() ?: 0f
            sbMin.progress = (mn / 10).toInt().coerceIn(0, 100)
            sbMax.progress = if (mx <= 0f) 100 else (mx / 10).toInt().coerceIn(1, 100)
        }
        val mn = etMin.text.toString().ifBlank { "0" }
        val mx = etMax.text.toString().ifBlank { "" }
        tvRange.text = "RM $mn – " + (if (mx.isEmpty()) "tiada had" else "RM $mx")
        syncing = false
    }

    /** dialog multi-select: carian, fast-scroll, "dipilih saja"; favs != null = boleh tandakan ★ (tekan lama) */
    private fun pickDialog(
        title: String, all: List<String>, selected: Set<String>, favs: Set<String>?,
        onDone: (Set<String>, Set<String>) -> Unit
    ) {
        if (all.isEmpty()) { toast("Import CSV dulu"); return }
        val sel = selected.toMutableSet()
        val fav = (favs ?: emptySet()).toMutableSet()
        var shown = all
        var query = ""
        var onlySel = false
        val tvCount = TextView(this).apply { textSize = 13f; setPadding(0, dp(4), 0, dp(4)) }
        val lv = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE; isFastScrollEnabled = true }
        fun count() { tvCount.text = "Dipilih ${sel.size}" + (if (favs != null) "  •  ★ ${fav.size}" else "") + "  •  Dipaparkan ${shown.size} / ${all.size}" }
        fun bind() {
            val words = query.split(" ").filter { it.isNotBlank() }
            val f = all.filter { n -> (!onlySel || n in sel) && words.all { n.contains(it, true) } }
            shown = if (favs != null) f.sortedByDescending { it in fav } else f
            val labels = shown.map { if (it in fav) "★ $it" else it }
            lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, labels)
            shown.forEachIndexed { i, n -> lv.setItemChecked(i, n in sel) }
            count()
        }
        lv.setOnItemClickListener { _, _, pos, _ ->
            val n = shown[pos]; if (lv.isItemChecked(pos)) sel.add(n) else sel.remove(n); count()
        }
        if (favs != null) lv.setOnItemLongClickListener { _, _, pos, _ ->
            val n = shown[pos]
            if (n in fav) { fav.remove(n); toast("Buang ★: $n") } else { fav.add(n); toast("★ favorit: $n") }
            bind(); true
        }

        val et = EditText(this).apply { hint = "Cari (contoh: taman sri)"; setSingleLine() }
        et.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { query = s.toString(); bind() }
        })
        val cbOnly = CheckBox(this).apply { text = "Papar yang dipilih saja"; setOnCheckedChangeListener { _, c -> onlySel = c; bind() } }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bar.addView(Button(this).apply { text = "Pilih semua dipaparkan"; textSize = 12f; setOnClickListener { sel.addAll(shown); bind() } }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(this).apply { text = "Nyahpilih dipaparkan"; textSize = 12f; setOnClickListener { sel.removeAll(shown.toSet()); bind() } }, LinearLayout.LayoutParams(0, -2, 1f))

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0) }
        box.addView(et); box.addView(tvCount)
        if (favs != null) box.addView(TextView(this).apply { text = "Tekan lama satu nama untuk ★ favorit (naik ke atas)"; textSize = 12f })
        box.addView(cbOnly); box.addView(bar)
        box.addView(lv, LinearLayout.LayoutParams(-1, dp(320)))
        bind()
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("Siap") { _, _ -> onDone(sel, fav) }
            .setNegativeButton("Batal", null).show()
    }

    private fun numField(h: String, v: Float) = EditText(this).apply {
        hint = h; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        if (v > 0f) setText(v.toString().removeSuffix(".0"))
    }

    private fun save() {
        prefs.enabled = swEnabled.isChecked
        prefs.allowDelivery = cbDelivery.isChecked
        prefs.allowScheduled = cbScheduled.isChecked
        prefs.minRm = etMin.text.toString().toFloatOrNull() ?: 0f
        prefs.maxRm = etMax.text.toString().toFloatOrNull() ?: 0f
        prefs.speak = swSpeak.isChecked
        prefs.notify = swNotify.isChecked
        prefs.favBypass = swFavBypass.isChecked
        prefs.premiumOn = swPremium.isChecked
        prefs.premPerKm = etPerKm.text.toString().toFloatOrNull() ?: 0f
        prefs.premAbs = etPremAbs.text.toString().toFloatOrNull() ?: 0f
        prefs.roadFactor = etFactor.text.toString().toFloatOrNull() ?: 1.3f
        prefs.speechRate = 0.5f + sbRate.progress * 0.1f
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun refresh() {
        val on = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        tvStatus.text = if (on) "Notification access: AKTIF ✔" else "Notification access: BELUM — tekan butang bawah"
        tvImport.text = "Senarai: ${prefs.pickupNames.size} pickup, ${prefs.dropNames.size} drop"
        btnPickup.text = "Pilih Pickup (${prefs.selPickup.size} dipilih, ★ ${prefs.favPickup.size})"
        btnDrop.text = "Pilih Drop (${prefs.selDrop.size} dipilih, ★ ${prefs.favDrop.size})"
        btnIgnPickup.text = "Abaikan Pickup (${prefs.ignPickup.size})"
        btnIgnDrop.text = "Abaikan Drop (${prefs.ignDrop.size})"

        val db = LogDb.get(this)
        if (!geoRunning) { val (ok, bad) = db.geoCounts(); tvGeo.text = "Koordinat tempat: $ok berjaya, $bad gagal" }
        btnLogOk.text = if (logOnlyOk) "● Lulus" else "Lulus"
        btnLogAll.text = if (!logOnlyOk) "● Semua" else "Semua"
        tvLogHead.text = "Lulus ${db.count(true)}  •  Semua ${db.count(false)}  (simpan sehingga 3000 setiap jenis)"
        val f = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        for (r in db.query(logOnlyOk, logLimit)) {
            sb.append(if (r.ok) "✔ " else "✘ ").append(if (r.prem) "[MAHAL] " else "").append(if (r.fav) "★ " else "")
                .append(f.format(Date(r.t))).append("  ").append(if (r.sched) "Scheduled" else "Immediate")
                .append("  RM").append(String.format(Locale.US, "%.2f", r.rm))
            if (r.bonus > 0) sb.append(" (bonus ").append(String.format(Locale.US, "%.2f", r.bonus)).append(')')
            if (r.km >= 0.5) sb.append("  ~").append(String.format(Locale.US, "%.1f", r.km)).append(" km, RM")
                .append(String.format(Locale.US, "%.1f", r.rm / r.km)).append("/km")
            sb.append('\n').append(r.p).append(" > ").append(r.d)
            if (!r.ok) sb.append("\n(").append(r.reason).append(')')
            sb.append("\n\n")
        }
        tvLog.text = if (sb.isEmpty()) "Belum ada job." else sb.toString()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onPause() { save(); super.onPause() }

}
