/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

/** Cancellation shared by image requests belonging to a discarded snapshot. */
internal class ResourceCancellation {
    private val lock = Any()
    private val listeners = mutableSetOf<() -> Unit>()
    private var cancelled = false

    val isCancelled: Boolean
        get() = synchronized(lock) { cancelled }

    fun register(listener: () -> Unit): () -> Unit {
        val invokeImmediately = synchronized(lock) {
            if (cancelled) {
                true
            } else {
                @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned mutable set; access is serialized.
                listeners.add(listener)
                false
            }
        }
        if (invokeImmediately) listener()
        return {
            synchronized(lock) {
                @Suppress("UnsafeThirdPartyFunctionCall") // SDK-owned mutable set; listener keys use identity equality.
                listeners.remove(listener)
            }
        }
    }

    fun cancel() {
        val callbacks = synchronized(lock) {
            cancelled = true
            listeners.toList().also { listeners.clear() }
        }
        callbacks.forEach { it() }
    }
}
