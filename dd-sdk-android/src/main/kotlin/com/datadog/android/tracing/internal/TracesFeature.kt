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
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.tracing.internal.domain.TracesFilePersistenceStrategy
import com.datadog.android.tracing.internal.net.TracesOkHttpUploader
import com.datadog.opentracing.DDSpan

internal object TracesFeature : SdkFeature<DDSpan, Configuration.Feature.Tracing>() {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.Tracing
    ): PersistenceStrategy<DDSpan> {
        return TracesFilePersistenceStrategy(
            CoreFeature.trackingConsentProvider,
            context,
            CoreFeature.persistenceExecutorService,
            CoreFeature.timeProvider,
            CoreFeature.networkInfoProvider,
            CoreFeature.userInfoProvider,
            CoreFeature.envName,
            sdkLogger,
            configuration.spanEventMapper
        )
    }

    override fun createUploader(configuration: Configuration.Feature.Tracing): DataUploader {
        return TracesOkHttpUploader(
            configuration.endpointUrl,
            CoreFeature.clientToken,
            CoreFeature.okHttpClient
        )
    }

    // endregion
}
