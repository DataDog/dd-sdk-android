/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import java.util.UUID

internal class SessionReplayRumContextProvider(
    private val sdkCore: FeatureSdkCore
) : RumContextProvider {
    override fun getRumContext(): SessionReplayRumContext {
        val rumContext = sdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)
        return SessionReplayRumContext(
            applicationId = rumContext["application_id"] as? String ?: NULL_UUID,
            sessionId = rumContext["session_id"] as? String ?: NULL_UUID,
            viewId = rumContext["view_id"] as? String ?: NULL_UUID
        )
    }

    companion object {
        val NULL_UUID = UUID(0, 0).toString()
    }
}
