/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.internal.rum.RumSessionConstants
import com.datadog.android.internal.rum.rumContext
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext

internal class SessionReplayRumContextProvider(
    private val onRumViewChanged: () -> Unit = {}
) : RumContextProvider, FeatureContextUpdateReceiver {

    @Volatile
    private var rumContext: RumContext? = null

    override fun getRumContext(): SessionReplayRumContext {
        return rumContext.let {
            SessionReplayRumContext(
                applicationId = it?.applicationId ?: NULL_UUID,
                sessionId = it?.session?.id ?: NULL_UUID,
                viewId = it?.viewId ?: NULL_UUID,
                viewTimeOffsetMs = it?.viewTimestampOffset ?: 0L,
                viewUrl = it?.viewUrl
            )
        }
    }

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            val previousViewId = rumContext?.viewId
            val newContext = context.rumContext()
            val newViewId = newContext?.viewId
            rumContext = newContext
            if (newViewId != null && newViewId != NULL_UUID && newViewId != previousViewId) {
                onRumViewChanged()
            }
        }
    }

    companion object {
        const val NULL_UUID = RumSessionConstants.EMPTY_RUM_SESSION_ID
    }
}
