/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.sessionreplay

import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sessionreplay.ExperimentalSessionReplayApi
import com.datadog.android.sessionreplay.SessionReplayConfiguration
import com.datadog.android.sessionreplay.SessionReplayPrivacy

/**
 * Exercises the experimental composition-tree recording pipeline against the same real
 * ImageButton content [SessionReplayImageButtonsActivity] uses for the legacy pipeline, so both
 * pipelines are verified end-to-end against the same fixture.
 */
internal class SessionReplayCompositionImageButtonsActivity : SessionReplayImageButtonsActivity() {

    @OptIn(ExperimentalSessionReplayApi::class)
    @Suppress("DEPRECATION")
    override fun sessionReplayConfiguration(
        privacy: SessionReplayPrivacy,
        sampleRate: Float
    ): SessionReplayConfiguration {
        return RuntimeConfig.sessionReplayConfigBuilder(sampleRate)
            .setPrivacy(privacy)
            .setCompositionTreeRecordingEnabled(true)
            .build()
    }
}
