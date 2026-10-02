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

    private val firstFlags = FirstFlagsLatch()

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        persistedState?.let {
            val cachedFlags = it.flags.mapValues { (_, flag) -> flag.copy(reason = ResolutionReason.CACHED.name) }
            val loadedState = FlagsState(it.evaluationContext, cachedFlags)
            if (atomicState.compareAndSet(null, loadedState)) {
                firstFlags.complete(cachedFlags.keys)
            }
        }
    }

    override fun waitForFlags(): FirstFlagsLatch = firstFlags

    override fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>) {
        val newState = FlagsState(context, flags)

        val firstInstallation = atomicState.getAndSet(newState) == null

        try {
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
        } finally {
            if (firstInstallation) {
                firstFlags.complete(flags.keys)
            }
        }
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        waitForInstalledFlags()
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
        waitForInstalledFlags()
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
        waitForInstalledFlags()
        return atomicState.get()?.context
    }

    override fun hasFlags(): Boolean {
        waitForInstalledFlags()
        return atomicState.get()?.flags?.isNotEmpty() ?: false
    }

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean {
        val state = atomicState.get()
        return state?.context == context && state.flags.isNotEmpty()
    }

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        waitForInstalledFlags()
        val state = atomicState.get() ?: return null
        val flag = state.flags[key] ?: return null
        return flag to state.context
    }

    private fun waitForInstalledFlags() {
        try {
            firstFlags.await(persistenceLoadTimeoutMs, TimeUnit.MILLISECONDS)
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
