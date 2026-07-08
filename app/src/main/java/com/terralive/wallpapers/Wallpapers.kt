package com.terralive.wallpapers

import android.content.Context

data class WallpaperInfo(val id: String, val title: String, val subtitle: String)

/**
 * The wallpaper catalog. To add a new live wallpaper later:
 *   1. drop its scene at  assets/wallpapers/<id>/index.html
 *   2. add one WallpaperInfo line below
 * The selector UI and the wallpaper service pick it up automatically.
 */
object Wallpapers {
    private const val PREFS = "terra_prefs"
    const val KEY_SELECTED = "selected_wallpaper"
    private const val DEFAULT = "earth"

    val ALL = listOf(
        WallpaperInfo(
            "earth",
            "The Planet",
            "Earth from orbit in real time — live clouds, true night sky, real Moon"
        ),
        WallpaperInfo(
            "clouds",
            "Drifting Clouds",
            "Fluffy cumulus at true drift speed — real 24-hour sky, stars and moon at night"
        )
        // coming soon: more places on Earth — oceans, mountains, rain...
    )

    fun assetUrl(id: String) = "file:///android_asset/wallpapers/$id/index.html"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(ctx: Context): String =
        prefs(ctx).getString(KEY_SELECTED, DEFAULT) ?: DEFAULT

    fun select(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_SELECTED, id).apply()
    }

    fun registerListener(ctx: Context, l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(ctx).registerOnSharedPreferenceChangeListener(l)
    }

    fun unregisterListener(ctx: Context, l: android.content.SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs(ctx).unregisterOnSharedPreferenceChangeListener(l)
    }
}
