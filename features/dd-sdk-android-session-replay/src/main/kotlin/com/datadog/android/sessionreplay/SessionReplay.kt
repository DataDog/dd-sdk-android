/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.Datadog
import com.datadog.android.api.SdkCore
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
        val sessionReplayFeature = SessionReplayFeature(
            sdkCore = sdkCore as FeatureSdkCore,
            customEndpointUrl = sessionReplayConfiguration.customEndpointUrl,
            privacy = sessionReplayConfiguration.privacy,
            customMappers = sessionReplayConfiguration.customMappers,
            customOptionSelectorDetectors = sessionReplayConfiguration.customOptionSelectorDetectors,
            sampleRate = sessionReplayConfiguration.sampleRate
        )

        sdkCore.registerFeature(sessionReplayFeature)
    }
}
