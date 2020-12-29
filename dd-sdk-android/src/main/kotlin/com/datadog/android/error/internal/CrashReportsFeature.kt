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
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.net.LogsOkHttpUploader

internal object CrashReportsFeature : SdkFeature<Log, Configuration.Feature.CrashReport>(
    authorizedFolderName = CrashLogFileStrategy.AUTHORIZED_FOLDER
) {

    private var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

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
    ): PersistenceStrategy<Log> {
        return CrashLogFileStrategy(
            context,
            trackingConsentProvider = CoreFeature.trackingConsentProvider,
            dataPersistenceExecutorService = CoreFeature.persistenceExecutorService
        )
    }

    override fun createUploader(): DataUploader {
        return LogsOkHttpUploader(
            endpointUrl,
            CoreFeature.clientToken,
            CoreFeature.okHttpClient
        )
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
                CoreFeature.envName,
                CoreFeature.packageVersion
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
