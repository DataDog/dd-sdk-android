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
import com.datadog.android.core.internal.privacy.ConsentProvider
import com.datadog.android.core.internal.system.SystemInfoProvider
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.domain.LogGenerator
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.plugin.DatadogPluginConfig
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

internal object CrashReportsFeature {

    internal var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    internal val initialized = AtomicBoolean(false)

    internal var clientToken: String = ""
    internal var endpointUrl: String = DatadogEndpoint.LOGS_US
    internal var persistenceStrategy: PersistenceStrategy<Log> = NoOpPersistenceStrategy()
    internal var uploader: DataUploader = NoOpDataUploader()
    internal var dataUploadScheduler: UploadScheduler = NoOpUploadScheduler()
    internal var plugins: List<DatadogPlugin> = emptyList()

    @Suppress("LongParameterList")
    fun initialize(
        appContext: Context,
        config: DatadogConfig.FeatureConfig,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        userInfoProvider: UserInfoProvider,
        systemInfoProvider: SystemInfoProvider,
        dataUploadThreadPoolExecutor: ScheduledThreadPoolExecutor,
        dataPersistenceExecutor: ExecutorService,
        trackingConsentProvider: ConsentProvider
    ) {

        if (initialized.get()) {
            return
        }

        clientToken = config.clientToken
        endpointUrl = config.endpointUrl

        persistenceStrategy = CrashLogFileStrategy(
            appContext,
            dataPersistenceExecutorService = dataPersistenceExecutor,
            trackingConsentProvider = trackingConsentProvider
        )
        setupUploader(
            endpointUrl,
            okHttpClient,
            networkInfoProvider,
            systemInfoProvider,
            dataUploadThreadPoolExecutor
        )
        registerPlugins(appContext, config)
        setupExceptionHandler(appContext, networkInfoProvider, userInfoProvider)

        initialized.set(true)
    }

    fun clearAllData() {
        persistenceStrategy.clearAllData()
    }

    fun stop() {
        if (initialized.get()) {
            unregisterPlugins()
            Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
            dataUploadScheduler.stopScheduling()

            persistenceStrategy = NoOpPersistenceStrategy()
            uploader = NoOpDataUploader()
            dataUploadScheduler = NoOpUploadScheduler()
            clientToken = ""
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
        userInfoProvider: UserInfoProvider
    ) {
        originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        DatadogExceptionHandler(
            LogGenerator(
                CoreFeature.serviceName,
                DatadogExceptionHandler.LOGGER_NAME,
                networkInfoProvider,
                userInfoProvider,
                CoreFeature.envName,
                CoreFeature.packageVersion
            ),
            writer = persistenceStrategy.getWriter(),
            appContext = appContext
        ).register()
    }

    private fun registerPlugins(appContext: Context, config: DatadogConfig.FeatureConfig) {
        plugins = config.plugins
        plugins.forEach {
            it.register(
                DatadogPluginConfig.CrashReportsPluginConfig(
                    appContext,
                    config.envName,
                    CoreFeature.serviceName
                )
            )
        }
    }

    private fun unregisterPlugins() {
        plugins.forEach {
            it.unregister()
        }
    }

    // endregion
}
