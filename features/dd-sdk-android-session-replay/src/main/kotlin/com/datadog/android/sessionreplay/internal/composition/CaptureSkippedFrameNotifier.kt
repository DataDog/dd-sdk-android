/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore

internal class CaptureSkippedFrameNotifier(
    private val sdkCore: FeatureSdkCore
) {
    fun notifySkippedFrame() {
        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)?.sendEvent(SKIPPED_FRAME_EVENT)
    }

    private companion object {
        const val TYPE_KEY = "type"
        const val TYPE_VALUE = "sr_skipped_frame"
        val SKIPPED_FRAME_EVENT = mapOf(TYPE_KEY to TYPE_VALUE)
    }
}
