/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import java.util.UUID

internal class SessionReplayRumContextProvider : RumContextProvider, FeatureContextUpdateReceiver {

    @Volatile
    private var rumContext = emptyMap<String, Any?>()

    override fun getRumContext(): SessionReplayRumContext {
        return rumContext.let {
            SessionReplayRumContext(
                applicationId = it["application_id"] as? String ?: NULL_UUID,
                sessionId = it["session_id"] as? String ?: NULL_UUID,
                viewId = it["view_id"] as? String ?: NULL_UUID,
                // TODO RUM-3785 Share this property somehow, defined in RumFeature.VIEW_TIMESTAMP_OFFSET_IN_MS_KEY
                viewTimeOffsetMs = it["view_timestamp_offset"] as? Long ?: 0L
            )
        }
    }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            rumContext = context
        }
    }

    companion object {
        val NULL_UUID = UUID(0, 0).toString()
    }
}
