/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.monitoring.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.utils.rebuildSdkLogger
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.LogsOkHttpUploader

internal object InternalLogsFeature : SdkFeature<Log, Configuration.Feature.InternalLogs>(
    authorizedFolderName = InternalLogFileStrategy.AUTHORIZED_FOLDER
) {

    internal const val SERVICE_NAME = "dd-sdk-android"
    internal const val ENV_NAME = "prod"

    // region SdkFeature

    override fun onPostInitialized(context: Context) {
        // The sdk logger might have already been initialized
        // while the feature was not yet initialized
        rebuildSdkLogger()
        sdkLogger.addAttribute("application", CoreFeature.packageName)
    }

    override fun onPostStopped() {
        rebuildSdkLogger()
    }

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.InternalLogs
    ): PersistenceStrategy<Log> {
        return InternalLogFileStrategy(
            context,
            trackingConsentProvider = CoreFeature.trackingConsentProvider,
            dataPersistenceExecutorService = CoreFeature.persistenceExecutorService,
            filePersistenceConfig = CoreFeature.buildFilePersistenceConfig()
        )
    }

    override fun createUploader(configuration: Configuration.Feature.InternalLogs): DataUploader {
        return LogsOkHttpUploader(
            configuration.endpointUrl,
            configuration.internalClientToken,
            CoreFeature.okHttpClient
        )
    }

    // endregion
}
