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
    const val KEY_MODE = "view_mode"         // "full" | "locked" | "closeup"
    const val KEY_LOCK = "lock_location"     // legacy Boolean (kept for compatibility)
    const val KEY_LAT = "user_lat"           // Float
    const val KEY_LON = "user_lon"           // Float
    private const val DEFAULT = "earth"

    val ALL = listOf(
        WallpaperInfo(
            "earth",
            "The Planet",
            "Earth from orbit in real time — live clouds, true night sky, real Moon"
        )
        /* hidden until refined — flip this back on in an update:
        ,WallpaperInfo(
            "clouds",
            "Drifting Clouds",
            "Volumetric ray-marched cumulus at true drift speed — real 24-hour sky, stars and moon at night"
        ) */
        // coming soon: more places on Earth — oceans, mountains, rain...
    )

    fun assetUrl(id: String) = "file:///android_asset/wallpapers/$id/index.html"

    /* Asset URL with view-mode + location query params (earth only). */
    fun urlFor(ctx: Context, id: String): String {
        val base = assetUrl(id)
        if (id != "earth") return base
        val p = prefs(ctx)
        val mode = p.getString(KEY_MODE, "full") ?: "full"
        if (mode == "full") return base
        val lat = p.getFloat(KEY_LAT, Float.NaN)
        val lon = p.getFloat(KEY_LON, Float.NaN)
        if (lat.isNaN() || lon.isNaN()) return base
        return "$base?mode=$mode&lat=$lat&lon=$lon"
    }

    fun viewMode(ctx: Context) = prefs(ctx).getString(KEY_MODE, "full") ?: "full"

    fun setMode(ctx: Context, mode: String, lat: Double? = null, lon: Double? = null) {
        val e = prefs(ctx).edit().putString(KEY_MODE, mode)
        if (lat != null && lon != null) { e.putFloat(KEY_LAT, lat.toFloat()); e.putFloat(KEY_LON, lon.toFloat()) }
        e.apply()
    }

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
