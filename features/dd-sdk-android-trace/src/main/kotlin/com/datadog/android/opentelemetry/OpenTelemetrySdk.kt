/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators

class OpenTelemetrySdk(private val tracerProvider: TracerProvider) : OpenTelemetry {

    override fun getTracerProvider(): TracerProvider {
        return this.tracerProvider
    }

    override fun getPropagators(): ContextPropagators {
        TODO("Not yet implemented")
    }

    class Builder {

        private var traceProvider: TracerProvider = TracerProvider.noop()

        fun setTracerProvider(traceProvider: TracerProvider): Builder {
            this.traceProvider = traceProvider
            return this
        }

        fun build(): OpenTelemetrySdk {
            return OpenTelemetrySdk(traceProvider)
        }
    }
}
