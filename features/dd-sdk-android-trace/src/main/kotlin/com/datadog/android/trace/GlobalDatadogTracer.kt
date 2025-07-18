/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace

import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.api.tracer.NoOpDatadogTracer
import java.util.concurrent.atomic.AtomicReference

/**
 * A holder object for managing and retrieving a global instance of the [DatadogTracer].
 *
 * This object is used to share same instance of [DatadogTracer] across different integrations such as
 * `OkHttp`, Kotlin's coroutines, ect.
 */
object GlobalDatadogTracer {

    private val instance = AtomicReference<DatadogTracer?>()

    /**
     * Registers the provided tracer as the global tracer if no tracer is currently registered.
     *
     * @param tracer The tracer to register as the global tracer.
     * @return `true` if the tracer was successfully registered, or `false` if a tracer was already registered.
     */
    @Synchronized
    fun registerIfAbsent(tracer: DatadogTracer): Boolean {
        return instance.compareAndSet(null, tracer)
    }

    /**
     * Retrieves the current active tracer for Datadog, or a no-operation tracer if none is active.
     *
     * @return The current instance of [DatadogTracer] if available. Otherwise, an instance of
     * [NoOpDatadogTracer] that performs no operations.
     */
    fun get(): DatadogTracer = getOrNull() ?: NoOpDatadogTracer()

    /**
     * Retrieves the current instance of the DatadogTracer, if available.
     *
     * @return An instance of [DatadogTracer] or null.
     */
    fun getOrNull(): DatadogTracer? = instance.get()

    /**
     * Clears the current instance of the global Datadog tracer.
     *
     * This method sets the internal tracer instance to null, effectively
     * removing any active tracer currently held in the global state.
     * The general purpose is to use it for test implementation.
     */
    @Synchronized
    fun clear() {
        instance.set(null)
    }
}
