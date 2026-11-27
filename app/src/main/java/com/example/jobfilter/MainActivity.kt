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
    private lateinit var etMin: EditText
    private lateinit var etMax: EditText
    private lateinit var btnPickup: Button
    private lateinit var btnDrop: Button
    private lateinit var tvImport: TextView
    private lateinit var sbMin: SeekBar
    private lateinit var sbMax: SeekBar
    private lateinit var tvRange: TextView
    private var syncing = false
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView

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

        root.addView(label("Julat nilai RM"))
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
            override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) {
                if (user) { if (sbMax.progress < 100 && p > sbMax.progress) sbMin.progress = sbMax.progress; updateRangeUi(true) }
            }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {}
        })
        sbMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, p: Int, user: Boolean) {
                if (user) {
                    if (p < 1) sbMax.progress = 1
                    if (sbMax.progress < sbMin.progress) sbMax.progress = sbMin.progress
                    updateRangeUi(true)
                }
            }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) {}
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
                pickDialog("Pilih Pickup", prefs.pickupNames, prefs.selPickup) { prefs.selPickup = it; refresh() }
            }
        }
        btnDrop = Button(this).apply {
            setOnClickListener {
                pickDialog("Pilih Drop", prefs.dropNames, prefs.selDrop) { prefs.selDrop = it; refresh() }
            }
        }
        root.addView(btnPickup); root.addView(btnDrop)
        root.addView(TextView(this).apply { text = "Tiada yang dipilih = semua tempat dibenarkan."; textSize = 12f })

        swSpeak = Switch(this).apply { text = "Baca guna suara"; isChecked = prefs.speak }
        swNotify = Switch(this).apply { text = "Notifikasi sendiri (job lulus)"; isChecked = prefs.notify }
        root.addView(label("")); root.addView(swSpeak); root.addView(swNotify)

        root.addView(Button(this).apply { text = "Simpan"; setOnClickListener { save(); toast("Disimpan") } })

        root.addView(label("Log (✔ lulus, ✘ diabaikan)"))
        tvLog = TextView(this).apply { textSize = 13f }
        root.addView(tvLog)
        root.addView(Button(this).apply { text = "Kosongkan log"; setOnClickListener { prefs.clearLog(); refresh() } })
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

    /** dialog multi-select: carian (banyak perkataan), fast-scroll, "dipilih saja" */
    private fun pickDialog(title: String, all: List<String>, selected: Set<String>, onDone: (Set<String>) -> Unit) {
        if (all.isEmpty()) { toast("Import CSV dulu"); return }
        val sel = selected.toMutableSet()
        var shown = all
        var query = ""
        var onlySel = false
        val tvCount = TextView(this).apply { textSize = 13f; setPadding(0, dp(4), 0, dp(4)) }
        val lv = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_MULTIPLE; isFastScrollEnabled = true }
        fun count() { tvCount.text = "Dipilih ${sel.size}  •  Dipaparkan ${shown.size} / ${all.size}" }
        fun bind() {
            val words = query.split(" ").filter { it.isNotBlank() }
            shown = all.filter { n -> (!onlySel || n in sel) && words.all { n.contains(it, true) } }
            lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, shown)
            shown.forEachIndexed { i, n -> lv.setItemChecked(i, n in sel) }
            count()
        }
        lv.setOnItemClickListener { _, _, pos, _ ->
            val n = shown[pos]; if (lv.isItemChecked(pos)) sel.add(n) else sel.remove(n); count()
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
        box.addView(et); box.addView(tvCount); box.addView(cbOnly); box.addView(bar)
        box.addView(lv, LinearLayout.LayoutParams(-1, dp(340)))
        bind()
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton("Siap") { _, _ -> onDone(sel) }
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
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun refresh() {
        val on = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        tvStatus.text = if (on) "Notification access: AKTIF ✔" else "Notification access: BELUM — tekan butang bawah"
        tvImport.text = "Senarai: ${prefs.pickupNames.size} pickup, ${prefs.dropNames.size} drop"
        btnPickup.text = "Pilih Pickup (${prefs.selPickup.size} dipilih)"
        btnDrop.text = "Pilih Drop (${prefs.selDrop.size} dipilih)"
        val f = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val arr = prefs.logs()
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ok = o.getString("r").isEmpty()
            sb.append(if (ok) "✔ " else "✘ ").append(f.format(Date(o.getLong("t"))))
                .append("  [").append(o.getString("tag")).append("] RM").append(o.getDouble("rm")).append('\n')
                .append(o.getString("p")).append(" > ").append(o.getString("d"))
            if (!ok) sb.append("\n(").append(o.getString("r")).append(')')
            sb.append("\n\n")
        }
        tvLog.text = if (sb.isEmpty()) "Belum ada job." else sb.toString()
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onPause() { save(); super.onPause() }
}
