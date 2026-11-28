package com.example.jobfilter

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.LayoutTransition
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    // ---- warna ----
    private val BG = 0xFF0E1116.toInt()
    private val NAV = 0xFF12161C.toInt()
    private val CARD = 0xFF171B22.toInt()
    private val STROKE = 0xFF262C36.toInt()
    private val CHIP = 0xFF1E2430.toInt()
    private val SEGSEL = 0xFF2A303A.toInt()
    private val TXT = 0xFFE6E8EB.toInt()
    private val SEC = 0xFF9AA3AF.toInt()
    private val GREEN = 0xFF3DDC84.toInt()
    private val AMBER = 0xFFFFB020.toInt()
    private val RED = 0xFFFF6B6B.toInt()
    private val BLUE = 0xFF6EA8FF.toInt()
    private val BLUE_DIM = 0xFF1B2A44.toInt()
    private val OFF = 0xFF3A414D.toInt()

    private lateinit var prefs: Prefs
    private val pages = ArrayList<ScrollView>()
    private val cols = ArrayList<LinearLayout>()
    private val tabBars = ArrayList<View>()
    private val tabLabels = ArrayList<TextView>()
    private var cur = 0

    // utama
    private lateinit var dot: View
    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: TextView
    private lateinit var toggleBg: GradientDrawable
    private var toggleColor = AMBER
    private var pulse: ObjectAnimator? = null
    private lateinit var tvAccess: TextView
    private lateinit var btnAccess: TextView
    private lateinit var tvLulus: TextView
    private lateinit var tvIgn: TextView
    private lateinit var tvMahal: TextView
    private lateinit var flowSummary: FlowLayout
    // tapisan
    private lateinit var chipDel: TextView
    private lateinit var chipSch: TextView
    private lateinit var tvRange: TextView
    private lateinit var sbMin: SeekBar
    private lateinit var sbMax: SeekBar
    private lateinit var etMin: EditText
    private lateinit var etMax: EditText
    private lateinit var tvImport: TextView
    private lateinit var btnPickup: TextView
    private lateinit var btnDrop: TextView
    private lateinit var btnIgnPickup: TextView
    private lateinit var btnIgnDrop: TextView
    private lateinit var etPerKm: EditText
    private lateinit var etPremAbs: EditText
    private lateinit var etFactor: EditText
    private lateinit var btnGeo: TextView
    private lateinit var tvGeo: TextView
    // suara
    private lateinit var tvRate: TextView
    private lateinit var sbRate: SeekBar
    // log
    private lateinit var segOk: TextView
    private lateinit var segAll: TextView
    private lateinit var tvLogHead: TextView
    private lateinit var logList: LinearLayout
    private lateinit var btnMore: TextView

    private var syncing = false
    private var logOnlyOk = true
    private var logLimit = 50
    private var geoRunning = false
    private var geoStop = false

    // ================= pembantu UI =================
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun rect(color: Int, r: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(r).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun oval(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    private fun tv(t: String, size: Float = 14f, color: Int = TXT, bold: Boolean = false): TextView = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun lp(w: Int = -1, h: Int = -2, bottom: Int = 0, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight).apply { bottomMargin = dp(bottom) }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = rect(CARD, 16, STROKE)
        setPadding(dp(16), dp(14), dp(16), dp(14)); layoutParams = lp(bottom = 12)
    }

    private fun View.press() {
        setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }
            false
        }
    }

    private fun btn(label: String, fill: Int = CHIP, textColor: Int = TXT, onClick: () -> Unit): TextView = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(textColor); gravity = Gravity.CENTER
        background = rect(fill, 12, if (fill == CHIP) STROKE else 0)
        setPadding(dp(12), dp(12), dp(12), dp(12)); isClickable = true
        press(); setOnClickListener { onClick() }
    }

    private fun styleChip(c: TextView, on: Boolean) {
        c.background = rect(if (on) BLUE_DIM else CHIP, 20, if (on) BLUE else STROKE)
        c.setTextColor(if (on) BLUE else SEC)
    }

    private fun toggleChip(label: String, on: Boolean, onChange: (Boolean) -> Unit): TextView {
        var state = on
        val c = TextView(this).apply {
            text = label; textSize = 14f; gravity = Gravity.CENTER
            setPadding(dp(18), dp(10), dp(18), dp(10)); isClickable = true; press()
        }
        styleChip(c, state)
        c.setOnClickListener {
            state = !state; styleChip(c, state); onChange(state)
            c.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).withEndAction {
                c.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            }.start()
        }
        return c
    }

    private fun sw(label: String, on: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        text = label; setTextColor(TXT); textSize = 14f; isChecked = on
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(GREEN, 0xFFB0B7C3.toInt()))
        trackTintList = ColorStateList(states, intArrayOf(0x663DDC84, OFF))
        setPadding(0, dp(6), 0, dp(6)); layoutParams = lp()
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun SeekBar.tint(c: Int) {
        progressTintList = ColorStateList.valueOf(c); thumbTintList = ColorStateList.valueOf(c)
        progressBackgroundTintList = ColorStateList.valueOf(OFF)
    }

    private fun field(hint: String, numeric: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint; setHintTextColor(SEC); setTextColor(TXT); textSize = 14f; setSingleLine()
        background = rect(CHIP, 10, STROKE); setPadding(dp(12), dp(10), dp(12), dp(10))
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    }

    private fun numField(h: String, v: Float) = field(h, true).apply { if (v > 0f) setText(v.toString().removeSuffix(".0")) }

    private fun row(vararg views: View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEachIndexed { i, v ->
            addView(v, LinearLayout.LayoutParams(0, -2, 1f).apply { if (i < views.size - 1) marginEnd = dp(8) })
        }
    }

    private fun newPage(): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)); layoutTransition = LayoutTransition()
        }
        val sv = ScrollView(this).apply { addView(col); isVerticalScrollBarEnabled = false; setBackgroundColor(BG) }
        pages.add(sv); cols.add(col)
        return Pair(sv, col)
    }

    private fun staggerIn(col: LinearLayout) {
        for (i in 0 until col.childCount) {
            val v = col.getChildAt(i)
            v.alpha = 0f; v.translationY = dp(14).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(minOf(i, 8) * 45L).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    private fun countTo(t: TextView, to: Int) {
        val from = (t.tag as? Int) ?: 0
        t.tag = to
        if (from == to) { t.text = to.toString(); return }
        ValueAnimator.ofInt(from, to).apply {
            duration = 500
            addUpdateListener { t.text = (it.animatedValue as Int).toString() }
        }.start()
    }

    private fun smallChip(label: String, bg: Int, fg: Int, size: Float = 11f): TextView = TextView(this).apply {
        text = label; textSize = size; setTextColor(fg); background = rect(bg, 6)
        setPadding(dp(8), dp(2), dp(8), dp(2))
    }

    // ================= onCreate =================
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        prefs = Prefs(this)
        window.statusBarColor = BG
        window.navigationBarColor = NAV
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(BG) }
        val content = FrameLayout(this)
        val builders = listOf(buildHome(), buildFilter(), buildVoice(), buildLog())
        builders.forEachIndexed { i, p ->
            content.addView(p, FrameLayout.LayoutParams(-1, -1))
            if (i != 0) p.visibility = View.GONE
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(STROKE) }, LinearLayout.LayoutParams(-1, dp(1)))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(NAV) }
        listOf("Utama", "Tapisan", "Suara", "Log").forEachIndexed { i, name ->
            val label = tv(name, 12f, if (i == 0) BLUE else SEC, true)
            val bar = View(this).apply { background = rect(BLUE, 2); scaleX = if (i == 0) 1f else 0f }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(12), 0, dp(10)); isClickable = true
                addView(label); addView(bar, LinearLayout.LayoutParams(dp(22), dp(3)).apply { topMargin = dp(6) })
                setOnClickListener { showTab(i) }
            }
            tabLabels.add(label); tabBars.add(bar)
            nav.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
        updateRangeUi(false)
        root.post { staggerIn(cols[0]) }
    }

    private fun showTab(i: Int) {
        if (i == cur) return
        save()
        val old = pages[cur]
        val nw = pages[i]
        cur = i
        old.animate().alpha(0f).setDuration(140).withEndAction { if (pages[cur] !== old) old.visibility = View.GONE }.start()
        nw.visibility = View.VISIBLE; nw.alpha = 1f
        tabLabels.forEachIndexed { k, l -> l.setTextColor(if (k == i) BLUE else SEC) }
        tabBars.forEachIndexed { k, bar -> bar.animate().scaleX(if (k == i) 1f else 0f).setDuration(200).start() }
        staggerIn(cols[i])
        if (i == 0) refreshHome()
        if (i == 3) refreshLog(true)
    }

    // ================= skrin Utama =================
    private fun buildHome(): ScrollView {
        val (sv, col) = newPage()
        col.addView(tv("Job Filter", 24f, TXT, true))
        col.addView(tv("Baca job Lalamove ikut tapisan kau", 13f, SEC), lp(bottom = 16))

        val st = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        dot = View(this).apply { background = oval(GREEN) }
        top.addView(dot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })
        tvStatus = tv("", 18f, TXT, true)
        top.addView(tvStatus)
        st.addView(top, lp(bottom = 14))

        toggleBg = rect(AMBER, 14)
        btnToggle = TextView(this).apply {
            textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(0xFF2A1D00.toInt()); background = toggleBg
            setPadding(0, dp(15), 0, dp(15)); isClickable = true; press()
            setOnClickListener { prefs.enabled = !prefs.enabled; refreshHome() }
        }
        st.addView(btnToggle, lp(bottom = 12))

        val acc = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        tvAccess = tv("", 12f, SEC)
        acc.addView(tvAccess, LinearLayout.LayoutParams(0, -2, 1f))
        btnAccess = btn("Aktifkan", BLUE_DIM, BLUE) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }.apply {
            textSize = 12f; setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        acc.addView(btnAccess)
        st.addView(acc)
        col.addView(st)

        fun stat(label: String, color: Int): Pair<View, TextView> {
            val v = tv("0", 24f, color, true); v.tag = 0
            val c = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; background = rect(CARD, 14, STROKE); setPadding(dp(12), dp(10), dp(12), dp(10))
                addView(v); addView(tv(label, 11f, SEC))
            }
            return Pair(c, v)
        }
        val s1 = stat("Lulus hari ini", GREEN); val s2 = stat("Diabaikan", TXT); val s3 = stat("Mahal", AMBER)
        tvLulus = s1.second; tvIgn = s2.second; tvMahal = s3.second
        col.addView(row(s1.first, s2.first, s3.first), lp(bottom = 12))

        val sc = card()
        val hdr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        hdr.addView(tv("Tapisan semasa", 15f, TXT, true), LinearLayout.LayoutParams(0, -2, 1f))
        hdr.addView(tv("Ubah", 13f, BLUE, true).apply { isClickable = true; setOnClickListener { showTab(1) } })
        sc.addView(hdr, lp(bottom = 10))
        flowSummary = FlowLayout(this, dp(6), dp(6))
        sc.addView(flowSummary, lp())
        col.addView(sc)
        return sv
    }

    private fun refreshHome() {
        val on = prefs.enabled
        tvStatus.text = if (on) "Membaca job Lalamove" else "Dijeda"
        btnToggle.text = if (on) "Jeda" else "Sambung"
        val target = if (on) AMBER else GREEN
        if (target != toggleColor) {
            ValueAnimator.ofObject(ArgbEvaluator(), toggleColor, target).apply {
                duration = 280; addUpdateListener { toggleBg.setColor(it.animatedValue as Int) }
            }.start()
            toggleColor = target
        } else toggleBg.setColor(target)
        dot.background = oval(if (on) GREEN else AMBER)
        pulse?.cancel(); dot.alpha = 1f; dot.scaleX = 1f; dot.scaleY = 1f
        if (on) {
            pulse = ObjectAnimator.ofPropertyValuesHolder(
                dot, PropertyValuesHolder.ofFloat("alpha", 1f, 0.3f),
                PropertyValuesHolder.ofFloat("scaleX", 1f, 1.35f), PropertyValuesHolder.ofFloat("scaleY", 1f, 1.35f)
            ).apply { duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start() }
        }

        val access = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")?.contains(packageName) == true
        tvAccess.text = if (access) "Akses notifikasi aktif" else "Akses notifikasi belum diaktifkan"
        tvAccess.setTextColor(if (access) SEC else RED)
        btnAccess.visibility = if (access) View.GONE else View.VISIBLE

        val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val s = LogDb.get(this).stats(cal.timeInMillis)
        countTo(tvLulus, s[0]); countTo(tvIgn, s[1]); countTo(tvMahal, s[2])

        flowSummary.removeAllViews()
        fun chip(t: String) {
            flowSummary.addView(TextView(this).apply {
                text = t; textSize = 12f; setTextColor(TXT); background = rect(CHIP, 8, STROKE)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
        }
        chip(when {
            prefs.allowDelivery && prefs.allowScheduled -> "Delivery + Scheduled"
            prefs.allowDelivery -> "Delivery sahaja"
            prefs.allowScheduled -> "Scheduled sahaja"
            else -> "Tiada jenis order"
        })
        val mn = prefs.minRm.toInt(); val mx = prefs.maxRm.toInt()
        chip("RM $mn – " + (if (mx > 0) "RM $mx" else "tiada had"))
        chip(if (prefs.selPickup.isEmpty()) "Pickup: semua" else "Pickup ${prefs.selPickup.size}")
        chip(if (prefs.selDrop.isEmpty()) "Drop: semua" else "Drop ${prefs.selDrop.size}")
        val ign = prefs.ignPickup.size + prefs.ignDrop.size
        if (ign > 0) chip("Abaikan $ign")
        val fav = prefs.favPickup.size + prefs.favDrop.size
        if (fav > 0) chip("★ $fav favorit")
        if (prefs.premiumOn) chip("Mahal: RM${prefs.premPerKm.toInt()}/km")
    }

    // ================= skrin Tapisan =================
    private fun buildFilter(): ScrollView {
        val (sv, col) = newPage()
        col.addView(tv("Tapisan", 24f, TXT, true), lp(bottom = 12))

        val c1 = card()
        c1.addView(tv("Jenis order", 15f, TXT, true), lp(bottom = 10))
        chipDel = toggleChip("Delivery", prefs.allowDelivery) { prefs.allowDelivery = it }
        chipSch = toggleChip("Scheduled", prefs.allowScheduled) { prefs.allowScheduled = it }
        val cr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cr.addView(chipDel, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) }); cr.addView(chipSch)
        c1.addView(cr)
        col.addView(c1)

        val c2 = card()
        c2.addView(tv("Julat nilai RM (termasuk bonus)", 15f, TXT, true), lp(bottom = 6))
        tvRange = tv("", 20f, BLUE, true)
        c2.addView(tvRange, lp(bottom = 6))
        sbMin = SeekBar(this).apply { max = 100; tint(BLUE) }   // 0..1000, langkah RM10
        sbMax = SeekBar(this).apply { max = 100; tint(BLUE) }   // hujung kanan = tiada had
        c2.addView(tv("Minimum", 12f, SEC)); c2.addView(sbMin, lp(bottom = 6))
        c2.addView(tv("Maksimum (hujung kanan = tiada had)", 12f, SEC)); c2.addView(sbMax, lp(bottom = 10))
        c2.addView(tv("Atau taip nilai tepat", 12f, SEC), lp(bottom = 4))
        etMin = numField("Minimum", prefs.minRm); etMax = numField("Maksimum", prefs.maxRm)
        c2.addView(row(etMin, etMax))
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
        col.addView(c2)

        val c3 = card()
        c3.addView(tv("Nama tempat", 15f, TXT, true), lp(bottom = 4))
        tvImport = tv("", 12f, SEC)
        c3.addView(tvImport, lp(bottom = 10))
        c3.addView(btn("Import CSV") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }, 42)
        }, lp(bottom = 8))
        btnPickup = btn("") { pickDialog("Pilih pickup", prefs.pickupNames, prefs.selPickup, prefs.favPickup) { s, f -> prefs.selPickup = s; prefs.favPickup = f; refreshAll() } }
        btnDrop = btn("") { pickDialog("Pilih drop", prefs.dropNames, prefs.selDrop, prefs.favDrop) { s, f -> prefs.selDrop = s; prefs.favDrop = f; refreshAll() } }
        btnIgnPickup = btn("") { pickDialog("Abaikan pickup", prefs.pickupNames, prefs.ignPickup, null) { s, _ -> prefs.ignPickup = s; refreshAll() } }
        btnIgnDrop = btn("") { pickDialog("Abaikan drop", prefs.dropNames, prefs.ignDrop, null) { s, _ -> prefs.ignDrop = s; refreshAll() } }
        c3.addView(row(btnPickup, btnDrop), lp(bottom = 8))
        c3.addView(row(btnIgnPickup, btnIgnDrop), lp(bottom = 6))
        c3.addView(tv("Tiada yang dipilih = semua tempat dibenarkan. Dalam senarai pilih, tekan lama satu nama untuk jadikan favorit.", 12f, SEC), lp(bottom = 6))
        c3.addView(sw("Favorit sentiasa lulus (abaikan julat RM dan senarai dipilih)", prefs.favBypass) { prefs.favBypass = it })
        col.addView(c3)

        val c4 = card()
        c4.addView(sw("Sentiasa baca job mahal", prefs.premiumOn) { prefs.premiumOn = it })
        c4.addView(tv("Mahal = RM per km mencapai had, atau RM melebihi had mutlak. Lepas julat RM dan senarai dipilih, tapi bukan senarai abaikan atau jenis order. Jarak ialah anggaran: garis lurus x faktor jalan.", 12f, SEC), lp(bottom = 10))
        c4.addView(row(tv("RM per km", 12f, SEC), tv("RM mutlak (0 = off)", 12f, SEC), tv("Faktor jalan", 12f, SEC)), lp(bottom = 4))
        etPerKm = numField("10", prefs.premPerKm); etPremAbs = numField("0", prefs.premAbs); etFactor = numField("1.3", prefs.roadFactor)
        c4.addView(row(etPerKm, etPremAbs, etFactor), lp(bottom = 10))
        tvGeo = tv("", 12f, SEC)
        c4.addView(tvGeo, lp(bottom = 6))
        btnGeo = btn("Muat koordinat tempat") {
            if (geoRunning) { geoStop = true; return@btn }
            val names = (prefs.pickupNames + prefs.dropNames).distinct()
            if (names.isEmpty()) { toast("Import CSV dulu"); return@btn }
            geoRunning = true; geoStop = false; btnGeo.text = "Henti"
            Thread {
                var done = 0
                for (n in names) {
                    if (geoStop) break
                    if (LogDb.get(this).geoGet(n.trim().lowercase()) == null) { Geo.lookup(this, n); Thread.sleep(150) }
                    done++
                    if (done % 5 == 0) runOnUiThread { tvGeo.text = "Memuat koordinat: $done / ${names.size}" }
                }
                runOnUiThread { geoRunning = false; btnGeo.text = "Muat koordinat tempat"; refreshAll() }
            }.start()
        }
        c4.addView(btnGeo)
        col.addView(c4)
        return sv
    }

    private fun refreshFilter() {
        tvImport.text = "Senarai: ${prefs.pickupNames.size} pickup, ${prefs.dropNames.size} drop"
        btnPickup.text = "Pilih pickup\n${prefs.selPickup.size} dipilih, ★ ${prefs.favPickup.size}"
        btnDrop.text = "Pilih drop\n${prefs.selDrop.size} dipilih, ★ ${prefs.favDrop.size}"
        btnIgnPickup.text = "Abaikan pickup\n${prefs.ignPickup.size}"
        btnIgnDrop.text = "Abaikan drop\n${prefs.ignDrop.size}"
        if (!geoRunning) { val (ok, bad) = LogDb.get(this).geoCounts(); tvGeo.text = "Koordinat tempat: $ok berjaya, $bad gagal" }
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

    // ================= skrin Suara =================
    private fun buildVoice(): ScrollView {
        val (sv, col) = newPage()
        col.addView(tv("Suara", 24f, TXT, true), lp(bottom = 12))

        val c1 = card()
        c1.addView(tv("Bacaan", 15f, TXT, true), lp(bottom = 6))
        c1.addView(sw("Baca guna suara", prefs.speak) { prefs.speak = it })
        c1.addView(sw("Notifikasi sendiri (job lulus)", prefs.notify) { prefs.notify = it })
        col.addView(c1)

        val c2 = card()
        c2.addView(tv("Kelajuan suara", 15f, TXT, true), lp(bottom = 4))
        tvRate = tv("", 20f, BLUE, true)
        c2.addView(tvRate, lp(bottom = 6))
        sbRate = SeekBar(this).apply {
            max = 20; tint(BLUE)   // 0.5x .. 2.5x, langkah 0.1
            progress = Math.round((prefs.speechRate - 0.5f) / 0.1f).coerceIn(0, 20)
        }
        fun rateText() { tvRate.text = String.format(Locale.US, "%.1f", 0.5f + sbRate.progress * 0.1f) + "x" }
        rateText()
        sbRate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { rateText(); if (user) prefs.speechRate = 0.5f + p * 0.1f }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        c2.addView(sbRate)
        c2.addView(tv("Naikkan untuk nama tempat yang panjang.", 12f, SEC))
        col.addView(c2)

        val c3 = card()
        c3.addView(tv("Susunan bacaan", 15f, TXT, true), lp(bottom = 6))
        c3.addView(tv("Nilai RM, pickup, drop. Job mahal tambah RM per kilo meter di hujung. Favorit disebut paling akhir.", 13f, SEC))
        col.addView(c3)
        return sv
    }

    // ================= skrin Log =================
    private fun buildLog(): ScrollView {
        val (sv, col) = newPage()
        col.addView(tv("Log", 24f, TXT, true), lp(bottom = 12))

        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; background = rect(CARD, 12, STROKE); setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        segOk = tv("Lulus", 14f, TXT, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(9), 0, dp(9)); isClickable = true }
        segAll = tv("Semua", 14f, SEC, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(9), 0, dp(9)); isClickable = true }
        seg.addView(segOk, LinearLayout.LayoutParams(0, -2, 1f)); seg.addView(segAll, LinearLayout.LayoutParams(0, -2, 1f))
        fun styleSeg() {
            segOk.background = if (logOnlyOk) rect(SEGSEL, 9) else null
            segAll.background = if (!logOnlyOk) rect(SEGSEL, 9) else null
            segOk.setTextColor(if (logOnlyOk) TXT else SEC); segAll.setTextColor(if (!logOnlyOk) TXT else SEC)
        }
        styleSeg()
        segOk.setOnClickListener { logOnlyOk = true; logLimit = 50; styleSeg(); refreshLog(true) }
        segAll.setOnClickListener { logOnlyOk = false; logLimit = 50; styleSeg(); refreshLog(true) }
        col.addView(seg, lp(bottom = 8))

        tvLogHead = tv("", 12f, SEC)
        col.addView(tvLogHead, lp(bottom = 8))
        col.addView(row(
            btn("Muat semula") { refreshLog(true) },
            btn("Eksport CSV") {
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE); type = "text/csv"
                    putExtra(Intent.EXTRA_TITLE, "job_log_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + ".csv")
                }, 43)
            }
        ), lp(bottom = 12))

        logList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(logList, lp(bottom = 4))
        btnMore = btn("Muat lagi") { logLimit += 50; refreshLog(false) }
        col.addView(btnMore, lp(bottom = 8))
        col.addView(btn("Kosongkan log", 0xFF3A1618.toInt(), RED) { LogDb.get(this).clear(); refreshLog(false); refreshHome() })
        return sv
    }

    private fun logCard(r: LogRow): View {
        val f = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = rect(CARD, 14, STROKE); setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(View(this).apply { background = oval(if (r.ok) GREEN else RED) }, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
        top.addView(tv("RM" + String.format(Locale.US, if (r.rm % 1.0 == 0.0) "%.0f" else "%.2f", r.rm), 22f, if (r.ok) TXT else SEC, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(tv(f.format(Date(r.t)), 12f, SEC))
        c.addView(top, lp(bottom = 6))

        val chips = FlowLayout(this, dp(6), dp(6))
        if (r.prem) chips.addView(smallChip("MAHAL", 0xFF3A2A08.toInt(), 0xFFFFC966.toInt()))
        chips.addView(smallChip(if (r.sched) "Scheduled" else "Immediate", CHIP, 0xFFC3CAD4.toInt()))
        if (r.fav) chips.addView(smallChip("★ Favorit", CHIP, 0xFFFFC966.toInt()))
        if (r.bonus > 0) chips.addView(smallChip("Bonus RM" + String.format(Locale.US, "%.2f", r.bonus), CHIP, 0xFFC3CAD4.toInt()))
        if (!r.ok) chips.addView(smallChip(r.reason, 0xFF3A1618.toInt(), 0xFFFF9A9A.toInt()))
        c.addView(chips, lp(bottom = 6))

        c.addView(tv(r.p + " → " + r.d, 14f, if (r.ok) TXT else SEC))
        if (r.km >= 0.5) {
            c.addView(tv("~" + String.format(Locale.US, "%.1f", r.km) + " km • RM" + String.format(Locale.US, "%.1f", r.rm / r.km) + "/km", 12f, SEC))
        }
        return c
    }

    private fun refreshLog(animate: Boolean) {
        val db = LogDb.get(this)
        tvLogHead.text = "Lulus ${db.count(true)}  •  Semua ${db.count(false)}  (simpan sehingga 3000 setiap jenis)"
        logList.removeAllViews()
        val rows = db.query(logOnlyOk, logLimit)
        if (rows.isEmpty()) logList.addView(tv("Belum ada job.", 14f, SEC))
        rows.forEachIndexed { i, r ->
            val v = logCard(r)
            logList.addView(v, lp(bottom = 10))
            if (animate && i < 12) {
                v.alpha = 0f; v.translationY = dp(14).toFloat()
                v.animate().alpha(1f).translationY(0f).setStartDelay(i * 35L).setDuration(220)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }
        btnMore.visibility = if (rows.size >= logLimit) View.VISIBLE else View.GONE
    }

    // ================= dialog pilih tempat =================
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
        val tvCount = tv("", 13f, SEC).apply { setPadding(0, dp(4), 0, dp(4)) }
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
            if (n in fav) { fav.remove(n); toast("Buang favorit: $n") } else { fav.add(n); toast("★ favorit: $n") }
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
        if (favs != null) box.addView(tv("Tekan lama satu nama untuk jadikan favorit (naik ke atas).", 12f, SEC))
        box.addView(cbOnly); box.addView(bar)
        box.addView(lv, LinearLayout.LayoutParams(-1, dp(320)))
        bind()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert).setTitle(title).setView(box)
            .setPositiveButton("Siap") { _, _ -> onDone(sel, fav) }
            .setNegativeButton("Batal", null).show()
    }

    // ================= import / eksport =================
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data ?: return
        if (res != RESULT_OK) return
        if (req == 43) {
            Thread {
                try {
                    val rows = LogDb.get(this).query(false, Int.MAX_VALUE).reversed()   // lama ke baru
                    val f = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                    contentResolver.openOutputStream(uri)!!.bufferedWriter().use { w ->
                        w.write("masa,jenis,pickup,drop,rm_jumlah,bonus,km,rm_per_km,lulus,mahal,favorit,sebab\n")
                        for (r in rows) {
                            val km = if (r.km >= 0.5) String.format(Locale.US, "%.1f", r.km) else ""
                            val perKm = if (r.km >= 0.5) String.format(Locale.US, "%.2f", r.rm / r.km) else ""
                            w.write(listOf(
                                q(f.format(Date(r.t))), if (r.sched) "Scheduled" else "Immediate", q(r.p), q(r.d),
                                String.format(Locale.US, "%.2f", r.rm), String.format(Locale.US, "%.2f", r.bonus), km, perKm,
                                if (r.ok) "1" else "0", if (r.prem) "1" else "0", if (r.fav) "1" else "0", q(r.reason)
                            ).joinToString(",") + "\n")
                        }
                    }
                    runOnUiThread { toast("Eksport siap: ${rows.size} baris") }
                } catch (e: Exception) {
                    runOnUiThread { toast("Gagal eksport: ${e.message}") }
                }
            }.start()
            return
        }
        if (req != 42) return
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
                    toast("Import: ${p.size} pickup, ${d.size} drop"); refreshAll()
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Gagal baca CSV: ${e.message}") }
            }
        }.start()
    }

    // ================= simpan / muat semula =================
    private fun save() {
        prefs.minRm = etMin.text.toString().toFloatOrNull() ?: 0f
        prefs.maxRm = etMax.text.toString().toFloatOrNull() ?: 0f
        prefs.premPerKm = etPerKm.text.toString().toFloatOrNull() ?: 0f
        prefs.premAbs = etPremAbs.text.toString().toFloatOrNull() ?: 0f
        prefs.roadFactor = etFactor.text.toString().toFloatOrNull() ?: 1.3f
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun refreshAll() { refreshHome(); refreshFilter(); refreshLog(false) }

    override fun onResume() { super.onResume(); refreshAll() }
    override fun onPause() { save(); super.onPause() }
}
