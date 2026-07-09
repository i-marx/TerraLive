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
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {

    private lateinit var listView: LinearLayout

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) applyLocationLock()
            else {
                Wallpapers.setLock(this, false)
                Toast.makeText(this, "Location permission is needed to lock the view to your place", Toast.LENGTH_LONG).show()
                renderList()
            }
        }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun applyLocationLock() {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val loc = try {
            lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) { null }
        if (loc != null) {
            Wallpapers.setLock(this, true, loc.latitude, loc.longitude)
        } else {
            // no fix yet — enable lock; wallpaper falls back to default until a fix exists
            Wallpapers.setLock(this, true)
            Toast.makeText(this, "Getting your location — move outside or open Maps once, then re-toggle", Toast.LENGTH_LONG).show()
        }
        renderList()
    }

    private fun toggleLocationLock(on: Boolean) {
        if (on) {
            if (hasLocationPermission()) applyLocationLock()
            else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            Wallpapers.setLock(this, false)
            renderList()
        }
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
        /* location-lock option (earth only) */
        val lockCard = MaterialCardView(this).apply {
            radius = dp(16f).toFloat()
            setCardBackgroundColor(Color.parseColor("#0C1220"))
            strokeWidth = dp(1f)
            strokeColor = ContextCompat.getColor(this@MainActivity, R.color.terra_grey_dim)
            cardElevation = 0f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4f); bottomMargin = dp(12f) }
        }
        val lockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18f), dp(14f), dp(14f), dp(14f))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val lockText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        lockText.addView(TextView(this).apply {
            text = "Lock to my location"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terra_white))
            textSize = 16f
        })
        lockText.addView(TextView(this).apply {
            text = "Centre Earth on your place — the globe holds still and the Sun moves for real day and night"
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.terra_grey))
            textSize = 12f
            setPadding(0, dp(3f), 0, 0)
        })
        lockRow.addView(lockText)
        lockRow.addView(MaterialSwitch(this).apply {
            isChecked = Wallpapers.lockEnabled(this@MainActivity)
            setOnClickListener { toggleLocationLock(isChecked) }
        })
        lockCard.addView(lockRow)
        listView.addView(lockCard)

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
