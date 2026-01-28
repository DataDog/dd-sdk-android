/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.utils

import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.internal.ComponentIdManager
import com.datadog.android.internal.NoOpComponentIdManager

/**
 * Retrieves [ComponentIdManager] from RUM's feature context, or [NoOpComponentIdManager] if unavailable.
 */
internal fun FeatureSdkCore.getComponentIdManager(): ComponentIdManager {
    val rumContext = getFeatureContext(Feature.RUM_FEATURE_NAME)
    return rumContext[ComponentIdManager.FEATURE_CONTEXT_KEY] as? ComponentIdManager
        ?: NoOpComponentIdManager()
}
