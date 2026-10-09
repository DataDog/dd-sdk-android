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
import com.datadog.android.flags.internal.diagnostics.StartupTrace
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
    private val persistenceLoadTimeoutMs: Long = PERSISTENCE_LOAD_TIMEOUT_MS,
    private val deliverFirstFlags: (Runnable) -> Unit = Runnable::run
) : FlagsRepository {
    private data class FlagsState(
        val context: EvaluationContext,
        val flags: Map<String, PrecomputedFlag>,
        val source: String,
        val diagnosticId: Long = StartupTrace.nextId()
    )
    private val atomicState = AtomicReference<FlagsState?>(null)

    @Suppress("UnsafeThirdPartyFunctionCall") // CountDownLatch rejects negative counts; 1 is valid.
    private val persistenceLoadedLatch = CountDownLatch(1)
    override val firstFlags = FirstFlagsLatch()

    private val persistenceManager = FlagsPersistenceManager(
        dataStore = dataStore,
        instanceName = instanceName,
        internalLogger = internalLogger
    ) { persistedState ->
        val installed = try {
            val candidate = persistedState?.let {
                FlagsState(
                    persistedState.evaluationContext,
                    persistedState.flags.mapValues { (_, flag) -> flag.copy(reason = ResolutionReason.CACHED.name) },
                    "disk"
                )
            }
            val accepted = candidate != null && StartupTrace.span("state.cache_cas", { stateProperties(candidate) }) {
                atomicState.compareAndSet(null, candidate)
            }
            StartupTrace.event("state.install") {
                stateProperties(candidate) + (INSTALLED to accepted.toString())
            }
            accepted
        } finally {
            releasePersistence("disk_callback")
        }
        StartupTrace.event("cache.install_result") {
            mapOf(StartupTrace.REPOSITORY to StartupTrace.id(this), "installed" to installed.toString())
        }
        if (installed && persistedState != null) {
            firstFlags.complete(persistedState.flags.keys, deliverFirstFlags)
        }
    }

    override fun setFlagsAndContext(
        context: EvaluationContext,
        flags: Map<String, PrecomputedFlag>,
        onInstalled: () -> Unit
    ) {
        val newState = FlagsState(context, flags, "network")

        val previousState = StartupTrace.span("state.network_swap", { stateProperties(newState) }) {
            atomicState.getAndSet(newState)
        }
        val firstInstallation = previousState == null
        StartupTrace.event("state.install") {
            stateProperties(newState) + mapOf(
                INSTALLED to "true",
                "previous_state" to (previousState?.diagnosticId ?: 0L).toString()
            )
        }
        releasePersistence("network_installation")

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
            StartupTrace.span("network.notify", { stateProperties(newState) }) { onInstalled() }
        } finally {
            if (firstInstallation) {
                firstFlags.complete(flags.keys, deliverFirstFlags)
            }
        }
    }

    override fun getPrecomputedFlag(key: String): PrecomputedFlag? {
        waitForPersistenceLoad("getPrecomputedFlag")
        val state = atomicState.get()
        StartupTrace.event(StartupTrace.STATE_READ) {
            stateProperties(state) +
                (StartupTrace.CALLER to "getPrecomputedFlag")
        }
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
        waitForPersistenceLoad("getFlagsSnapshot")
        val state = atomicState.get()
        StartupTrace.event(StartupTrace.STATE_READ) {
            stateProperties(state) +
                (StartupTrace.CALLER to "getFlagsSnapshot")
        }
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
        waitForPersistenceLoad("getEvaluationContext")
        val state = atomicState.get()
        StartupTrace.event(StartupTrace.STATE_READ) {
            stateProperties(state) +
                (StartupTrace.CALLER to "getEvaluationContext")
        }
        return state?.context
    }

    override fun hasFlags(): Boolean {
        waitForPersistenceLoad("hasFlags")
        val state = atomicState.get()
        StartupTrace.event(StartupTrace.STATE_READ) { stateProperties(state) + (StartupTrace.CALLER to "hasFlags") }
        return state?.flags?.isNotEmpty() ?: false
    }

    override fun hasLoadedFlagsForContext(context: EvaluationContext): Boolean {
        val state = atomicState.get()
        return state?.context == context && state.flags.isNotEmpty()
    }

    @Suppress("ReturnCount")
    override fun getPrecomputedFlagWithContext(key: String): Pair<PrecomputedFlag, EvaluationContext>? {
        waitForPersistenceLoad("getPrecomputedFlagWithContext")
        val state = atomicState.get()
        StartupTrace.event(StartupTrace.STATE_READ) {
            stateProperties(state) +
                (StartupTrace.CALLER to "getPrecomputedFlagWithContext")
        }
        if (state == null) return null
        val flag = state.flags[key] ?: return null
        return flag to state.context
    }

    private fun stateProperties(state: FlagsState?): Map<String, String> = mapOf(
        StartupTrace.REPOSITORY to StartupTrace.id(this),
        "state" to (state?.diagnosticId ?: 0L).toString(),
        "source" to (state?.source ?: "none"),
        "count" to (state?.flags?.size ?: 0).toString()
    )

    private fun releasePersistence(source: String) {
        StartupTrace.span("latch.release", {
            mapOf(StartupTrace.REPOSITORY to StartupTrace.id(this), "source" to source)
        }) {
            persistenceLoadedLatch.countDown()
        }
    }

    private fun waitForPersistenceLoad(caller: String) {
        val span = StartupTrace.begin("latch.wait") {
            mapOf(StartupTrace.REPOSITORY to StartupTrace.id(this), StartupTrace.CALLER to caller)
        }
        try {
            val completed = persistenceLoadedLatch.await(persistenceLoadTimeoutMs, TimeUnit.MILLISECONDS)
            StartupTrace.event("latch.result") { mapOf("outcome" to if (completed) "completed" else "timeout") }
        } catch (e: InterruptedException) {
            StartupTrace.event("latch.result") { mapOf("outcome" to "interrupted") }
            @Suppress("UnsafeThirdPartyFunctionCall") // Safe: self-interruption is always permitted
            Thread.currentThread().interrupt()
        } finally {
            StartupTrace.end(span)
        }
    }

    companion object {
        private const val INSTALLED = "accepted"
        const val WARN_CONTEXT_NOT_SET = "You must call FlagsClientManager.get().setEvaluationContext " +
            "in order to have flags available"
        const val ERROR_SAVING_FLAGS_STATE = "Failed to save flags state to persistent storage"
        private const val PERSISTENCE_LOAD_TIMEOUT_MS = 100L
    }
}
