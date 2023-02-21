/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log.storage

import androidx.annotation.WorkerThread
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.serializeToByteArray
import com.datadog.android.v2.api.EventBatchWriter
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.core.storage.DataWriter
import com.google.gson.JsonObject

internal class WebViewLogsDataWriter(
    private val serializer: Serializer<JsonObject>,
    private val internalLogger: InternalLogger
) : DataWriter<JsonObject> {

    @WorkerThread
    override fun write(writer: EventBatchWriter, element: JsonObject): Boolean {
        val serialized = serializer.serializeToByteArray(element, internalLogger) ?: return false
        return synchronized(this) { writer.write(serialized, null) }
    }
}
