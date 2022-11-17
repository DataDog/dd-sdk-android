/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal

import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.sessionreplay.utils.RumContextProvider
import com.datadog.android.sessionreplay.utils.SessionReplayRumContext
import com.datadog.android.v2.api.SdkCore

internal class SessionReplayRumContextProvider(
    private val sdkCore: SdkCore
) : RumContextProvider {
    override fun getRumContext(): SessionReplayRumContext {
        val rumContext = sdkCore.getFeatureContext(RumFeature.RUM_FEATURE_NAME)
        return SessionReplayRumContext(
            applicationId = rumContext["application_id"] as? String ?: RumContext.NULL_UUID,
            sessionId = rumContext["session_id"] as? String ?: RumContext.NULL_UUID,
            viewId = rumContext["view_id"] as? String ?: RumContext.NULL_UUID
        )
    }
}
