/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.logToUser
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal class TracerProvider internal constructor(
    private val localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> DatadogTracer,
    private val globalTracerProvider: () -> DatadogTracer?
) {
    private val localTracerReference: AtomicReference<DatadogTracer> = AtomicReference()

    @Synchronized
    @Suppress("UnsafeThirdPartyFunctionCall") // updateAndGet is safe
    fun provideTracer(
        sdkCore: InternalSdkCore,
        localHeaderTypes: Set<TracingHeaderType>,
        networkInstrumentationName: String
    ): DatadogTracer? {
        val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
        val globalTracerInstance = globalTracerProvider.invoke()
        return when {
            tracingFeature == null -> {
                sdkCore.internalLogger.logToUser(
                    level = InternalLogger.Level.WARN,
                    onlyOnce = true
                ) { WARNING_TRACING_DISABLED.format(Locale.US, networkInstrumentationName) }
                null
            }

            globalTracerInstance != null -> {
                // clear the localTracer reference if any
                localTracerReference.set(null)
                globalTracerInstance
            }

            else -> {
                // only register once
                if (localTracerReference.get() == null) {
                    @Suppress("UnsafeThirdPartyFunctionCall") // internal safe call
                    val globalHeaderTypes = sdkCore.firstPartyHostResolver.getAllHeaderTypes()
                    val allHeaders = localHeaderTypes.plus(globalHeaderTypes)
                    localTracerReference.compareAndSet(null, localTracerFactory(sdkCore, allHeaders))
                    sdkCore.internalLogger.logToUser(InternalLogger.Level.WARN) {
                        WARNING_DEFAULT_TRACER.format(Locale.US, networkInstrumentationName)
                    }
                }
                return localTracerReference.get()
            }
        }
    }

    internal companion object {
        const val WARNING_TRACING_DISABLED = "You added a TracingInstrumentation to your %s, " +
            "but you did not enable the TracingFeature. " +
            "Your requests won't be traced."

        const val WARNING_DEFAULT_TRACER =
            "You added a TracingInstrumentation to your %s instrumentation, " +
                "but you didn't register any AgentTracer.TracerAPI. " +
                "We automatically created a local tracer for you."
    }
}
