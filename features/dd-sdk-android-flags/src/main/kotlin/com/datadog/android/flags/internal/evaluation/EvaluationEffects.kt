/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import com.datadog.android.api.InternalLogger

/** Delivers committed transitions in order, without holding the context policy lock. */
internal class EvaluationEffects(private val lock: Any, private val logger: InternalLogger) {
    private val pending = ArrayDeque<() -> Unit>()
    private var draining = false

    /** The caller holds [lock] while committing state and enqueuing its effects. */
    fun enqueue(effect: () -> Unit) {
        pending.addLast(effect)
    }

    fun drain() {
        synchronized(lock) {
            if (draining) return
            draining = true
        }
        while (true) {
            val effect = synchronized(lock) {
                pending.removeFirstOrNull().also { if (it == null) draining = false }
            } ?: return
            notifySafely(effect)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Customer listeners and callbacks must not stop later state delivery.
    fun notifySafely(action: () -> Unit) {
        try {
            action()
        } catch (throwable: Throwable) {
            logger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.USER,
                { "A feature flags state listener or evaluation callback failed." },
                throwable
            )
        }
    }
}
