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
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage
import com.google.gson.JsonObject

internal class WebViewLogsFeature(
    coreFeature: CoreFeature,
    storage: Storage,
    uploader: DataUploader
) : SdkFeature<JsonObject, Configuration.Feature.Logs>(coreFeature, storage, uploader) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        storage: Storage,
        configuration: Configuration.Feature.Logs
    ): PersistenceStrategy<JsonObject> {
        return WebViewLogFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption,
            coreFeature.buildFilePersistenceConfig(),
            storage
        )
    }

    // endregion

    companion object {
        internal const val WEB_LOGS_FEATURE_NAME = "web-logs"
    }
}
