package com.terralive.wallpapers

import android.app.Presentation
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.webkit.WebView

class EarthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = TerraEngine()

    inner class TerraEngine : Engine() {

        private var virtualDisplay: VirtualDisplay? = null
        private var presentation: Presentation? = null
        private var webView: WebView? = null

        /* hot-swap the scene when the user picks another wallpaper in the app */
        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Wallpapers.KEY_SELECTED || key == Wallpapers.KEY_LOCK || key == Wallpapers.KEY_LAT || key == Wallpapers.KEY_LON) {
                webView?.loadUrl(Wallpapers.urlFor(this@EarthWallpaperService, Wallpapers.selected(this@EarthWallpaperService)))
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            Wallpapers.registerListener(this@EarthWallpaperService, prefListener)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            release()
            val dm = getSystemService(DISPLAY_SERVICE) as DisplayManager
            virtualDisplay = dm.createVirtualDisplay(
                "terra-live", width, height,
                resources.displayMetrics.densityDpi, holder.surface, 0
            )
            val pres = Presentation(this@EarthWallpaperService, virtualDisplay!!.display)
            pres.window?.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            val wv = WebView(pres.context)
            wv.setBackgroundColor(Color.BLACK)
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
            wv.loadUrl(Wallpapers.urlFor(this@EarthWallpaperService, Wallpapers.selected(this@EarthWallpaperService)))
            pres.setContentView(wv)
            pres.show()
            presentation = pres
            webView = wv
        }

        override fun onVisibilityChanged(visible: Boolean) {
            if (visible) { webView?.onResume(); webView?.resumeTimers() }
            else { webView?.onPause(); webView?.pauseTimers() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            release()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            Wallpapers.unregisterListener(this@EarthWallpaperService, prefListener)
            release()
            super.onDestroy()
        }

        private fun release() {
            presentation?.dismiss(); presentation = null
            virtualDisplay?.release(); virtualDisplay = null
            webView?.destroy(); webView = null
        }
    }
}
