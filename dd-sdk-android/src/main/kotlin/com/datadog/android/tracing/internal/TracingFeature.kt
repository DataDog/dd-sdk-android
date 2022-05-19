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
import com.datadog.android.core.internal.system.StaticAndroidInfoProvider
import com.datadog.android.core.internal.utils.ERROR_TASK_REJECTED
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.errorWithTelemetry
import com.datadog.android.tracing.internal.domain.TracesFilePersistenceStrategy
import com.datadog.android.tracing.internal.net.TracesOkHttpUploaderV2
import com.datadog.opentracing.DDSpan
import java.util.Locale
import java.util.concurrent.RejectedExecutionException

internal class TracingFeature(
    coreFeature: CoreFeature
) : SdkFeature<DDSpan, Configuration.Feature.Tracing>(coreFeature) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.Tracing
    ): PersistenceStrategy<DDSpan> {
        return TracesFilePersistenceStrategy(
            coreFeature.trackingConsentProvider,
            context,
            coreFeature.persistenceExecutorService,
            coreFeature,
            coreFeature.envName,
            sdkLogger,
            configuration.spanEventMapper,
            coreFeature.localDataEncryption
        )
    }

    override fun createUploader(configuration: Configuration.Feature.Tracing): DataUploader {
        return TracesOkHttpUploaderV2(
            configuration.endpointUrl,
            coreFeature.clientToken,
            coreFeature.sourceName,
            coreFeature.sdkVersion,
            coreFeature.okHttpClient,
            StaticAndroidInfoProvider
        )
    }

    override fun onPostInitialized(context: Context) {
        try {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            coreFeature.persistenceExecutorService.submit {
                @Suppress("ThreadSafety")
                migrateToCacheDir(context, TRACING_FEATURE_NAME, sdkLogger)
            }
        } catch (e: RejectedExecutionException) {
            sdkLogger.errorWithTelemetry(
                ERROR_TASK_REJECTED.format(Locale.US, "postInitMigration"),
                e
            )
        }
    }

    // endregion

    companion object {
        internal const val TRACING_FEATURE_NAME = "tracing"
    }
}
