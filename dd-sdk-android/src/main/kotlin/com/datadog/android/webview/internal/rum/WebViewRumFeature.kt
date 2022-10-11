/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.persistence.NoOpPersistenceStrategy
import com.datadog.android.core.internal.persistence.PersistenceStrategy
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.rum.internal.ndk.DatadogNdkCrashHandler
import com.datadog.android.v2.core.internal.storage.Storage
import java.util.concurrent.atomic.AtomicBoolean

internal class WebViewRumFeature(
    private val coreFeature: CoreFeature,
    private val storage: Storage
) {

    internal var persistenceStrategy: PersistenceStrategy<Any> = NoOpPersistenceStrategy()
    internal val initialized = AtomicBoolean(false)

    // region SdkFeature

    fun initialize() {
        persistenceStrategy = createPersistenceStrategy(storage)
        initialized.set(true)
    }

    fun stop() {
        persistenceStrategy = NoOpPersistenceStrategy()
        initialized.set(false)
    }

    private fun createPersistenceStrategy(
        storage: Storage
    ): PersistenceStrategy<Any> {
        return WebViewRumFilePersistenceStrategy(
            coreFeature.contextProvider,
            coreFeature.persistenceExecutorService,
            sdkLogger,
            coreFeature.localDataEncryption,
            DatadogNdkCrashHandler.getLastViewEventFile(coreFeature.storageDir),
            storage
        )
    }

    // endregion

    companion object {
        internal const val WEB_RUM_FEATURE_NAME = "web-rum"
    }
}
