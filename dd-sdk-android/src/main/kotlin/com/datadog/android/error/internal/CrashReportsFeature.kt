/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.atomic.AtomicBoolean

internal class CrashReportsFeature(
    private val coreFeature: CoreFeature,
    private val storage: Storage
) {

    internal var persistenceStrategy: PersistenceStrategy<LogEvent> = NoOpPersistenceStrategy()
    internal val initialized = AtomicBoolean(false)
    internal var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

    // region SdkFeature

    fun initialize(context: Context) {
        persistenceStrategy = createPersistenceStrategy(storage)
        setupExceptionHandler(context)
        initialized.set(true)
    }

    fun stop() {
        resetOriginalExceptionHandler()
        initialized.set(false)
    }

    private fun createPersistenceStrategy(
        storage: Storage
    ): PersistenceStrategy<LogEvent> {
        return CrashReportFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.persistenceExecutorService,
            storage
        )
    }

    // endregion

    // region Internal

    private fun setupExceptionHandler(
        appContext: Context
    ) {
        originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        DatadogExceptionHandler(
            DatadogLogGenerator(
                coreFeature.serviceName,
                DatadogExceptionHandler.LOGGER_NAME,
                coreFeature.networkInfoProvider,
                coreFeature.userInfoProvider,
                coreFeature.timeProvider,
                coreFeature.sdkVersion,
                coreFeature.envName,
                coreFeature.variant,
                coreFeature.packageVersionProvider
            ),
            writer = persistenceStrategy.getWriter(),
            appContext = appContext
        ).register()
    }

    private fun resetOriginalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
    }

    // endregion

    companion object {
        internal const val CRASH_FEATURE_NAME = "crash"
    }
}
