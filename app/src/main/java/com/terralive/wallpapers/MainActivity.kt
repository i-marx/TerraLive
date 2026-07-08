package com.terralive.wallpapers

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var listView: LinearLayout

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
