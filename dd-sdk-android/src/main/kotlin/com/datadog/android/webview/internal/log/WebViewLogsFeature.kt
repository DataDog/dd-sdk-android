/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.net.LogsOkHttpUploaderV2
import com.google.gson.JsonObject

internal class WebViewLogsFeature(
    coreFeature: CoreFeature
) : SdkFeature<JsonObject, Configuration.Feature.Logs>(coreFeature) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.Logs
    ): PersistenceStrategy<JsonObject> {
        return WebViewLogFilePersistenceStrategy(
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption
        )
    }

    override fun createUploader(configuration: Configuration.Feature.Logs): DataUploader {
        return LogsOkHttpUploaderV2(
            configuration.endpointUrl,
            coreFeature.clientToken,
            coreFeature.sourceName,
            coreFeature.sdkVersion,
            coreFeature.okHttpClient,
            coreFeature.androidInfoProvider,
            sdkLogger
        )
    }

    // endregion

    companion object {
        internal const val WEB_LOGS_FEATURE_NAME = "web-logs"
    }
}
