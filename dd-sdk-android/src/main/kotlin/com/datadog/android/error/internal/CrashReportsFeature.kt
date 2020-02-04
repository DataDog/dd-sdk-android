/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2020 Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.DatadogConfig
import com.datadog.android.DatadogEndpoint
import com.datadog.android.core.internal.domain.NoOpPersistenceStrategy
import com.datadog.android.core.internal.domain.PersistenceStrategy
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.core.internal.net.NoOpDataUploader
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.log.internal.user.UserInfoProvider
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.OkHttpClient

internal object CrashReportsFeature {

    internal var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
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

    @Suppress("LongParameterList")
    fun initialize(
        appContext: Context,
        config: DatadogConfig.FeatureConfig,
        okHttpClient: OkHttpClient,
        networkInfoProvider: NetworkInfoProvider,
        userInfoProvider: UserInfoProvider,
        timeProvider: TimeProvider
    ) {

        if (initialized.get()) {
            return
        }

        clientToken = config.clientToken
        endpointUrl = config.endpointUrl
        serviceName = config.serviceName
        envName = config.envName

        persistenceStrategy = CrashLogFileStrategy(appContext)
        uploader = LogsOkHttpUploader(endpointUrl, clientToken, okHttpClient)

        setupTheExceptionHandler(appContext, networkInfoProvider, userInfoProvider, timeProvider)

        initialized.set(true)
    }

    fun stop() {
        if (initialized.get()) {
            Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)

            persistenceStrategy = NoOpPersistenceStrategy()
            uploader = NoOpDataUploader()
            clientToken = ""
            endpointUrl = DatadogEndpoint.LOGS_US
            serviceName = DatadogConfig.DEFAULT_SERVICE_NAME

            initialized.set(false)
        }
    }

    // region Internal

    private fun setupTheExceptionHandler(
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
