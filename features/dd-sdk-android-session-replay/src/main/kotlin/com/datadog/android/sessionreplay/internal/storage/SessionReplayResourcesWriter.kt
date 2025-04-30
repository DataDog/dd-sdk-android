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
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource
import com.datadog.android.sessionreplay.internal.processor.asBinaryMetadata

internal class SessionReplayResourcesWriter(
    private val sdkCore: FeatureSdkCore
) : ResourcesWriter {
    override fun write(enrichedResource: EnrichedResource) {
        sdkCore.getFeature(SESSION_REPLAY_RESOURCES_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                synchronized(this) {
                    val serializedMetadata = enrichedResource.asBinaryMetadata(datadogContext.rumApplicationId)
                    eventBatchWriter.write(
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

    private val DatadogContext.rumApplicationId: String
        get() = (featuresContext[Feature.RUM_FEATURE_NAME]?.get("application_id") as? String).orEmpty()
}
