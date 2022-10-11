/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.tracing.internal.domain.TracesFilePersistenceStrategy
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.opentracing.DDSpan
import java.util.concurrent.atomic.AtomicBoolean

internal class TracingFeature(
    private val coreFeature: CoreFeature,
    private val storage: Storage
) {

    internal var persistenceStrategy: PersistenceStrategy<DDSpan> = NoOpPersistenceStrategy()
    internal val initialized = AtomicBoolean(false)

    // region SdkFeature

    fun initialize(configuration: Configuration.Feature.Tracing) {
        persistenceStrategy = createPersistenceStrategy(storage, configuration)
        initialized.set(true)
    }

    fun stop() {
        persistenceStrategy = NoOpPersistenceStrategy()
        initialized.set(false)
    }

    private fun createPersistenceStrategy(
        storage: Storage,
        configuration: Configuration.Feature.Tracing
    ): PersistenceStrategy<DDSpan> {
        return TracesFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.persistenceExecutorService,
            coreFeature,
            coreFeature.envName,
            sdkLogger,
            configuration.spanEventMapper,
            storage
        )
    }

    // endregion

    companion object {
        internal const val TRACING_FEATURE_NAME = "tracing"
    }
}
