package com.terralive.wallpapers

import android.app.Presentation
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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

        /* ---- motion parallax: accelerometer -> JS at UI rate, only while visible ---- */
        private var sensorManager: SensorManager? = null
        private var sensorOn = false
        private var hiddenAt = 0L
        private val tiltListener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val wv = webView ?: return
                val x = (-e.values[0] / 4.5f).coerceIn(-1f, 1f)
                val y = ((e.values[1] - 7f) / 4.5f).coerceIn(-1f, 1f)
                wv.evaluateJavascript("window._terraTilt&&_terraTilt($x,$y)", null)
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        private fun startTilt() {
            if (sensorOn) return
            if (!Wallpapers.motion(this@EarthWallpaperService)) return
            val sm = getSystemService(SENSOR_SERVICE) as SensorManager
            sensorManager = sm
            val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            sm.registerListener(tiltListener, acc, SensorManager.SENSOR_DELAY_UI)
            sensorOn = true
        }
        private fun stopTilt() {
            if (!sensorOn) return
            sensorManager?.unregisterListener(tiltListener)
            sensorOn = false
        }

        /* hot-swap the scene when the user picks another wallpaper in the app */
        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Wallpapers.KEY_SELECTED || key == Wallpapers.KEY_MODE || key == Wallpapers.KEY_LOCK || key == Wallpapers.KEY_LAT || key == Wallpapers.KEY_LON || key == Wallpapers.KEY_LOOK || key == Wallpapers.KEY_MOTION || key == Wallpapers.KEY_WAKE) {
                if (key == Wallpapers.KEY_MOTION) { stopTilt(); startTilt() }
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
            if (visible) {
                webView?.onResume(); webView?.resumeTimers(); startTilt()
                if (hiddenAt > 0L && System.currentTimeMillis() - hiddenAt > 45000L) {
                    webView?.evaluateJavascript("window._terraWake&&_terraWake()", null)
                }
                hiddenAt = 0L
            }
            else { hiddenAt = System.currentTimeMillis(); stopTilt(); webView?.onPause(); webView?.pauseTimers() }
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
            stopTilt()
            presentation?.dismiss(); presentation = null
            virtualDisplay?.release(); virtualDisplay = null
            webView?.destroy(); webView = null
        }
    }
}
