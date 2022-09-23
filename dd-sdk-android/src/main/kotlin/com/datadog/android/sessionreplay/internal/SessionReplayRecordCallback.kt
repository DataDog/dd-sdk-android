/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.sessionreplay.RecordCallback
import com.datadog.android.v2.api.SDKCore

internal class SessionReplayRecordCallback(private val datadogCore: SDKCore) : RecordCallback {
    override fun onStartRecording() {
        updateRecording(true)
    }

    override fun onStopRecording() {
        updateRecording(false)
    }

    private fun updateRecording(isRecording: Boolean) {
        val featureContext = mapOf(SessionReplayFeature.IS_RECORDING_CONTEXT_KEY to isRecording)
        datadogCore.setFeatureContext(
            SessionReplayFeature.SESSION_REPLAY_FEATURE_NAME,
            featureContext
        )
    }
}
