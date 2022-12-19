/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.log.internal.domain.event.WebViewLogEventSerializer
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import com.datadog.android.v2.webview.internal.storage.WebViewLogsDataWriter
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewLogsFeature {

    internal var dataWriter: DataWriter<JsonObject> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    // region SdkFeature

    fun initialize() {
        dataWriter = createDataWriter()
        initialized.set(true)
    }

    fun stop() {
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    private fun createDataWriter(): DataWriter<JsonObject> {
        return WebViewLogsDataWriter(
            serializer = WebViewLogEventSerializer(),
            internalLogger = internalLogger
        )
    }

    // endregion

    companion object {
        internal const val WEB_LOGS_FEATURE_NAME = "web-logs"
    }
}
