/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.app.Activity
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.rum.Rum
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.R
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getForgeSeed
import com.datadog.android.sdk.utils.getSessionReplayPrivacy
import com.datadog.android.sdk.utils.getSrSampleRate
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.sessionreplay.SessionReplay
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import java.util.Random

internal abstract class BaseSessionReplayActivity : AppCompatActivity() {
    @Suppress("CheckInternal")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = RuntimeConfig.configBuilder().build()
        val trackingConsent = intent.getTrackingConsent()
        val sessionReplayPrivacy = intent.getSessionReplayPrivacy()
        val sessionReplaySampleRate = intent.getSrSampleRate()
        Datadog.setVerbosity(Log.VERBOSE)
        val sdkCore = Datadog.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
            // we will use a large long task threshold to make sure we will not have LongTask events
            // noise in our integration tests.
            {
                val rumConfig = RuntimeConfig.rumConfigBuilder()
                    .trackUserInteractions()
                    .trackLongTasks(RuntimeConfig.LONG_TASK_LARGE_THRESHOLD)
                    .useViewTrackingStrategy(ActivityViewTrackingStrategy(true))
                    .build()
                Rum.enable(rumConfig, sdkCore)
            },
            {
                val sessionReplayConfig = sessionReplayConfiguration(
                    sessionReplayPrivacy,
                    sessionReplaySampleRate
                )
                SessionReplay.enable(sessionReplayConfig, sdkCore)
            }
        )
        featureActivations.shuffled(Random(intent.getForgeSeed()))
            .forEach { it() }
    }

    open fun sessionReplayConfiguration(privacy: SessionReplayPrivacy, sampleRate: Float):
        SessionReplayConfiguration =
        RuntimeConfig.sessionReplayConfigBuilder(sampleRate)
            .setPrivacy(privacy)
            .build()

    @Suppress("DEPRECATION")
    open fun resolveScreenDimensions(activity: Activity): Pair<Long, Long> {
        val displayMetrics = activity.resources.displayMetrics
        val screenDensity = displayMetrics.density
        val screenHeight: Long
        val screenWidth: Long
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val currentWindowMetrics = windowManager.currentWindowMetrics
            val screenBounds = currentWindowMetrics.bounds
            screenHeight = (screenBounds.bottom - screenBounds.top).toLong()
                .densityNormalized(screenDensity)
            screenWidth = (screenBounds.right - screenBounds.left).toLong()
                .densityNormalized(screenDensity)
        } else {
            val size = Point()
            windowManager.defaultDisplay.getSize(size)
            screenHeight = size.y.toLong().densityNormalized(screenDensity)
            screenWidth = size.x.toLong().densityNormalized(screenDensity)
        }
        return screenWidth to screenHeight
    }
}
