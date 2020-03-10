/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.content.Context
import android.os.HandlerThread
import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.data.upload.DataUploadHandlerThread
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogFileStrategy
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

internal object LogsFeature {

    internal val initialized = AtomicBoolean(false)

    internal var clientToken: String = ""
    internal var endpointUrl: String = DatadogEndpoint.LOGS_US
    internal var serviceName: String = DatadogConfig.DEFAULT_SERVICE_NAME
    internal var envName: String = DatadogConfig.DEFAULT_ENV_NAME
        set(value) {
            field = value
            envTag = if (value.isEmpty()) {
                ""
            } else {
                "env:$value"
            }
        }
    internal var envTag: String = ""
        private set

    internal var persistenceStrategy: PersistenceStrategy<Log> = NoOpPersistenceStrategy()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var uploadHandlerThread: HandlerThread = HandlerThread("NoOp")

    fun initialize(
        appContext: Context,
        config: DatadogConfig.FeatureConfig,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        systemInfoProvider: SystemInfoProvider
    ) {
        if (initialized.get()) {
            return
        }

        clientToken = config.clientToken
        endpointUrl = config.endpointUrl
        serviceName = config.serviceName
        envName = config.envName

        persistenceStrategy = LogFileStrategy(appContext)
        setupUploader(endpointUrl, okHttpClient, networkInfoProvider, systemInfoProvider)

        initialized.set(true)
    }

    fun isInitialized(): Boolean {
        return initialized.get()
    }

    fun stop() {
        if (initialized.get()) {
            uploadHandlerThread.quit()

            persistenceStrategy = NoOpPersistenceStrategy()
            uploadHandlerThread = HandlerThread("NoOp")
            clientToken = ""
            endpointUrl = DatadogEndpoint.LOGS_US
            serviceName = DatadogConfig.DEFAULT_SERVICE_NAME

            initialized.set(false)
        }
    }

    // region Internal

    private fun setupUploader(
        endpointUrl: String,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        systemInfoProvider: SystemInfoProvider
    ) {
        uploader = LogsOkHttpUploader(endpointUrl, clientToken, okHttpClient)

        uploadHandlerThread = DataUploadHandlerThread(
            LOGS_UPLOAD_THREAD_NAME,
            persistenceStrategy.getReader(),
            uploader,
            networkInfoProvider,
            systemInfoProvider
        )
        uploadHandlerThread.start()
    }

    // endregion

    // region Constants

    internal const val LOGS_UPLOAD_THREAD_NAME = "ddog-logs-upload"

    // endregion
}
