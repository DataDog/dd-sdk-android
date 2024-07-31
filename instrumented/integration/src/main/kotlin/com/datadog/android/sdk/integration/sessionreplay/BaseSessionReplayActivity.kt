/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.Datadog
import com.datadog.android.rum.Rum
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.utils.getForgeSeed
import com.datadog.android.sdk.utils.getSessionReplayImagePrivacy
import com.datadog.android.sdk.utils.getSessionReplayPrivacy
import com.datadog.android.sdk.utils.getSrSampleRate
import com.datadog.android.sdk.utils.getTrackingConsent
import com.datadog.android.sessionreplay.ImagePrivacy
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
        val sessionReplayImagePrivacy = intent.getSessionReplayImagePrivacy()
        val sessionReplaySampleRate = intent.getSrSampleRate()
        Datadog.setVerbosity(Log.VERBOSE)
        // make sure the previous instance is stopped
        Datadog.stopInstance()
        val sdkCore = Datadog.initialize(this, config, trackingConsent)
        checkNotNull(sdkCore)
        val featureActivations = mutableListOf(
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
                    privacy = sessionReplayPrivacy,
                    sampleRate = sessionReplaySampleRate,
                    imagePrivacy = sessionReplayImagePrivacy
                )
                SessionReplay.enable(sessionReplayConfig, sdkCore)
            }
        )
        featureActivations.shuffled(Random(intent.getForgeSeed()))
            .forEach { it() }
    }

    open fun sessionReplayConfiguration(
        privacy: SessionReplayPrivacy,
        sampleRate: Float,
        imagePrivacy: ImagePrivacy
    ): SessionReplayConfiguration {
        return RuntimeConfig.sessionReplayConfigBuilder(sampleRate)
            .setPrivacy(privacy)
            .setImagePrivacy(imagePrivacy)
            .build()
    }
}
