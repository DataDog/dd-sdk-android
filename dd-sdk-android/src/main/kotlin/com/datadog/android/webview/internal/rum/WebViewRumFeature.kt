/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.v2.core.internal.net.DataUploader
import com.datadog.android.v2.core.internal.storage.Storage

internal class WebViewRumFeature(
    coreFeature: CoreFeature,
    storage: Storage,
    uploader: DataUploader
) : SdkFeature<Any, Configuration.Feature.RUM>(coreFeature, storage, uploader) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        storage: Storage,
        configuration: Configuration.Feature.RUM
    ): PersistenceStrategy<Any> {
        return WebViewRumFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption,
            DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir),
            coreFeature.buildFilePersistenceConfig(),
            storage
        )
    }

    // endregion

    companion object {
        internal const val WEB_RUM_FEATURE_NAME = "web-rum"
    }
}
