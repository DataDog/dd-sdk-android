/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.flags.AssignmentProtection
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.ProtectedAssignmentEnvelope
import com.datadog.android.flags.internal.persistence.FlagsPersistenceManager
import com.datadog.android.flags.model.EvaluationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal fun interface ProtectedAssignmentsCacheVerifier {
    fun verify(
        entry: FlagsStateEntry,
        context: EvaluationContext,
        datadogContext: DatadogContext
    ): Map<String, PrecomputedFlag>?
}

internal interface ProtectedFlagsCacheRepository {
    fun restoreProtectedState(context: EvaluationContext, datadogContext: DatadogContext): Boolean
}

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val instanceName: String,
    private val dataStore: DataStoreHandler,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger,
    private val persistenceLoadTimeoutMs: Long = PERSISTENCE_LOAD_TIMEOUT_MS,
    private val acceptPersistedState: Boolean = true,
    private val assignmentProtection: AssignmentProtection = if (acceptPersistedState) {
        AssignmentProtection.DISABLED
    } else {
        AssignmentProtection.SIGNED_AND_AUTHORIZED
    },
    private val protectedCacheVerifier: ProtectedAssignmentsCacheVerifier? = null
) : FlagsRepository, ProtectedFlagsCacheRepository {
    private data class FlagsState(val context: EvaluationContext, val flags: Map<String, PrecomputedFlag>)
    private val atomicState = AtomicReference<FlagsState?>(null)
    private val persistedStateLock = Any()
    private var mayLoadPersistedState = true
    private var pendingProtectedState: FlagsStateEntry? = null
    private var stateGeneration = 0L

    @Suppress("UnsafeThirdPartyFunctionCall") // Safe: count is positive constant (1)
    private val persistenceLoadedLatch = CountDownLatch(1)

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        try {
            synchronized(persistedStateLock) {
                if (mayLoadPersistedState) {
                    persistedState?.let {
                        if (assignmentProtection == AssignmentProtection.DISABLED && acceptPersistedState) {
                            val loadedState = FlagsState(it.evaluationContext, it.flags)
                            atomicState.compareAndSet(null, loadedState)
                        } else if (it.protectedEnvelope?.protection == assignmentProtection) {
                            pendingProtectedState = it
                        }
                    }
                }
                mayLoadPersistedState = false
            }
        } finally {
            persistenceLoadedLatch.countDown()
        }
    }

    override fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        rawResponseBody: String?,
        protectedEnvelope: ProtectedAssignmentEnvelope?
    ) {
        val isProtected = assignmentProtection != AssignmentProtection.DISABLED
        if (
            isProtected &&
            (rawResponseBody == null || protectedEnvelope?.protection != assignmentProtection)
        ) {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { "Rejected flag assignments without the required verified envelope" },
                onlyOnce = true
            )
            return
        }
        val newState = FlagsState(context, flags)
        synchronized(persistedStateLock) {
            mayLoadPersistedState = false
            pendingProtectedState = null
            stateGeneration += 1
            atomicState.set(newState)
        }
        persistenceLoadedLatch.countDown()

        persistenceManager.saveFlagsState(
            context = context,
            flags = flags,
            currentTimestamp = featureSdkCore.timeProvider.getDeviceTimestampMillis(),
            rawResponseBody = rawResponseBody.takeIf { isProtected },
            protectedEnvelope = protectedEnvelope.takeIf { isProtected },
            callback = object : DataStoreWriteCallback {
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

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean {
        val state = atomicState.get()
        return state?.context == context && state.flags.isNotEmpty()
    }

    override fun clear() {
        synchronized(persistedStateLock) {
            mayLoadPersistedState = false
            pendingProtectedState = null
            stateGeneration += 1
            atomicState.set(null)
        }
        persistenceLoadedLatch.countDown()
        persistenceManager.clearFlagsState()
    }

    @Suppress("ReturnCount") // Early returns keep every rejected cache path explicit.
    override fun restoreProtectedState(context: EvaluationContext, datadogContext: DatadogContext): Boolean {
        if (assignmentProtection == AssignmentProtection.DISABLED) return false
        waitForPersistenceLoad()
        val verifier = protectedCacheVerifier ?: return false
        val (entry, generation) = synchronized(persistedStateLock) {
            val candidate = pendingProtectedState?.takeIf { it.evaluationContext == context }
                ?: return false
            candidate to stateGeneration
        }
        val flags = verifier.verify(entry, context, datadogContext)
        if (flags == null) {
            synchronized(persistedStateLock) {
                if (pendingProtectedState === entry) pendingProtectedState = null
            }
            persistenceManager.clearFlagsState()
            return false
        }
        return synchronized(persistedStateLock) {
            if (stateGeneration != generation || pendingProtectedState !== entry) {
                false
            } else {
                pendingProtectedState = null
                stateGeneration += 1
                atomicState.set(FlagsState(context, flags))
                true
            }
        }
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
        private const val PERSISTENCE_LOAD_TIMEOUT_MS = 100L
    }
}
