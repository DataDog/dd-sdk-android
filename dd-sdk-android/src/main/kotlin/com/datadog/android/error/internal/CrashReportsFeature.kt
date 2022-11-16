/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.net.LogsOkHttpUploaderV2
import com.datadog.android.log.model.LogEvent

internal object CrashReportsFeature : SdkFeature<LogEvent, Configuration.Feature.CrashReport>() {

    internal const val CRASH_FEATURE_NAME = "crash"

    internal var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    // region SdkFeature

    override fun onInitialize(context: Context, configuration: Configuration.Feature.CrashReport) {
        setupExceptionHandler(context)
    }

    override fun onStop() {
        resetOriginalExceptionHandler()
    }

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.CrashReport
    ): PersistenceStrategy<LogEvent> {
        return CrashReportFilePersistenceStrategy(
            CoreFeature.trackingConsentProvider,
            context,
            CoreFeature.persistenceExecutorService,
            sdkLogger,
            CoreFeature.localDataEncryption
        )
    }

    override fun createUploader(configuration: Configuration.Feature.CrashReport): DataUploader {
        return LogsOkHttpUploaderV2(
            configuration.endpointUrl,
            CoreFeature.clientToken,
            CoreFeature.sourceName,
            CoreFeature.sdkVersion,
            CoreFeature.okHttpClient,
            CoreFeature.androidInfoProvider,
            sdkLogger
        )
    }

    override fun onPostInitialized(context: Context) {
        migrateToCacheDir(context, CRASH_FEATURE_NAME, sdkLogger)
    }

    // endregion

    // region Internal

    private fun setupExceptionHandler(
        appContext: Context
    ) {
        originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        DatadogExceptionHandler(
            LogGenerator(
                CoreFeature.serviceName,
                DatadogExceptionHandler.LOGGER_NAME,
                CoreFeature.networkInfoProvider,
                CoreFeature.userInfoProvider,
                CoreFeature.timeProvider,
                CoreFeature.sdkVersion,
                CoreFeature.envName,
                CoreFeature.variant,
                CoreFeature.packageVersionProvider,
                CoreFeature.androidInfoProvider
            ),
            writer = persistenceStrategy.getWriter(),
            appContext = appContext
        ).register()
    }

    private fun resetOriginalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
    }

    // endregion
}
