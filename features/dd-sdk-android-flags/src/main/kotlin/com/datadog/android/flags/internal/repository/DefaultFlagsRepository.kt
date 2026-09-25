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
import com.datadog.android.flags.model.ResolutionReason
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
    private data class FlagsState(
        val context: EvaluationContext,
        val flags: Map<String, PrecomputedFlag>,
        val isStale: Boolean
    )

    // Serialize request admission and both installation paths; readers capture one atomic state.
    private val stateLock = Any()
    private var requestedContext: EvaluationContext? = null
    private val atomicState = AtomicReference<FlagsState?>(null)

    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: count is positive constant (1)
    private val persistenceLoadedLatch = CountDownLatch(1)

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        try {
            persistedState?.let {
                val cachedFlags = it.flags.mapValues { (_, flag) -> flag.copy(reason = ResolutionReason.CACHED.name) }
                synchronized(stateLock) {
                    if (atomicState.get() == null) {
                        atomicState.set(FlagsState(it.evaluationContext, cachedFlags, isStaleFor(it.evaluationContext)))
                    }
                }
            }
        } finally {
            persistenceLoadedLatch.countDown()
        }
    }

    override fun setRequestedContext(context: EvaluationContext) {
        synchronized(stateLock) {
            requestedContext = context
            atomicState.get()?.let { state ->
                atomicState.set(state.copy(isStale = isStaleFor(state.context)))
            }
        }
    }

    override fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>) {
        synchronized(stateLock) {
            atomicState.set(FlagsState(context, flags, isStaleFor(context)))
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
            return if (state.isStale) {
                state.flags.mapValues { (_, flag) -> flag.copy(reason = ResolutionReason.STALE.name) }
            } else {
                state.flags
            }
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

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean {
        val state = atomicState.get()
        return state?.context == context && state.flags.isNotEmpty()
    }

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): FlagWithContext? {
        waitForPersistenceLoad()
        val state = atomicState.get() ?: return null
        val flag = state.flags[key] ?: return null
        return FlagWithContext(flag, state.context, state.isStale)
    }

    // Called only while holding stateLock. No request is distinct from EvaluationContext.EMPTY.
    private fun isStaleFor(context: EvaluationContext): Boolean = requestedContext?.let { it != context } ?: false

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
        private const val PERSISTENCE_LOAD_TIMEOUT_MS = 100L
    }
}
