/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.data.upload.DataUploadScheduler
import com.datadog.android.core.internal.data.upload.NoOpUploadScheduler
import com.datadog.android.core.internal.data.upload.UploadScheduler
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.log.internal.user.UserInfoProvider
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

internal object CrashReportsFeature {

    internal var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    internal val initialized = AtomicBoolean(false)

    internal var clientToken: String = ""
    internal var endpointUrl: String = DatadogEndpoint.LOGS_US
    internal var envName: String = ""
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
    internal var dataUploadScheduler: UploadScheduler = NoOpUploadScheduler()

    @Suppress("LongParameterList")
    fun initialize(
        appContext: Context,
        config: DatadogConfig.FeatureConfig,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        userInfoProvider: UserInfoProvider,
        timeProvider: TimeProvider,
        systemInfoProvider: SystemInfoProvider,
        dataUploadThreadPoolExecutor: ScheduledThreadPoolExecutor
    ) {

        if (initialized.get()) {
            return
        }

        clientToken = config.clientToken
        endpointUrl = config.endpointUrl
        envName = config.envName

        persistenceStrategy = CrashLogFileStrategy(appContext)
        setupUploader(
            endpointUrl,
            okHttpClient,
            networkInfoProvider,
            systemInfoProvider,
            dataUploadThreadPoolExecutor
        )
        setupExceptionHandler(appContext, networkInfoProvider, userInfoProvider, timeProvider)

        initialized.set(true)
    }

    fun stop() {
        if (initialized.get()) {
            Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
            dataUploadScheduler.stopScheduling()

            persistenceStrategy = NoOpPersistenceStrategy()
            uploader = NoOpDataUploader()
            dataUploadScheduler = NoOpUploadScheduler()
            clientToken = ""
            envName = ""
            endpointUrl = DatadogEndpoint.LOGS_US

            initialized.set(false)
        }
    }

    // region Internal

    private fun setupUploader(
        endpointUrl: String,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        systemInfoProvider: SystemInfoProvider,
        scheduledThreadPoolExecutor: ScheduledThreadPoolExecutor
    ) {
        dataUploadScheduler = if (CoreFeature.isMainProcess) {
            uploader = LogsOkHttpUploader(endpointUrl, clientToken, okHttpClient)
            DataUploadScheduler(
                persistenceStrategy.getReader(),
                uploader,
                networkInfoProvider,
                systemInfoProvider,
                scheduledThreadPoolExecutor
            )
        } else {
            NoOpUploadScheduler()
        }
        dataUploadScheduler.startScheduling()
    }

    private fun setupExceptionHandler(
        appContext: Context,
        networkInfoProvider: NetworkInfoProvider,
        userInfoProvider: UserInfoProvider,
        timeProvider: TimeProvider
    ) {
        originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        DatadogExceptionHandler(
            networkInfoProvider,
            timeProvider,
            userInfoProvider,
            persistenceStrategy.getWriter(),
            appContext
        ).register()
    }

    // endregion
}
