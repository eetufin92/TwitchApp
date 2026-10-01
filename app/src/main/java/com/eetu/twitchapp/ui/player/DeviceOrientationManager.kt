package com.eetu.twitchapp.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager

class DeviceOrientationManager(
    private val activity: Activity,
    private val onOrientationChanged: ((isLandscape: Boolean) -> Unit)? = null
) {
    private var isEnabled = false
    private val handler = Handler(Looper.getMainLooper())
    private var pendingOrientationRunnable: Runnable? = null
    private var lastReportedLandscape: Boolean? = null

    val isTablet: Boolean
        get() = activity.resources.configuration.smallestScreenWidthDp >= 600

    private val isNaturalLandscape: Boolean by lazy {
        val windowManager = activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.rotation ?: Surface.ROTATION_0
        }
        val config = activity.resources.configuration
        ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) &&
                config.screenWidthDp > config.screenHeightDp) ||
                ((rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) &&
                        config.screenWidthDp < config.screenHeightDp)
    }

    private val orientationEventListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_UI) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return

            val isLandscapeAngle: Boolean
            val isPortraitAngle: Boolean

            if (isNaturalLandscape) {
                // For natural landscape devices (many tablets):
                // 0 and 180 are landscape, 90 and 270 are portrait
                isLandscapeAngle = (orientation in 315..360 || orientation in 0..45) || (orientation in 135..225)
                isPortraitAngle = (orientation in 45..135) || (orientation in 225..315)
            } else {
                // For natural portrait devices (standard phones):
                // 90 and 270 are landscape, 0 and 180 are portrait
                isLandscapeAngle = (orientation in 45..135) || (orientation in 225..315)
                isPortraitAngle = (orientation in 315..360 || orientation in 0..45) || (orientation in 135..225)
            }

            val targetLandscape = when {
                isLandscapeAngle -> true
                isPortraitAngle -> false
                else -> null
            }

            if (targetLandscape != null && targetLandscape != lastReportedLandscape) {
                pendingOrientationRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable {
                    lastReportedLandscape = targetLandscape
                    applyOrientation(targetLandscape)
                    onOrientationChanged?.invoke(targetLandscape)
                }
                pendingOrientationRunnable = runnable
                // Fast 30ms debounce: immediately responsive while smoothing single-frame jitter
                handler.postDelayed(runnable, 30)
            }
        }
    }

    fun start() {
        if (!isEnabled && orientationEventListener.canDetectOrientation()) {
            lastReportedLandscape = (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            orientationEventListener.enable()
            isEnabled = true
        }
    }

    fun stop() {
        if (isEnabled) {
            pendingOrientationRunnable?.let { handler.removeCallbacks(it) }
            orientationEventListener.disable()
            isEnabled = false
        }
    }

    fun requestLandscape() {
        pendingOrientationRunnable?.let { handler.removeCallbacks(it) }
        lastReportedLandscape = true
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun requestPortrait() {
        pendingOrientationRunnable?.let { handler.removeCallbacks(it) }
        lastReportedLandscape = false
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }

    fun resetToSensor() {
        pendingOrientationRunnable?.let { handler.removeCallbacks(it) }
        lastReportedLandscape = null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun applyOrientation(isLandscape: Boolean) {
        val currentRequested = activity.requestedOrientation
        if (isLandscape) {
            if (currentRequested != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE &&
                currentRequested != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            ) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        } else {
            if (currentRequested != ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT &&
                currentRequested != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            ) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            }
        }
    }
}
