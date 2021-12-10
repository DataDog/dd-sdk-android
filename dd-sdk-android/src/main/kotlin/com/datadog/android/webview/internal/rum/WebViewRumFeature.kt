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
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.rum.internal.net.RumOkHttpUploaderV2

internal object WebViewRumFeature : SdkFeature<Any, Configuration.Feature.RUM>() {

    internal const val WEB_RUM_FEATURE_NAME = "web-rum"

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.RUM
    ): PersistenceStrategy<Any> {
        return WebViewRumFilePersistenceStrategy(
            CoreFeature.trackingConsentProvider,
            context,
            CoreFeature.persistenceExecutorService,
            sdkLogger,
            DatadogNdkCrashHandler.getLastViewEventFile(context)
        )
    }

    override fun createUploader(configuration: Configuration.Feature.RUM): DataUploader {
        return RumOkHttpUploaderV2(
            configuration.endpointUrl,
            CoreFeature.clientToken,
            CoreFeature.sourceName,
            CoreFeature.sdkVersion,
            CoreFeature.okHttpClient
        )
    }
}
