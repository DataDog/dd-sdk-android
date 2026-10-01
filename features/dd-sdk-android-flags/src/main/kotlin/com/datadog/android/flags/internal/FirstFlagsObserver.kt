/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FirstFlagsCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.FlagsClientEventType
import com.datadog.android.flags.model.UnparsedFlag
import org.json.JSONObject
import java.util.Collections

/** Claims under the repository installation lock; delivery and disposal synchronize independently. */
internal class FirstFlagsObserver(
    private var callback: FirstFlagsCallback?,
    private val logger: InternalLogger,
    private var snapshotCallback: ((EvaluationContext, Map<String, UnparsedFlag>) -> Unit)? = null
) {
    private val lock = Any()
    private var client: FlagsClient? = null
    private var event: FlagsClientEvent? = null
    private var snapshot: Pair<EvaluationContext, Map<String, UnparsedFlag>>? = null
    private var claimed = false
    private var eligible = false
    private var disposed = false

    @Suppress("ReturnCount") // No work is captured for disposed, consumed or unobserved clients.
    fun claim(
        keys: Set<String>,
        snapshotFactory: (() -> Pair<EvaluationContext, Map<String, UnparsedFlag>>)? = null
    ): (() -> Unit)? {
        synchronized(lock) {
            if (claimed || disposed) return null
            if (callback == null && snapshotCallback == null) return null
            if (snapshotCallback != null) snapshot = snapshotFactory?.invoke()
            claimed = true
            event = FlagsClientEvent(
                type = FlagsClientEventType.CONFIGURATION_CHANGED,
                providerName = "Datadog Feature Flags Provider",
                flagsChanged = keys.toList()
            )
        }
        return ::release
    }

    fun attachClient(client: FlagsClient) {
        synchronized(lock) {
            if (disposed || (callback == null && snapshotCallback == null)) return
            this.client = client
        }
        deliver()
    }

    // Only the winning installation receives this release function.
    private fun release() {
        synchronized(lock) {
            if (disposed) return
            eligible = true
        }
        deliver()
    }

    fun dispose() {
        synchronized(lock) {
            disposed = true
            callback = null
            snapshotCallback = null
            snapshot = null
            client = null
            event = null
        }
    }

    @Suppress("ReturnCount") // Abort delivery until all eligibility conditions hold under the same lock.
    private fun deliver() {
        val delivery = synchronized(lock) {
            if (disposed || !eligible) return
            val target = client ?: return
            val handler = callback
            val snapshotHandler = snapshotCallback
            if (handler == null && snapshotHandler == null) return
            val value = event ?: return
            val firstSnapshot = snapshot
            // Taking both callbacks is the delivery-start linearization point versus disposal.
            callback = null
            snapshotCallback = null
            snapshot = null
            event = null
            client = null
            val action: () -> Unit = {
                if (handler != null) invokeSafely { handler.onFirstFlags(target, value) }
                if (snapshotHandler != null && firstSnapshot != null) {
                    invokeSafely { snapshotHandler(firstSnapshot.first, firstSnapshot.second) }
                }
            }
            action
        }
        delivery()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun invokeSafely(action: () -> Unit) {
        try {
            action()
        } catch (e: Exception) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "An onFirstFlags callback threw an exception." },
                e
            )
        }
    }

    companion object {
        @Suppress("UnsafeThirdPartyFunctionCall")
        fun snapshot(context: EvaluationContext, flags: Map<String, PrecomputedFlag>):
            Pair<EvaluationContext, Map<String, UnparsedFlag>> {
            val detachedContext = context.copy(attributes = Collections.unmodifiableMap(HashMap(context.attributes)))
            val detachedFlags = flags.mapValues { (_, flag) ->
                flag.copy(extraLogging = JSONObject(flag.extraLogging.toString()))
            }
            return detachedContext to Collections.unmodifiableMap(detachedFlags)
        }
    }
}
