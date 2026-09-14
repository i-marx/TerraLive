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
            if (granted) applyMode(pendingMode)
            else {
                Wallpapers.setMode(this, "full")
                Toast.makeText(this, "Location permission is needed to centre Earth on your place", Toast.LENGTH_LONG).show()
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
            Toast.makeText(this, "Getting your location - open Maps once, then re-select", Toast.LENGTH_LONG).show()
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
        bg.settings.allowFileAccess = true
        bg.loadUrl("file:///android_asset/wallpapers/earth/index.html?look=3&motion=0&wake=0&intro=0")
        if (Build.VERSION.SDK_INT >= 31) {
            bg.setRenderEffect(RenderEffect.createBlurEffect(55f, 55f, Shader.TileMode.CLAMP))
        } else {
            findViewById<View>(R.id.bgScrim).setBackgroundColor(Color.parseColor("#E0030507"))
        }

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
    override fun onResume() { super.onResume(); findViewById<WebView>(R.id.bgEarth)?.onResume() }

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
                text = w.title
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (selected) R.color.aero_ice else R.color.aero_text))
                textSize = 17f
                letterSpacing = 0.06f
                light(this)
            })
            inner.addView(TextView(this).apply {
                text = w.subtitle
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text_dim))
                textSize = 13f
                setPadding(0, dp(4f), 0, 0)
            })
            inner.addView(TextView(this).apply {
                text = "Tap to configure"
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
            text = if (on) "ON" else "OFF"
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
        body.addView(TextView(this).apply {
            text = "EARTH"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.aero_text))
            textSize = 16f
            letterSpacing = 0.30f
            light(this)
        })
        sectionLabel(body, "VIEW")
        val currentMode = Wallpapers.viewMode(this)
        val modes = listOf(
            Triple("full", "Full view", "Earth from deep space, real day and night"),
            Triple("locked", "Locked to my place", "Your location centred, the Sun sweeps past"),
            Triple("closeup", "Close-up over my place", "Low orbit above your home, ultra-HD terrain")
        )
        for ((id, title, sub) in modes) {
            val on = id == currentMode
            body.addView(optionCard(on, title, sub) { selectMode(id) })
        }
        sectionLabel(body, "EFFECTS")
        val p = Wallpapers.prefs(this)
        val motionOn = p.getBoolean(Wallpapers.KEY_MOTION, true)
        body.addView(optionCard(motionOn, "Parallax on tilt",
            "The planet shifts subtly as you move your phone") {
            p.edit().putBoolean(Wallpapers.KEY_MOTION, !motionOn).apply(); refreshSheet()
        })
        val wakeOn = p.getBoolean(Wallpapers.KEY_WAKE, true)
        body.addView(optionCard(wakeOn, "Unlock spin",
            "A quick 360 orbit of the planet each time you return") {
            p.edit().putBoolean(Wallpapers.KEY_WAKE, !wakeOn).apply(); refreshSheet()
        })
    }
}
