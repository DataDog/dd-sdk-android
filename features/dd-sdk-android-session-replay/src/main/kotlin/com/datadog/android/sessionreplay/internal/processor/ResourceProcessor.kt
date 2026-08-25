/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import com.datadog.android.sessionreplay.internal.resources.ResourceDataStoreManager
import com.datadog.android.sessionreplay.internal.storage.ResourcesWriter
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal fun interface ResourceProcessor {
    fun process(identifier: String, data: ByteArray, mimeType: String?)
}

internal class DefaultResourceProcessor(
    private val resourceDataStoreManager: ResourceDataStoreManager,
    private val resourcesWriter: ResourcesWriter
) : ResourceProcessor {

    override fun process(identifier: String, data: ByteArray, mimeType: String?) {
        if (!resourceDataStoreManager.markResourceAsSentIfNew(identifier)) {
            return
        }

        resourcesWriter.write(
            EnrichedResource(
                resource = data,
                filename = identifier,
                mimeType = mimeType
            )
        )
    }
}
