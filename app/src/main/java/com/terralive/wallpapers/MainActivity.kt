package com.terralive.wallpapers

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var listView: LinearLayout
    private var pendingMode: String = "full"
    private var sheet: BottomSheetDialog? = null
    private var sheetBody: LinearLayout? = null

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) { if (pendingMode == "skyloc") applySkyLocation() else applyMode(pendingMode) }
            else {
                Wallpapers.setMode(this, "full")
                Toast.makeText(this, getString(R.string.toast_loc_perm), Toast.LENGTH_LONG).show()
                refreshSheet()
            }
        }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun applyMode(mode: String) {
        if (mode == "full") { Wallpapers.setMode(this, "full"); refreshSheet(); return }
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) { null }
        if (loc != null) {
            Wallpapers.setMode(this, mode, loc.latitude, loc.longitude)
        } else {
            Wallpapers.setMode(this, mode)
            Toast.makeText(this, getString(R.string.toast_loc_wait), Toast.LENGTH_LONG).show()
        }
        refreshSheet()
    }

    private fun selectMode(mode: String) {
        if (mode == "full") { applyMode("full"); return }
        if (hasLocationPermission()) applyMode(mode)
        else { pendingMode = mode; locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        val bg = findViewById<WebView>(R.id.bgEarth)
        bg.setBackgroundColor(Color.BLACK)
        bg.settings.javaScriptEnabled = true
        bg.settings.allowUniversalAccessFromFileURLs = true
        bg.settings.allowFileAccess = true
        bg.loadUrl("file:///android_asset/wallpapers/earth/index.html?look=3&motion=0&wake=0&intro=0")
        /* First 2 minutes: the live planet IS the first screen. No blur; the
           layout scrim keeps the text readable while the real engine runs behind. */
        setupLangChip()

        listView = findViewById(R.id.wallpaperList)
        renderList()

        findViewById<View>(R.id.btnSet).setOnClickListener {
            try {
                startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                    putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        ComponentName(this@MainActivity, EarthWallpaperService::class.java)
                    )
                })
            } catch (e: Exception) {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            }
        }
    }

    override fun onPause() { super.onPause(); findViewById<WebView>(R.id.bgEarth)?.onPause() }
    override fun onResume() { super.onResume(); findViewById<WebView>(R.id.bgEarth)?.onResume(); maybeAskReview() }

    /* Ask for a Play rating once, after the wallpaper has been set for 3+ days */
    private fun maybeAskReview() {
        try {
            val p = Wallpapers.prefs(this)
            val first = p.getLong("first_set_ts", 0L)
            if (first == 0L || p.getBoolean("review_asked", false)) return
            if (System.currentTimeMillis() - first < 3L * 24 * 3600 * 1000) return
            val mgr = com.google.android.play.core.review.ReviewManagerFactory.create(this)
            mgr.requestReviewFlow().addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    p.edit().putBoolean("review_asked", true).apply()
                    try { mgr.launchReviewFlow(this, t.result) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {}
    }

    private val langTags = listOf("en", "lt", "lv", "et", "pl", "de", "fr", "es", "it", "pt-BR", "ja", "ko", "tr", "id", "nl")

    private fun setupLangChip() {
        val chip = findViewById<TextView>(R.id.langChip)
        val cur = AppCompatDelegate.getApplicationLocales()
        val tag = if (!cur.isEmpty) cur.toLanguageTags().split(",")[0]
                  else java.util.Locale.getDefault().toLanguageTag()
        chip.text = tag.split("-")[0].uppercase(java.util.Locale.ROOT)
        chip.setOnClickListener {
            val names = langTags.map { tg ->
                val l = java.util.Locale.forLanguageTag(tg)
                l.getDisplayName(l).replaceFirstChar { c -> c.titlecase(l) }
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.language))
                .setItems(names.toTypedArray()) { _, i ->
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(langTags[i]))
                }
                .show()
        }
    }

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun light(tv: TextView) { tv.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
    private fun medium(tv: TextView) { tv.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }

    private fun renderList() {
        listView.removeAllViews()
        val selectedId = Wallpapers.selected(this)
        for (w in Wallpapers.ALL) {
            val selected = w.id == selectedId
            val card = MaterialCardView(this).apply {
                radius = dp(22f).toFloat()
                setCardBackgroundColor(ContextCompat.getColor(this@MainActivity,
                    if (selected) R.color.aero_glass_hi else R.color.aero_glass))
                strokeWidth = dp(1f)
                strokeColor = ContextCompat.getColor(this@MainActivity,
                    if (selected) R.color.aero_stroke_hi else R.color.aero_stroke)
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12f) }
                setOnClickListener {
                    Wallpapers.select(this@MainActivity, w.id)
                    renderList()
                    openSheet()
                }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22f), dp(20f), dp(22f), dp(20f))
            }
            inner.addView(TextView(this).apply {
                text = getString(w.titleRes)
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (selected) R.color.aero_ice else R.color.aero_text))
                textSize = 17f
                letterSpacing = 0.06f
                light(this)
            })
            inner.addView(TextView(this).apply {
                text = getString(w.subtitleRes)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_dim))
                textSize = 13f
                setPadding(0, dp(4f), 0, 0)
            })
            inner.addView(TextView(this).apply {
                text = getString(R.string.tap_to_configure)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_faint))
                textSize = 11f
                letterSpacing = 0.10f
                setPadding(0, dp(10f), 0, 0)
                medium(this)
            })
            card.addView(inner)
            listView.addView(card)
        }
        listView.addView(TextView(this).apply {
            text = getString(R.string.coming_soon)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_faint))
            textSize = 11f
            letterSpacing = 0.08f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(12f), 0, 0)
        })
        listView.addView(TextView(this).apply {
            text = "vc" + BuildConfig.VERSION_CODE + " (" + BuildConfig.BUILD_DATE + ")"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_faint))
            textSize = 11f
            letterSpacing = 0.08f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(18f), 0, dp(8f))
        })
    }

    private fun openSheet() {
        val d = BottomSheetDialog(this)
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5080D15"))
                val r = dp(26f).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(dp(22f), dp(10f), dp(22f), dp(26f))
        }
        val handle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF")); cornerRadius = dp(2f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(40f), dp(4f)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(4f); bottomMargin = dp(14f)
            }
        }
        wrap.addView(handle)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sheetBody = body
        fillSheet(body)
        wrap.addView(body)
        d.setContentView(wrap)
        (wrap.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        d.setOnDismissListener { sheet = null; sheetBody = null }
        sheet = d
        d.show()
    }

    private fun refreshSheet() {
        renderList()
        sheetBody?.let { it.removeAllViews(); fillSheet(it) }
    }

    private fun sectionLabel(body: LinearLayout, label: String) {
        body.addView(TextView(this).apply {
            text = label
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_faint))
            textSize = 10f
            letterSpacing = 0.34f
            medium(this)
            setPadding(dp(4f), dp(14f), 0, dp(8f))
        })
    }

    private fun optionCard(on: Boolean, title: String, sub: String, tap: () -> Unit): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = dp(18f).toFloat()
            setCardBackgroundColor(ContextCompat.getColor(this@MainActivity,
                if (on) R.color.aero_glass_hi else R.color.aero_glass))
            strokeWidth = dp(1f)
            strokeColor = ContextCompat.getColor(this@MainActivity,
                if (on) R.color.aero_stroke_hi else R.color.aero_stroke)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
            setOnClickListener { tap() }
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(18f), dp(13f), dp(18f), dp(13f))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        col.addView(TextView(this).apply {
            text = title
            setTextColor(ContextCompat.getColor(this@MainActivity,
                if (on) R.color.aero_ice else R.color.aero_text))
            textSize = 15f
            letterSpacing = 0.03f
            light(this)
        })
        if (sub.isNotEmpty()) col.addView(TextView(this).apply {
            text = sub
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_dim))
            textSize = 12f
            setPadding(0, dp(2f), 0, 0)
        })
        row.addView(col)
        row.addView(TextView(this).apply {
            text = if (on) getString(R.string.label_on) else getString(R.string.label_off)
            setTextColor(ContextCompat.getColor(this@MainActivity,
                if (on) R.color.aero_ice else R.color.aero_text_faint))
            textSize = 12f
            letterSpacing = 0.20f
            medium(this)
        })
        card.addView(row)
        return card
    }

    private fun fillSheet(body: LinearLayout) {
        if (Wallpapers.selected(this) == "sky") { fillSkySheet(body); return }
        body.addView(sheetTitle(getString(R.string.sheet_earth)))
        val currentMode0 = Wallpapers.viewMode(this)
        val currentMode1 = if (currentMode0 == "locked") "closeup" else currentMode0
        val currentMode = if (currentMode1 == "sky") "full" else currentMode1
        sectionLabel(body, getString(R.string.section_view))
        val planetModes = listOf(
            Triple("full", getString(R.string.mode_full), getString(R.string.mode_full_sub)),
            Triple("closeup", getString(R.string.mode_closeup), getString(R.string.mode_closeup_sub))
        )
        for ((id, title, sub) in planetModes) {
            val on = id == currentMode
            body.addView(optionCard(on, title, sub) { selectMode(id) })
        }
        sectionLabel(body, getString(R.string.section_effects))
        val p = Wallpapers.prefs(this)
        val motionOn = p.getBoolean(Wallpapers.KEY_MOTION, true)
        body.addView(optionCard(motionOn, getString(R.string.fx_parallax),
            getString(R.string.fx_parallax_sub)) {
            p.edit().putBoolean(Wallpapers.KEY_MOTION, !motionOn).apply(); refreshSheet()
        })
        val rotOn = p.getBoolean(Wallpapers.KEY_ROT, false)
        body.addView(optionCard(rotOn, getString(R.string.fx_rotation),
            getString(R.string.fx_rotation_sub)) {
            p.edit().putBoolean(Wallpapers.KEY_ROT, !rotOn).apply(); refreshSheet()
        })
        addVersionFooter(body)
    }

    private fun sheetTitle(t: String): TextView = TextView(this).apply {
        text = t
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text))
        textSize = 16f
        letterSpacing = 0.30f
        light(this)
    }

    private fun fillSkySheet(body: LinearLayout) {
        body.addView(sheetTitle(getString(R.string.sheet_sky)))
        val p = Wallpapers.prefs(this)
        sectionLabel(body, getString(R.string.section_your_sky))
        val hasLoc = !p.getFloat(Wallpapers.KEY_LAT, Float.NaN).isNaN()
        body.addView(optionCard(hasLoc,
            if (hasLoc) getString(R.string.sky_loc_set) else getString(R.string.sky_loc_use),
            if (hasLoc) getString(R.string.sky_loc_set_sub)
            else getString(R.string.sky_loc_need_sub)) { requestSkyLocation() })
        sectionLabel(body, getString(R.string.section_above_you))
        val satsOn = p.getBoolean(Wallpapers.KEY_SKY_SATS, true)
        body.addView(optionCard(satsOn, getString(R.string.sky_sats),
            getString(R.string.sky_sats_sub)) {
            p.edit().putBoolean(Wallpapers.KEY_SKY_SATS, !satsOn).apply(); refreshSheet()
        })
        val plnOn = p.getBoolean(Wallpapers.KEY_SKY_PLANES, true)
        body.addView(optionCard(plnOn, getString(R.string.sky_planes),
            getString(R.string.sky_planes_sub)) {
            p.edit().putBoolean(Wallpapers.KEY_SKY_PLANES, !plnOn).apply(); refreshSheet()
        })
        sectionLabel(body, getString(R.string.section_effects))
        val motionOn = p.getBoolean(Wallpapers.KEY_MOTION, true)
        body.addView(optionCard(motionOn, getString(R.string.fx_parallax),
            getString(R.string.fx_parallax_sub)) {
            p.edit().putBoolean(Wallpapers.KEY_MOTION, !motionOn).apply(); refreshSheet()
        })
        addVersionFooter(body)
    }

    private fun addVersionFooter(body: LinearLayout) {
        val ver = TextView(this)
        ver.text = "vc" + BuildConfig.VERSION_CODE + " (" + BuildConfig.BUILD_DATE + ")"
        ver.textSize = 12f
        ver.setTextColor(0x66FFFFFF.toInt())
        ver.gravity = android.view.Gravity.CENTER
        ver.setPadding(0, dp(12f), 0, dp(4f))
        body.addView(ver)
    }

    private fun requestSkyLocation() {
        if (hasLocationPermission()) applySkyLocation()
        else { pendingMode = "skyloc"; locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }
    }

    private fun applySkyLocation() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) { null }
        if (loc != null) Wallpapers.setLocation(this, loc.latitude, loc.longitude)
        else Toast.makeText(this, getString(R.string.toast_loc_wait2), Toast.LENGTH_LONG).show()
        refreshSheet()
    }
}
