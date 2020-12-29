/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.net.LogsOkHttpUploader

internal object LogsFeature : SdkFeature<Log, Configuration.Feature.Logs>(
    authorizedFolderName = LogFileStrategy.AUTHORIZED_FOLDER
) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.Logs
    ): PersistenceStrategy<Log> {
        return LogFileStrategy(
            context,
            trackingConsentProvider = CoreFeature.trackingConsentProvider,
            dataPersistenceExecutorService = CoreFeature.persistenceExecutorService,
            filePersistenceConfig = CoreFeature.buildFilePersistenceConfig()
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
}
