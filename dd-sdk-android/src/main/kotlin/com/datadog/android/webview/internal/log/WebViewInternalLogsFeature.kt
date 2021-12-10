/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.content.Context
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeature
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.log.Logger
import com.datadog.android.log.internal.logger.NoOpLogHandler
import com.datadog.android.log.internal.net.LogsOkHttpUploaderV2
import com.datadog.android.log.model.WebViewLogEvent

internal object WebViewInternalLogsFeature : SdkFeature<WebViewLogEvent,
    Configuration.Feature.InternalLogs>() {

    internal const val WEB_INTERNAL_LOGS_FEATURE_NAME = "web-internal-logs"

    // region SdkFeature

    override fun createPersistenceStrategy(
        context: Context,
        configuration: Configuration.Feature.InternalLogs
    ): PersistenceStrategy<WebViewLogEvent> {
        return WebViewInternalLogFilePersistenceStrategy(
            CoreFeature.trackingConsentProvider,
            context,
            CoreFeature.persistenceExecutorService,
            Logger(NoOpLogHandler())
        )
    }

    override fun createUploader(configuration: Configuration.Feature.InternalLogs): DataUploader {
        return LogsOkHttpUploaderV2(
            configuration.endpointUrl,
            configuration.internalClientToken,
            CoreFeature.sourceName,
            CoreFeature.sdkVersion,
            CoreFeature.okHttpClient,
            Logger(NoOpLogHandler())
        )
    }

    // endregion
}
