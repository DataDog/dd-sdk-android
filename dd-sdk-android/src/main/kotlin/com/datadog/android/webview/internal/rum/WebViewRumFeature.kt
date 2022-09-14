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
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.rum.internal.net.RumRequestFactory

internal class WebViewRumFeature(
    coreFeature: CoreFeature
) : SdkFeature<Any, Configuration.Feature.RUM>(coreFeature) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.RUM
    ): PersistenceStrategy<Any> {
        return WebViewRumFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption,
            DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir)
        )
    }

    override fun createRequestFactory(configuration: Configuration.Feature.RUM): RequestFactory {
        return RumRequestFactory(configuration.endpointUrl)
    }

    // endregion

    companion object {
        internal const val WEB_RUM_FEATURE_NAME = "web-rum"
    }
}
