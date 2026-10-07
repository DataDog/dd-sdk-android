/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.persistence.FlagsPersistenceManager
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ResolutionReason
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class DefaultFlagsRepository(
    private val featureSdkCore: FeatureSdkCore,
    private val instanceName: String,
    private val dataStore: DataStoreHandler,
    private val internalLogger: InternalLogger = featureSdkCore.internalLogger,
    private val persistenceLoadTimeoutMs: Long = PERSISTENCE_LOAD_TIMEOUT_MS,
    private val deliverFirstFlags: (Runnable) -> Unit = Runnable::run
) : FlagsRepository {
    private data class FlagsState(
        val context: EvaluationContext,
        val flags: Map<String, PrecomputedFlag>,
        val obfuscation: FlagKeyObfuscation?,
        val restoredFromCache: Boolean = false
    ) {
        fun get(key: String): PrecomputedFlag? =
            if (obfuscation == null) flags[key] else obfuscation.encode(key)?.let { flags[it] }
    }
    private val atomicState = AtomicReference<FlagsState?>(null)
    private val obfuscationSupported = AtomicBoolean(false)

    // Preserve encoded cache data while source detection is pending, but do not expose it.
    private val readableState: FlagsState?
        get() = atomicState.get()?.takeIf { it.obfuscation == null || obfuscationSupported.get() }

    @Suppress("UnsafeThirdPartyFunctionCall") // CountDownLatch rejects negative counts; 1 is valid.
    private val persistenceLoadedLatch = CountDownLatch(1)
    override val firstFlags = FirstFlagsLatch()

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        val cachedState = try {
            persistedState?.let {
                FlagsState(
                    it.evaluationContext,
                    it.flags.mapValues { (_, flag) -> flag.copy(reason = ResolutionReason.CACHED.name) },
                    it.obfuscation,
                    restoredFromCache = true
                ).takeIf { state -> atomicState.compareAndSet(null, state) }
            }
        } finally {
            persistenceLoadedLatch.countDown()
        }
        if (cachedState != null && persistedState != null) {
            publishFirstFlags(cachedState, persistedState.flags.keys)
        }
    }

    init {
        featureSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)?.withContext { context ->
            setObfuscationSupported(context.source == "android")
        }
    }

    override fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        obfuscation: FlagKeyObfuscation?,
        onInstalled: () -> Unit
    ) {
        val newState = FlagsState(context, flags, obfuscation)

        val previousState = atomicState.getAndSet(newState)
        val firstInstallation = previousState == null ||
            (previousState.obfuscation != null && !obfuscationSupported.get())
        persistenceLoadedLatch.countDown()

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
                obfuscation = obfuscation
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

        try {
            onInstalled()
        } finally {
            if (firstInstallation) {
                publishFirstFlags(newState, flags.keys)
            }
        }
    }

    override fun setObfuscationSupported(supported: Boolean) {
        obfuscationSupported.set(supported)
        readableState?.takeIf { it.restoredFromCache }?.let { publishFirstFlags(it, it.flags.keys) }
    }

    private fun publishFirstFlags(state: FlagsState, installedKeys: Collection<String>) {
        if (state.obfuscation == null || obfuscationSupported.get()) {
            // Encoded responses cannot enumerate the original application keys.
            val keys = if (state.obfuscation == null) installedKeys else emptySet()
            firstFlags.complete(keys, deliverFirstFlags)
        }
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        waitForPersistenceLoad()
        val state = readableState
        if (state != null) {
            return state.get(key)
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
        val state = readableState
        if (state != null) {
            // The React Native bridge consumes original keys and cannot decode this representation.
            return if (state.obfuscation == null) state.flags else emptyMap()
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
        return readableState?.context
    }

    override fun hasFlags(): Boolean {
        waitForPersistenceLoad()
        return readableState?.flags?.isNotEmpty() ?: false
    }

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean {
        val state = readableState
        return state?.context == context && state.flags.isNotEmpty()
    }

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        waitForPersistenceLoad()
        val state = readableState ?: return null
        val flag = state.get(key) ?: return null
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
