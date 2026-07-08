package com.terralive.wallpapers

import android.app.Presentation
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import android.webkit.WebView

/**
 * Live wallpaper engine. The real-time Earth is rendered by a hardware-
 * accelerated WebView (WebGL) projected onto the wallpaper surface via a
 * VirtualDisplay + Presentation. When the app is uninstalled Android
 * automatically removes this wallpaper - nothing is left behind.
 */
class EarthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = TerraEngine()

    inner class TerraEngine : Engine() {

        private var virtualDisplay: VirtualDisplay? = null
        private var presentation: Presentation? = null
        private var webView: WebView? = null

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
            wv.loadUrl("file:///android_asset/earth.html")
            pres.setContentView(wv)
            pres.show()
            presentation = pres
            webView = wv
        }

        override fun onVisibilityChanged(visible: Boolean) {
            // save battery: freeze rendering whenever the wallpaper is hidden
            if (visible) { webView?.onResume(); webView?.resumeTimers() }
            else { webView?.onPause(); webView?.pauseTimers() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            release()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
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
