/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.processor

import androidx.annotation.MainThread
import com.datadog.android.sessionreplay.SessionReplayInternalResourceQueue
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueHandler

internal class ResourceQueueImpl(
    private val internalHandler: RecordedDataQueueHandler
) : SessionReplayInternalResourceQueue {
    @MainThread
    override fun addResourceItem(identifier: String, resourceData: ByteArray, mimeType: String?) {
        internalHandler.addResourceItem(identifier, resourceData, mimeType)
    }
}
