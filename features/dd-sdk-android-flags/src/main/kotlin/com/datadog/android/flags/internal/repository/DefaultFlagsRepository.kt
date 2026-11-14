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
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.flags.ExperimentationOptOutMode
import com.datadog.android.flags.internal.aggregation.EvaluationTargetingKey
import com.datadog.android.flags.internal.model.FlagEvaluationSnapshot
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.persistence.FlagsPersistenceManager
import com.datadog.android.flags.internal.selection.CanonicalFlagSelection
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    instanceName: String,
    dataStore: DataStoreHandler,
    flagSelection: CanonicalFlagSelection?,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger,
    private val persistenceLoadTimeoutMs: Long = PERSISTENCE_LOAD_TIMEOUT_MS,
    private val deliverFirstFlags: (Runnable) -> Unit = Runnable::run,
    experimentationOptOutMode: ExperimentationOptOutMode = ExperimentationOptOutMode.DISABLED
) : FlagsRepository {
    private data class FlagsState(
        val context: EvaluationContext,
        val flags: Map<String, PrecomputedFlag>,
        val obfuscation: FlagKeyObfuscation?,
        val observeFullEvaluationData: Boolean,
        val hasAcceptedConfiguration: Boolean = true,
        val isTombstone: Boolean = false
    ) {
        val telemetryTargetingKey = EvaluationTargetingKey.create(context.targetingKey, observeFullEvaluationData)

        operator fun get(key: String): PrecomputedFlag? =
            if (obfuscation == null) flags[key] else obfuscation.encode(key)?.let { flags[it] }
    }
    private val atomicState = AtomicReference<FlagsState?>(null)
    private val obfuscationSupported = (featureSdkCore as? InternalSdkCore)?.source == "android"

    private val readableState: FlagsState?
        get() = atomicState.get()?.takeIf { it.obfuscation == null || obfuscationSupported }

    @Suppress("UnsafeThirdPartyFunctionCall") // CountDownLatch rejects negative counts; 1 is valid.
    private val persistenceLoadedLatch = CountDownLatch(1)

    override val firstFlags = FirstFlagsLatch()
    override val cachedFlagsLoaded = FirstFlagsLatch()

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        flagSelection = flagSelection,
        internalLogger = internalLogger,
        experimentationOptOutMode = experimentationOptOutMode
    ) { loadedState ->
        // State fetched with a different selection holds a different set of flags.
        val persistedState = loadedState?.takeIf { it.flagSelection == flagSelection }
        val cachedState = try {
            persistedState?.let {
                FlagsState(
                    it.evaluationContext,
                    it.flags.mapValues { (_, flag) ->
                        flag.copy(
                            reason = ResolutionReason.CACHED.name,
                            observeFullEvaluationData = it.observeFullEvaluationData
                        )
                    },
                    it.obfuscation,
                    it.observeFullEvaluationData
                ).takeIf { state -> installPersistedState(state) }
            }
        } finally {
            persistenceLoadedLatch.countDown()
        }
        try {
            cachedFlagsLoaded.complete(emptyList())
        } finally {
            if (cachedState != null && persistedState != null) {
                publishFirstFlags(cachedState, persistedState.flags.keys)
            }
        }
    }

    override fun installFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        observeFullEvaluationData: Boolean
    ): FlagsInstallation {
        val newState = FlagsState(context, flags, obfuscation, observeFullEvaluationData)

        val previousState = atomicState.getAndSet(newState)
        val firstInstallation = previousState?.hasAcceptedConfiguration != true ||
            (previousState.obfuscation != null && !obfuscationSupported)
        persistenceLoadedLatch.countDown()

        return FlagsInstallation { onInstalled ->
            persistFlags(context, flags, obfuscation, observeFullEvaluationData)
            try {
                onInstalled()
            } finally {
                if (firstInstallation) publishFirstFlags(newState, flags.keys)
            }
        }
    }

    private fun persistFlags(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        observeFullEvaluationData: Boolean
    ) {
        try {
            persistenceManager.saveFlagsState(
                context = context,
                flags = flags,
                currentTimestamp = featureSdkCore.timeProvider.getDeviceTimestampMillis(),
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
                },
                obfuscation = obfuscation,
                observeFullEvaluationData = observeFullEvaluationData
            )
        } catch (
            // Storage submission failure must not abort an accepted installation.
            @Suppress("TooGenericExceptionCaught")
            exception: Exception
        ) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
                { ERROR_SAVING_FLAGS_STATE },
                exception
            )
        }
    }

    override fun getAssignmentsSnapshot(): FlagsAssignmentsSnapshot? {
        val state = readableState?.takeUnless { it.isTombstone } ?: return null
        return FlagsAssignmentsSnapshot(if (state.obfuscation == null) state.flags else emptyMap(), state.context)
    }

    override fun retargetInvalidatedContext(context: EvaluationContext) {
        do {
            val currentState = atomicState.get() ?: return
            if (!currentState.isTombstone || currentState.context == context) return
            val retargetedState = currentState.copy(context = context)
        } while (!atomicState.compareAndSet(currentState, retargetedState))
    }

    override fun clearFlagsIfContextChanged(context: EvaluationContext) {
        do {
            val currentState = atomicState.get()
            if (currentState?.context == context) return
            // Reject late cache reads without accepting or persisting an empty configuration.
            val clearedState = FlagsState(
                context,
                emptyMap(),
                obfuscation = null,
                observeFullEvaluationData = false,
                hasAcceptedConfiguration = currentState?.hasAcceptedConfiguration == true &&
                    (currentState.obfuscation == null || obfuscationSupported),
                isTombstone = true
            )
        } while (!atomicState.compareAndSet(currentState, clearedState))
    }

    override fun getEvaluationContext(): EvaluationContext? {
        waitForPersistenceLoad()
        return readableState?.takeUnless { it.isTombstone }?.context
    }

    override fun hasConfiguration(): Boolean {
        waitForPersistenceLoad()
        return readableState?.isTombstone == false
    }

    override fun hasConfigurationForContext(context: EvaluationContext): Boolean =
        readableState?.let { it.context == context && !it.isTombstone } == true

    override fun getEvaluationSnapshot(key: String): FlagEvaluationSnapshot? {
        val state = readableState?.takeUnless { it.isTombstone } ?: return null
        val flag = state[key]
        return FlagEvaluationSnapshot(flag, state.context, state.observeFullEvaluationData, state.telemetryTargetingKey)
    }

    override fun waitForPersistenceLoad() {
        try {
            persistenceLoadedLatch.await(persistenceLoadTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            @Suppress("UnsafeThirdPartyFunctionCall") // Safe: self-interruption is always permitted
            Thread.currentThread().interrupt()
        }
    }

    private fun installPersistedState(persistedState: FlagsState): Boolean {
        do {
            val currentState = atomicState.get()
            if (currentState != null &&
                (!currentState.isTombstone || currentState.context != persistedState.context)
            ) {
                return false
            }
        } while (!atomicState.compareAndSet(currentState, persistedState))
        return true
    }

    private fun publishFirstFlags(state: FlagsState, installedKeys: Collection<String>) {
        if (state.obfuscation == null || obfuscationSupported) {
            // Encoded responses cannot enumerate the original application keys.
            val keys = if (state.obfuscation == null) installedKeys else emptySet()
            firstFlags.complete(keys, deliverFirstFlags)
        }
    }

    companion object {
        const val ERROR_SAVING_FLAGS_STATE = "Failed to save flags state to persistent storage"
        private const val PERSISTENCE_LOAD_TIMEOUT_MS = 100L
    }
}
