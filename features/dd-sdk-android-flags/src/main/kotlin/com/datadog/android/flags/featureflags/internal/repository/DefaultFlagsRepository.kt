/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.utils.executeSafe
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.repository.net.DefaultFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.FlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.NoOpFlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.internal.repository.store.FlagsStoreManager
import com.datadog.android.flags.featureflags.internal.repository.store.NoOpStoreManager
import com.datadog.android.flags.featureflags.internal.repository.store.StoreManager
import com.datadog.android.flags.featureflags.model.ProviderContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicReference

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val executorService: ExecutorService,
    private val flagsContext: FlagsContext,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger,
    private var flagsStoreManager: StoreManager = NoOpStoreManager(),
    private val precomputeMapper: PrecomputeMapper = PrecomputeMapper(
        internalLogger = internalLogger
    ),
    private var flagsNetworkManager: FlagsNetworkManager = NoOpFlagsNetworkManager()
) : FlagsRepository {
    private var currentProviderContext = AtomicReference<ProviderContext>(null)

    init {
        flagsStoreManager = FlagsStoreManager()

        flagsNetworkManager = DefaultFlagsNetworkManager(
            internalLogger = internalLogger,
            flagsContext = flagsContext
        )
    }

    override fun updateProviderContext(newContext: ProviderContext) {
        currentProviderContext.set(newContext)
        fetchPrecomputedFlagsFromRemote()
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        if (currentProviderContext.get() == null) {
            featureSdkCore.internalLogger.log(
                level = InternalLogger.Level.WARN,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_CONTEXT_NOT_SET }
            )
            return null
        }

        val result = flagsStoreManager.getPrecomputedFlag(key)
        return result
    }

    private fun fetchPrecomputedFlagsFromRemote() {
        executorService.executeSafe(
            operationName = FETCH_PRECOMPUTED_FLAGS_OPERATION_NAME,
            internalLogger = internalLogger
        ) {
            val response = flagsNetworkManager.downloadPrecomputedFlags(currentProviderContext.get())
            if (response != null) {
                val flagsMap = precomputeMapper.map(response)
                if (flagsMap.isNotEmpty()) {
                    flagsStoreManager.updateFlagsState(flagsMap)
                }
            }
        }
    }

    private companion object {
        const val FETCH_PRECOMPUTED_FLAGS_OPERATION_NAME = "Fetch precomputed flags"
        const val ERROR_CONTEXT_NOT_SET = "You must call FlagsClient.get().setContext in order to have flags available"
    }
}
