/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.content.Context
import com.datadog.android.core.internal.utils.internalLogger
import com.datadog.android.log.internal.domain.event.WebViewLogEventSerializer
import com.datadog.android.v2.api.FeatureStorageConfiguration
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.api.SdkCore
import com.datadog.android.v2.api.StorageBackedFeature
import com.datadog.android.v2.core.internal.storage.DataWriter
import com.datadog.android.v2.core.internal.storage.NoOpDataWriter
import com.datadog.android.v2.log.internal.net.LogsRequestFactory
import com.datadog.android.v2.webview.internal.storage.WebViewLogsDataWriter
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewLogsFeature(
    endpointUrl: String
) : StorageBackedFeature {

    internal var dataWriter: DataWriter<JsonObject> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = WEB_LOGS_FEATURE_NAME
    override fun onInitialize(sdkCore: SdkCore, appContext: Context) {
        dataWriter = createDataWriter()
        initialized.set(true)
    }

    override val requestFactory: RequestFactory = LogsRequestFactory(endpointUrl)
    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    // endregion

    private fun createDataWriter(): DataWriter<JsonObject> {
        return WebViewLogsDataWriter(
            serializer = WebViewLogEventSerializer(),
            internalLogger = internalLogger
        )
    }

    companion object {
        internal const val WEB_LOGS_FEATURE_NAME = "web-logs"
    }
}
