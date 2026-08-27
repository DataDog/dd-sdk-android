/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.rum

import com.datadog.android.internal.FeatureContextKeys

/**
 * Shared sentinel values for the RUM session state exposed via the features context.
 */
object RumSessionConstants {
    /**
     * Sentinel value the RUM session id takes before the first RUM session has been created
     * (e.g. SDK initialised in background). Consumers should skip processing when the session
     * ID equals this value.
     */
    const val EMPTY_RUM_SESSION_ID: String = "00000000-0000-0000-0000-000000000000"

    /**
     * The RUM session is being tracked (i.e. it was sampled in). Exposed via the features
     * context under [FeatureContextKeys.RUM_SESSION_STATE].
     */
    const val SESSION_STATE_TRACKED: String = "TRACKED"

    /**
     * The RUM session is not being tracked (i.e. it was sampled out). Exposed via the
     * features context under [FeatureContextKeys.RUM_SESSION_STATE].
     */
    const val SESSION_STATE_NOT_TRACKED: String = "NOT_TRACKED"

    /**
     * The RUM session has expired and is awaiting renewal. Exposed via the features context
     * under [FeatureContextKeys.RUM_SESSION_STATE].
     */
    const val SESSION_STATE_EXPIRED: String = "EXPIRED"
}
