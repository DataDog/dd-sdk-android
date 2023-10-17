/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.time

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.utils.TimeProvider

internal class SessionReplayTimeProvider(
    private val sdkCore: FeatureSdkCore,
    private val currentTimeProvider: () -> Long =
        { System.currentTimeMillis() }
) : TimeProvider {
    override fun getDeviceTimestamp(): Long {
        return currentTimeProvider() +
            getTimestampOffset()
    }

    override fun getTimestampOffset(): Long {
        val rumFeatureContext = sdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)
        val timestampOffset = rumFeatureContext[RUM_VIEW_TIMESTAMP_OFFSET]
        return if (timestampOffset is Long) timestampOffset else 0L
    }

    companion object {
        // TODO RUMM-0000 Share this property somehow, defined in RumFeature.VIEW_TIMESTAMP_OFFSET_IN_MS_KEY
        const val RUM_VIEW_TIMESTAMP_OFFSET = "view_timestamp_offset"
    }
}
