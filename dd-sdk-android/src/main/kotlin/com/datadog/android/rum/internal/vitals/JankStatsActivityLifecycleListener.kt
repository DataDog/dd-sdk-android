/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.metrics.performance.FrameData
import androidx.metrics.performance.JankStats
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.v2.api.InternalLogger
import java.util.concurrent.TimeUnit

/**
 * Utility class listening to frame rate information.
 */
internal class JankStatsActivityLifecycleListener(
    private val vitalObserver: VitalObserver,
    private val buildSdkVersionProvider: BuildSdkVersionProvider,
    private val internalLogger: InternalLogger
) : ActivityLifecycleCallbacks, JankStats.OnFrameListener {

    private var displayRefreshRateScale: Float = 1.0f

    // region ActivityLifecycleCallbacks

    @Suppress("DEPRECATION")
    @SuppressLint("NewApi")
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        try {
            val display = if (buildSdkVersionProvider.version() >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                (activity.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
            val displayRefreshRate = display?.refreshRate ?: STANDARD_FPS.toFloat()
            displayRefreshRateScale = STANDARD_FPS / displayRefreshRate
            JankStats.createAndTrack(activity.window, this)
        } catch (e: IllegalStateException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                "Unable to attach JankStats to the current window.",
                e
            )
        }
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
    }

    // endregion

    // region JankStats.OnFrameListener

    override fun onFrame(volatileFrameData: FrameData) {
        val durationNs = volatileFrameData.frameDurationUiNanos
        if (durationNs > 0.0) {
            val frameRate = (ONE_SECOND_NS / durationNs) * displayRefreshRateScale
            if (frameRate in VALID_FPS_RANGE) {
                vitalObserver.onNewSample(frameRate)
            }
        }
    }

    // endregion

    companion object {
        val ONE_SECOND_NS: Double = TimeUnit.SECONDS.toNanos(1).toDouble()

        private const val MIN_FPS: Double = 1.0
        private const val MAX_FPS: Double = 240.0
        private const val STANDARD_FPS: Float = 60.0f
        val VALID_FPS_RANGE = MIN_FPS.rangeTo(MAX_FPS)
    }
}
