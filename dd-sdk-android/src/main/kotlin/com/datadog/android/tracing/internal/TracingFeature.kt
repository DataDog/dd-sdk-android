/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.tracing.internal.domain.TracesFilePersistenceStrategy
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.tracing.internal.net.TracesRequestFactory
import com.datadog.opentracing.DDSpan

internal class TracingFeature(
    coreFeature: CoreFeature
) : SdkFeature<DDSpan, Configuration.Feature.Tracing>(coreFeature) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.Tracing
    ): PersistenceStrategy<DDSpan> {
        return TracesFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            coreFeature,
            coreFeature.envName,
            sdkLogger,
            configuration.spanEventMapper,
            coreFeature.localDataEncryption
        )
    }

    override fun createRequestFactory(configuration: Configuration.Feature.Tracing):
        RequestFactory {
        return TracesRequestFactory(configuration.endpointUrl)
    }

    override fun onPostInitialized(context: Context) {}

    // endregion

    companion object {
        internal const val TRACING_FEATURE_NAME = "tracing"
    }
}
