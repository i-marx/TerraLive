package com.terralive.wallpapers

import android.Manifest
import android.app.WallpaperManager
import android.content.pm.PackageManager
import android.location.LocationManager
import android.widget.Toast
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var listView: LinearLayout

    private var pendingMode: String = "full"

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) applyMode(pendingMode)
            else {
                Wallpapers.setMode(this, "full")
                Toast.makeText(this, "Location permission is needed to centre Earth on your place", Toast.LENGTH_LONG).show()
                renderList()
            }
        }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun applyMode(mode: String) {
        if (mode == "full") { Wallpapers.setMode(this, "full"); renderList(); return }
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
            Toast.makeText(this, "Getting your location — open Maps once, then re-select", Toast.LENGTH_LONG).show()
        }
        renderList()
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

    private fun dp(v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    private fun renderList() {
        listView.removeAllViews()
        val selectedId = Wallpapers.selected(this)
        for (w in Wallpapers.ALL) {
            val selected = w.id == selectedId
            val card = MaterialCardView(this).apply {
                radius = dp(16f).toFloat()
                setCardBackgroundColor(Color.parseColor("#0C1220"))
                strokeWidth = if (selected) dp(2f) else dp(1f)
                strokeColor = ContextCompat.getColor(
                    this@MainActivity,
                    if (selected) R.color.terra_blue else R.color.terra_grey_dim
                )
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12f) }
                setOnClickListener {
                    Wallpapers.select(this@MainActivity, w.id)
                    renderList()
                }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18f), dp(16f), dp(18f), dp(16f))
            }
            inner.addView(TextView(this).apply {
                text = if (selected) "${w.title}   ●" else w.title
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (selected) R.color.terra_blue else R.color.terra_white))
                textSize = 18f
                letterSpacing = 0.04f
            })
            inner.addView(TextView(this).apply {
                text = w.subtitle
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terra_grey))
                textSize = 13f
                setPadding(0, dp(4f), 0, 0)
            })
            card.addView(inner)
            listView.addView(card)
        }
        /* view-mode selector (earth only) */
        listView.addView(TextView(this).apply {
            text = "VIEW"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terra_grey))
            textSize = 11f
            letterSpacing = 0.18f
            setPadding(dp(4f), dp(8f), 0, dp(6f))
        })
        val currentMode = Wallpapers.viewMode(this)
        val modes = listOf(
            Triple("full",    "Full view",            "Earth from deep space, terminator on the left"),
            Triple("locked",  "Locked to my place",   "Your location centred, globe holds still, Sun sweeps for real day/night"),
            Triple("closeup", "Close-up over my place","Low-orbit oblique (~600 km) looking to the horizon")
        )
        for ((id, title, sub) in modes) {
            val on = id == currentMode
            val card = MaterialCardView(this).apply {
                radius = dp(16f).toFloat()
                setCardBackgroundColor(Color.parseColor("#0C1220"))
                strokeWidth = if (on) dp(2f) else dp(1f)
                strokeColor = ContextCompat.getColor(this@MainActivity, if (on) R.color.terra_blue else R.color.terra_grey_dim)
                cardElevation = 0f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8f) }
                setOnClickListener { selectMode(id) }
            }
            val inner = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            }
            inner.addView(TextView(this).apply {
                text = if (on) "$title   ●" else title
                setTextColor(ContextCompat.getColor(this@MainActivity, if (on) R.color.terra_blue else R.color.terra_white))
                textSize = 15f
            })
            inner.addView(TextView(this).apply {
                text = sub
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terra_grey))
                textSize = 12f
                setPadding(0, dp(2f), 0, 0)
            })
            card.addView(inner)
            listView.addView(card)
        }

        /* teaser for the upcoming collection */
        listView.addView(TextView(this).apply {
            text = getString(R.string.coming_soon)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terra_grey_dim))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(6f), 0, 0)
        })
    }
}
