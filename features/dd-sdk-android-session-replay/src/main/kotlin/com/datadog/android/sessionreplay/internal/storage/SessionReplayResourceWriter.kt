/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.storage

import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.RawBatchEvent
import com.datadog.android.sessionreplay.internal.ResourceWriter
import com.datadog.android.sessionreplay.internal.ResourcesFeature.Companion.SESSION_REPLAY_RESOURCES_FEATURE_NAME
import com.datadog.android.sessionreplay.internal.processor.EnrichedResource

internal class SessionReplayResourceWriter(
    private val sdkCore: FeatureSdkCore
) : ResourceWriter {
    override fun write(enrichedResource: EnrichedResource) {
        sdkCore.getFeature(SESSION_REPLAY_RESOURCES_FEATURE_NAME)?.withWriteContext() { _, eventBatchWriter ->
            synchronized(this) {
                val serializedMetadata = enrichedResource.metadata.toString().toByteArray(Charsets.UTF_8)
                @Suppress("ThreadSafety") // called from the worker thread
                eventBatchWriter.write(
                    event = RawBatchEvent(
                        data = enrichedResource.resource,
                        metadata = serializedMetadata
                    ),
                    batchMetadata = null
                )
            }
        }
    }
}
