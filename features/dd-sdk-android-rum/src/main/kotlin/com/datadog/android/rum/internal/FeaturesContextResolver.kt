/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.rum.internal.domain.scope.SESSION_REPLAY_CONTEXT_NAMES

internal class FeaturesContextResolver {

    fun resolveViewHasReplay(datadogContext: DatadogContext, viewId: String): Boolean {
        return resolveViewMetadata(datadogContext, viewId, HAS_REPLAY_KEY) as? Boolean ?: false
    }

    fun resolveViewRecordsCount(datadogContext: DatadogContext, viewId: String): Long {
        return resolveViewMetadata(datadogContext, viewId, VIEW_RECORDS_COUNT_KEY) as? Long ?: 0L
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveViewMetadata(datadogContext: DatadogContext, viewId: String, key: String): Any? {
        return SESSION_REPLAY_CONTEXT_NAMES.firstNotNullOfOrNull {
            val sessionReplayMetadata = datadogContext.featuresContext[it]?.get(viewId) as? Map<String, Any?>
            sessionReplayMetadata?.get(key)
        }
    }

    companion object {
        internal const val HAS_REPLAY_KEY = "has_replay"
        internal const val VIEW_RECORDS_COUNT_KEY = "records_count"

        /**
         * The feature cross-platform Session Replay registers under, so that it doesn't replace
         * native Session Replay in hybrid apps. RUM reads Session Replay state from its context when
         * native Session Replay publishes none.
         *
         * Must match FlutterSessionReplayFeature.FLUTTER_SESSION_REPLAY_FEATURE_NAME in dd-sdk-flutter.
         */
        internal const val CROSS_PLATFORM_SESSION_REPLAY_FEATURE_NAME = "flutter-session-replay"
    }
}
