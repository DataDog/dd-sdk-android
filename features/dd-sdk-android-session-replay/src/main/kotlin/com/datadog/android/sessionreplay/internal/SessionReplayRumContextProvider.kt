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

internal class SessionReplayRumContextProvider(
    private val onRumViewChanged: () -> Unit = {}
) : RumContextProvider, FeatureContextUpdateReceiver {

    @Volatile
    private var rumContext = emptyMap<String, Any?>()

    override fun getRumContext(): SessionReplayRumContext {
        return rumContext.let {
            SessionReplayRumContext(
                applicationId = it[RUM_APPLICATION_ID_CONTEXT_KEY] as? String ?: NULL_UUID,
                sessionId = it[RUM_SESSION_ID_CONTEXT_KEY] as? String ?: NULL_UUID,
                viewId = it[RUM_VIEW_ID_CONTEXT_KEY] as? String ?: NULL_UUID,
                // TODO RUM-3785 Share this property somehow, defined in RumFeature.VIEW_TIMESTAMP_OFFSET_IN_MS_KEY
                viewTimeOffsetMs = it[RUM_VIEW_TIME_OFFSET_CONTEXT_KEY] as? Long ?: 0L,
                viewUrl = it[RUM_VIEW_URL_CONTEXT_KEY] as? String
            )
        }
    }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            val previousViewId = rumContext[RUM_VIEW_ID_CONTEXT_KEY] as? String
            val newViewId = context[RUM_VIEW_ID_CONTEXT_KEY] as? String
            if (newViewId != null && newViewId != NULL_UUID && newViewId != previousViewId) {
                onRumViewChanged()
            }
            rumContext = context
        }
    }

    companion object {
        val NULL_UUID = UUID(0, 0).toString()

        // Mirrors feature-context keys from RumContext in the RUM module.
        internal const val RUM_APPLICATION_ID_CONTEXT_KEY = "application_id"
        internal const val RUM_SESSION_ID_CONTEXT_KEY = "session_id"
        internal const val RUM_VIEW_ID_CONTEXT_KEY = "view_id"
        internal const val RUM_VIEW_TIME_OFFSET_CONTEXT_KEY = "view_timestamp_offset"
        internal const val RUM_VIEW_URL_CONTEXT_KEY = "view_url"
    }
}
