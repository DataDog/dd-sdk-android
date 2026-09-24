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
import com.datadog.android.flags.FlagsConfigurationChangeListener
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.persistence.FlagsPersistenceManager
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import com.datadog.android.internal.utils.DDCoreStateHolder
import java.util.Collections
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
        val restoredFromDisk: Boolean = false
    )

    private data class RepositoryState(
        val assignments: FlagsState? = null,
        val requestedContext: EvaluationContext? = null
    ) {
        fun effectiveFlag(flag: PrecomputedFlag): PrecomputedFlag {
            if (assignments?.restoredFromDisk != true) return flag
            val reason = if (requestedContext == null || requestedContext == assignments.context) {
                ResolutionReason.CACHED
            } else {
                ResolutionReason.STALE
            }
            return flag.copy(reason = reason.name)
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: AtomicReference accepts any initial value.
    private val atomicState = AtomicReference(RepositoryState())

    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: count is positive constant (1)
    private val persistenceLoadedLatch = CountDownLatch(1)

    private data class PersistenceCompletion(val changedKeys: Set<String>? = null)
    private val configurationChanges =
        DDCoreStateHolder.create<PersistenceCompletion, FlagsConfigurationChangeListener>(
            initialState = PersistenceCompletion(),
            onStateChanged = { completion -> completion.changedKeys?.let { onConfigurationChanged(it) } }
        )

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        var changedKeys = emptySet<String>()
        try {
            persistedState?.let {
                val loadedState = FlagsState(it.evaluationContext, it.flags, restoredFromDisk = true)
                val updatedState = updateState { state ->
                    if (state.assignments == null) state.copy(assignments = loadedState) else state
                }
                if (updatedState.assignments === loadedState) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: the set is non-null
                    changedKeys = Collections.unmodifiableSet(it.flags.keys.toSet())
                }
            }
        } finally {
            persistenceLoadedLatch.countDown()
        }
        configurationChanges.updateState(PersistenceCompletion(changedKeys))
    }

    override fun addConfigurationChangeListener(listener: FlagsConfigurationChangeListener) {
        configurationChanges.addListener(listener)
    }

    override fun removeConfigurationChangeListener(listener: FlagsConfigurationChangeListener) {
        configurationChanges.removeListener(listener)
    }

    override fun setRequestedContext(context: EvaluationContext) {
        updateState { it.copy(requestedContext = context) }
    }

    override fun setFlagsAndContext(context: EvaluationContext, flags: Map<String, PrecomputedFlag>) {
        val newState = FlagsState(context, flags)
        updateState { it.copy(assignments = newState) }
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
        if (state.assignments != null) {
            return state.assignments.flags[key]?.let(state::effectiveFlag)
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
        if (state.assignments != null) {
            return if (state.assignments.restoredFromDisk) {
                state.assignments.flags.mapValues { (_, flag) -> state.effectiveFlag(flag) }
            } else {
                state.assignments.flags
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
        return atomicState.get().assignments?.context
    }

    override fun hasFlags(): Boolean {
        waitForPersistenceLoad()
        return atomicState.get().assignments?.flags?.isNotEmpty() ?: false
    }

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean {
        val state = atomicState.get().assignments
        return state?.context == context && state.flags.isNotEmpty()
    }

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        waitForPersistenceLoad()
        val state = atomicState.get()
        val assignments = state.assignments ?: return null
        val flag = assignments.flags[key] ?: return null
        return state.effectiveFlag(flag) to assignments.context
    }

    // AtomicReference.updateAndGet requires API 24; this SDK also supports API 23.
    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: compareAndSet accepts both immutable state references.
    private fun updateState(transform: (RepositoryState) -> RepositoryState): RepositoryState {
        while (true) {
            val previous = atomicState.get()
            val updated = transform(previous)
            if (atomicState.compareAndSet(previous, updated)) return updated
        }
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
        private const val PERSISTENCE_LOAD_TIMEOUT_MS = 100L
    }
}
