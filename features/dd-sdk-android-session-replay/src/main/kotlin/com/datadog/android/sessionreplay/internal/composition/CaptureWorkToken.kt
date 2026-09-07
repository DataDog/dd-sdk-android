/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import java.util.concurrent.atomic.AtomicBoolean

/** Callback identity allocated from, and invalidated with, one generation scope. */
internal class CaptureWorkToken internal constructor(
    private val context: CaptureGenerationContext,
    private val registry: GenerationWorkRegistry
) {
    private val valid = AtomicBoolean(true)

    fun isValid(): Boolean = valid.get() && context.isActive()

    fun complete(): Boolean {
        if (!valid.compareAndSet(true, false)) return false
        registry.release(this)
        return context.isActive()
    }

    internal fun invalidate() {
        valid.set(false)
    }
}
