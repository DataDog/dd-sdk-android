/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.EventType
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.internal.processor.asBinaryMetadata

internal class SessionReplayResourcesWriter(
    private val sdkCore: FeatureSdkCore
) : ResourcesWriter {
    override fun write(enrichedResource: EnrichedResource) {
        val feature = sdkCore.getFeature(Feature.SESSION_REPLAY_RESOURCES_FEATURE_NAME)
        // Deliberately kept in (not removed after investigation) at the user's explicit
        // request — see the git history/PR discussion for the "full screen broken images"
        // investigation this was added for. android.util.Log rather than InternalLogger:
        // this is meant to be read straight off Logcat on a real device/app while
        // reproducing the issue, not routed through the SDK's own telemetry/user-facing
        // channels. Traces the last-mile handoff for a resource whose client-side
        // enqueueing (see ResourceItemCreationHandler's own logging) already succeeded —
        // if `feature` is null here, SESSION_REPLAY_RESOURCES_FEATURE_NAME was never
        // registered/is gone, and this whole write silently no-ops (the `?.` below skips
        // everything). If `feature` is non-null but the withWriteContext callback below
        // never logs, `withWriteContext` itself silently short-circuited (e.g.
        // SdkFeature's own `coreFeature.initialized` check) — also a silent no-op today.
        android.util.Log.d(
            "DD_SessionReplay",
            "[SessionReplayResourcesWriter] write: nodeId=${enrichedResource.filename} " +
                "resourceFeature=${feature != null}"
        )
        feature
            ?.withWriteContext(
                withFeatureContexts = setOf(Feature.RUM_FEATURE_NAME)
            ) { datadogContext, writeScope ->
                android.util.Log.d(
                    "DD_SessionReplay",
                    "[SessionReplayResourcesWriter] write: nodeId=${enrichedResource.filename} " +
                        "withWriteContext callback invoked, rumApplicationId=${datadogContext.rumApplicationId}"
                )
                writeScope {
                    synchronized(this@SessionReplayResourcesWriter) {
                        val serializedMetadata = enrichedResource.asBinaryMetadata(datadogContext.rumApplicationId)
                        android.util.Log.d(
                            "DD_SessionReplay",
                            "[SessionReplayResourcesWriter] write: nodeId=${enrichedResource.filename} " +
                                "writeScope invoked, resourceBytes=${enrichedResource.resource.size}"
                        )
                        it.write(
                            event = RawBatchEvent(
                                data = enrichedResource.resource,
                                metadata = serializedMetadata
                            ),
                            batchMetadata = null,
                            eventType = EventType.DEFAULT
                        )
                    }
                }
            }
    }

    private val DatadogContext.rumApplicationId: String
        get() = (featuresContext[Feature.RUM_FEATURE_NAME]?.get("application_id") as? String).orEmpty()
}
