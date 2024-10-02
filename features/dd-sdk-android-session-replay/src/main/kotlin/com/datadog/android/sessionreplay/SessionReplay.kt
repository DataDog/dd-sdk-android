/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.os.Handler
import android.os.Looper
import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature.Companion.SESSION_REPLAY_FEATURE_NAME
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.SessionReplayFeature

/**
 * An entry point to Datadog Session Replay feature.
 */
object SessionReplay {

    /**
     * Enables a SessionReplay feature based on the configuration provided.
     *
     * @param sessionReplayConfiguration Configuration to use for the feature.
     * @param sdkCore SDK instance to register feature in. If not provided, default SDK instance
     * will be used.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        sessionReplayConfiguration: SessionReplayConfiguration,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        enable(sessionReplayConfiguration, Handler(Looper.getMainLooper()), sdkCore)
    }

    internal fun enable(
        sessionReplayConfiguration: SessionReplayConfiguration,
        uiHandler: Handler,
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val featureSdkCore = sdkCore as FeatureSdkCore
        sessionReplayConfiguration.systemRequirementsConfiguration.let {
            it.runIfRequirementsMet(
                sdkCore = sdkCore,
                uiHandler = uiHandler,
                featureSdkCore.internalLogger
            ) {
                val sessionReplayFeature = SessionReplayFeature(
                    sdkCore = featureSdkCore,
                    customEndpointUrl = sessionReplayConfiguration.customEndpointUrl,
                    privacy = sessionReplayConfiguration.privacy,
                    imagePrivacy = sessionReplayConfiguration.imagePrivacy,
                    touchPrivacy = sessionReplayConfiguration.touchPrivacy,
                    textAndInputPrivacy = sessionReplayConfiguration.textAndInputPrivacy,
                    customMappers = sessionReplayConfiguration.customMappers,
                    customOptionSelectorDetectors = sessionReplayConfiguration.customOptionSelectorDetectors,
                    sampleRate = sessionReplayConfiguration.sampleRate,
                    startRecordingImmediately = sessionReplayConfiguration.startRecordingImmediately,
                    dynamicOptimizationEnabled = sessionReplayConfiguration.dynamicOptimizationEnabled
                )
                sdkCore.registerFeature(sessionReplayFeature)
            }
        }
    }

    /**
     * Start recording session replay data.
     * @param sdkCore SDK instance to get the feature from. If not provided, default SDK instance
     * will be used.
     */
    fun startRecording(
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val sessionReplayFeature = (sdkCore as? FeatureSdkCore)
            ?.getFeature(SESSION_REPLAY_FEATURE_NAME)?.let {
                it.unwrap() as? SessionReplayFeature
            }

        sessionReplayFeature?.manuallyStartRecording()
    }

    /**
     * Stop recording session replay data.
     * @param sdkCore SDK instance to get the feature from. If not provided, default SDK instance
     * will be used.
     */
    fun stopRecording(
        sdkCore: SdkCore = Datadog.getInstance()
    ) {
        val sessionReplayFeature = (sdkCore as? FeatureSdkCore)
            ?.getFeature(SESSION_REPLAY_FEATURE_NAME)?.let {
                it.unwrap() as? SessionReplayFeature
            }

        sessionReplayFeature?.manuallyStopRecording()
    }
}
