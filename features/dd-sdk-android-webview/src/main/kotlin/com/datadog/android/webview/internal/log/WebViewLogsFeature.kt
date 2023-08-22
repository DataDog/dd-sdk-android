/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.content.Context
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.webview.internal.storage.NoOpDataWriter
import com.datadog.android.webview.internal.storage.WebViewDataWriter
import com.datadog.android.webview.internal.storage.WebViewEventSerializer
import com.google.gson.JsonObject
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewLogsFeature(
    private val sdkCore: FeatureSdkCore,
    override val requestFactory: RequestFactory
) : StorageBackedFeature {

    internal var dataWriter: DataWriter<JsonObject> = NoOpDataWriter()
    internal val initialized = AtomicBoolean(false)

    // region Feature

    override val name: String = WEB_LOGS_FEATURE_NAME
    override fun onInitialize(appContext: Context) {
        dataWriter = createDataWriter(sdkCore.internalLogger)
        initialized.set(true)
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        dataWriter = NoOpDataWriter()
        initialized.set(false)
    }

    // endregion

    private fun createDataWriter(internalLogger: InternalLogger): DataWriter<JsonObject> {
        return WebViewDataWriter(
            serializer = WebViewEventSerializer(),
            internalLogger = internalLogger
        )
    }

    companion object {
        internal const val WEB_LOGS_FEATURE_NAME = "web-logs"
    }
}
