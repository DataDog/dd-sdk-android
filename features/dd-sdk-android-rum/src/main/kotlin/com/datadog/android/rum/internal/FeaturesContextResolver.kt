/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.internal.FeatureContextKeys

internal class FeaturesContextResolver {

    fun resolveViewHasReplay(datadogContext: DatadogContext, viewId: String): Boolean {
        val sessionReplayContext =
            datadogContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return false

        @Suppress("UNCHECKED_CAST")
        val sessionReplayMetadata = sessionReplayContext[viewId] as? Map<String, Any?>
        return (sessionReplayMetadata?.get(FeatureContextKeys.SESSION_REPLAY_VIEW_HAS_REPLAY) as? Boolean) ?: false
    }

    fun resolveViewRecordsCount(datadogContext: DatadogContext, viewId: String): Long {
        val sessionReplayContext =
            datadogContext.featuresContext[Feature.SESSION_REPLAY_FEATURE_NAME] ?: return 0L

        @Suppress("UNCHECKED_CAST")
        val sessionReplayMetadata = sessionReplayContext[viewId] as? Map<String, Any?>
        return (sessionReplayMetadata?.get(FeatureContextKeys.SESSION_REPLAY_VIEW_RECORDS_COUNT) as? Long) ?: 0L
    }
}
