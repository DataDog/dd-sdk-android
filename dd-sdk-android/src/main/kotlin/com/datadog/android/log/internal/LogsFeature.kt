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
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.domain.LogFilePersistenceStrategy
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.api.RequestFactory
import com.datadog.android.v2.log.internal.net.LogsRequestFactory

internal class LogsFeature(
    coreFeature: CoreFeature
) : SdkFeature<LogEvent, Configuration.Feature.Logs>(coreFeature) {

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.Logs
    ): PersistenceStrategy<LogEvent> {
        return LogFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.trackingConsentProvider,
            coreFeature.storageDir,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            configuration.logsEventMapper,
            coreFeature.localDataEncryption
        )
    }

    override fun createRequestFactory(configuration: Configuration.Feature.Logs): RequestFactory {
        return LogsRequestFactory(configuration.endpointUrl)
    }

    override fun onPostInitialized(context: Context) {}

    // endregion

    companion object {
        internal const val LOGS_FEATURE_NAME = "logs"
    }
}
