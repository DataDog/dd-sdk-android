/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.persistence.FlagsPersistenceManager
import com.datadog.android.flags.model.EvaluationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val instanceName: String,
    private val dataStore: DataStoreHandler,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger,
    private val persistenceLoadTimeoutMs: Long = PERSISTENCE_LOAD_TIMEOUT_MS
) : FlagsRepository {
    private data class FlagsState(val context: EvaluationContext, val flags: Map<String, PrecomputedFlag>)
    private val atomicState = AtomicReference<FlagsState?>(null)
    private val persistenceLoadLock = Any()
    private var canApplyPersistenceLoad = true

    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: count is positive constant (1)
    private val persistenceLoadedLatch = CountDownLatch(1)

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        try {
            synchronized(persistenceLoadLock) {
                if (canApplyPersistenceLoad) {
                    canApplyPersistenceLoad = false
                    persistedState?.let {
                        atomicState.set(FlagsState(it.evaluationContext, it.flags))
                    }
                }
            }
        } finally {
            persistenceLoadedLatch.countDown()
        }
    }

    override fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>) {
        val newState = FlagsState(context, flags)
        synchronized(persistenceLoadLock) {
            canApplyPersistenceLoad = false
            atomicState.set(newState)
        }
        persistenceLoadedLatch.countDown()

        persistenceManager.saveFlagsState(
            context = context,
            flags = flags,
            currentTimestamp = featureSdkCore.timeProvider.getDeviceTimestampMillis(),
            object : DataStoreWriteCallback {
                override fun onSuccess() {
                }

                override fun onFailure() {
                    internalLogger.log(
                        target = InternalLogger.Target.MAINTAINER,
                        level = InternalLogger.Level.WARN,
                        messageBuilder = { ERROR_SAVING_FLAGS_STATE }
                    )
                }
            }
        )
    }

    override fun clear() {
        synchronized(persistenceLoadLock) {
            canApplyPersistenceLoad = false
            atomicState.set(null)
        }
        persistenceLoadedLatch.countDown()
        persistenceManager.clearFlagsState(
            object : DataStoreWriteCallback {
                override fun onSuccess() {
                }

                override fun onFailure() {
                    internalLogger.log(
                        target = InternalLogger.Target.MAINTAINER,
                        level = InternalLogger.Level.WARN,
                        messageBuilder = { ERROR_CLEARING_FLAGS_STATE }
                    )
                }
            }
        )
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        waitForPersistenceLoad()
        val state = atomicState.get()
        if (state != null) {
            return state.flags[key]
        }
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { WARN_CONTEXT_NOT_SET }
        )
        return null
    }

    override fun getFlagsSnapshot(): Map<String, PrecomputedFlag> {
        waitForPersistenceLoad()
        val state = atomicState.get()
        if (state != null) {
            return state.flags
        }
        internalLogger.log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            { WARN_CONTEXT_NOT_SET }
        )
        return emptyMap()
    }

    override fun getEvaluationContext(): EvaluationContext? {
        waitForPersistenceLoad()
        return atomicState.get()?.context
    }

    override fun hasFlags(): Boolean {
        waitForPersistenceLoad()
        return atomicState.get()?.flags?.isNotEmpty() ?: false
    }

    override fun hasFlagsForContext(context: EvaluationContext): Boolean {
        waitForPersistenceLoad()
        val state = atomicState.get()
        return state?.context == context && state.flags.isNotEmpty()
    }

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        waitForPersistenceLoad()
        val state = atomicState.get() ?: return null
        val flag = state.flags[key] ?: return null
        return flag to state.context
    }

    private fun waitForPersistenceLoad() {
        try {
            persistenceLoadedLatch.await(persistenceLoadTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Safe: self-interruption is always permitted
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        const val WARN_CONTEXT_NOT_SET = "You must call FlagsClientManager.get().setEvaluationContext " +
            "in order to have flags available"
        const val ERROR_SAVING_FLAGS_STATE = "Failed to save flags state to persistent storage"
        const val ERROR_CLEARING_FLAGS_STATE = "Failed to clear flags state from persistent storage"
        private const val PERSISTENCE_LOAD_TIMEOUT_MS = 100L
    }
}
