/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

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
     * @param startRecordingImmediately whether to start recording immediately or wait for manual start.
     * If not provided, the default is to start immediately.
     */
    @JvmOverloads
    @JvmStatic
    fun enable(
        sessionReplayConfiguration: SessionReplayConfiguration,
        sdkCore: SdkCore = Datadog.getInstance(),
        startRecordingImmediately: Boolean = true
    ) {
        val sessionReplayFeature = SessionReplayFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            customEndpointUrl = sessionReplayConfiguration.customEndpointUrl,
            privacy = sessionReplayConfiguration.privacy,
            imagePrivacy = sessionReplayConfiguration.imagePrivacy,
            customMappers = sessionReplayConfiguration.customMappers,
            customOptionSelectorDetectors = sessionReplayConfiguration.customOptionSelectorDetectors,
            sampleRate = sessionReplayConfiguration.sampleRate,
            startRecordingImmediately = startRecordingImmediately
        )

        sdkCore.registerFeature(sessionReplayFeature)
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

        sessionReplayFeature?.startRecording()
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

        sessionReplayFeature?.stopRecording()
    }
}
