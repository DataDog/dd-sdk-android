/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.log.internal.domain.LogFilePersistenceStrategy
import com.datadog.android.log.model.LogEvent
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.atomic.AtomicBoolean

internal class LogsFeature(
    private val coreFeature: CoreFeature,
    private val storage: Storage
) {
    internal var persistenceStrategy: PersistenceStrategy<LogEvent> = NoOpPersistenceStrategy()
    internal val initialized = AtomicBoolean(false)

    internal fun initialize(configuration: Configuration.Feature.Logs) {
        persistenceStrategy = createPersistenceStrategy(storage, configuration)
        initialized.set(true)
    }

    internal fun stop() {
        persistenceStrategy = NoOpPersistenceStrategy()
        initialized.set(false)
    }

    // region SdkFeature

    private fun createPersistenceStrategy(
        storage: Storage,
        configuration: Configuration.Feature.Logs
    ): PersistenceStrategy<LogEvent> {
        return LogFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.persistenceExecutorService,
            configuration.logsEventMapper,
            storage
        )
    }

    // endregion

    companion object {
        internal const val LOGS_FEATURE_NAME = "logs"
    }
}
