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
    const val KEY_LOOK = "look_variant"      // Int 0..5 (testing)
    const val KEY_MOTION = "motion_parallax"  // Boolean, default true
    const val KEY_WAKE = "wake_spin"          // Boolean (retired)
    const val KEY_ROT = "slow_rotation"       // Boolean, default false
    const val KEY_SKY_SATS = "sky_satellites"   // Boolean, default true
    const val KEY_SKY_PLANES = "sky_aircraft"    // Boolean, default true
    private const val DEFAULT = "earth"

    val ALL = listOf(
        WallpaperInfo(
            "earth",
            "The Planet",
            "Earth from orbit in real time — live clouds, true night sky, real Moon"
        ),
        WallpaperInfo(
            "sky",
            "Sky View",
            "The sky above you, live: real clouds, rain and snow, planes, the ISS, a night with zero light pollution"
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
        if (id == "sky") {
            val pS = prefs(ctx)
            val motS = if (pS.getBoolean(KEY_MOTION, true)) 1 else 0
            val latS = pS.getFloat(KEY_LAT, Float.NaN)
            val lonS = pS.getFloat(KEY_LON, Float.NaN)
            val qsS = StringBuilder("?look=3&motion=").append(motS).append("&mode=sky")
            if (!latS.isNaN() && !lonS.isNaN()) qsS.append("&lat=").append(latS).append("&lon=").append(lonS)
            qsS.append("&sats=").append(if (pS.getBoolean(KEY_SKY_SATS, true)) 1 else 0)
            qsS.append("&planes=").append(if (pS.getBoolean(KEY_SKY_PLANES, true)) 1 else 0)
            return assetUrl("earth") + qsS.toString()
        }
        val base = assetUrl(id)
        if (id != "earth") return base
        val p = prefs(ctx)
        val mode0 = p.getString(KEY_MODE, "full") ?: "full"
        val mode1 = if (mode0 == "locked") "closeup" else mode0
        val mode = if (mode1 == "sky") "full" else mode1   /* sky lives as its own wallpaper now */
        val look = p.getInt(KEY_LOOK, 3)
        val mot = if (p.getBoolean(KEY_MOTION, true)) 1 else 0
        val rt = if (p.getBoolean(KEY_ROT, false)) 1 else 0
        val lat = p.getFloat(KEY_LAT, Float.NaN)
        val lon = p.getFloat(KEY_LON, Float.NaN)
        val qs = StringBuilder("?look=").append(look).append("&motion=").append(mot).append("&rot=").append(rt)
        if (mode != "full" && !lat.isNaN() && !lon.isNaN())
            qs.append("&mode=").append(mode).append("&lat=").append(lat).append("&lon=").append(lon)
        return base + qs.toString()
    }

    fun motion(ctx: Context) = prefs(ctx).getBoolean(KEY_MOTION, true)

    fun wake(ctx: Context) = prefs(ctx).getBoolean(KEY_WAKE, true)

    fun viewMode(ctx: Context) = prefs(ctx).getString(KEY_MODE, "full") ?: "full"

    fun setMode(ctx: Context, mode: String, lat: Double? = null, lon: Double? = null) {
        val e = prefs(ctx).edit().putString(KEY_MODE, mode)
        if (lat != null && lon != null) { e.putFloat(KEY_LAT, lat.toFloat()); e.putFloat(KEY_LON, lon.toFloat()) }
        e.apply()
    }

    fun setLocation(ctx: Context, lat: Double, lon: Double) {
        prefs(ctx).edit().putFloat(KEY_LAT, lat.toFloat()).putFloat(KEY_LON, lon.toFloat()).apply()
    }

    fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
