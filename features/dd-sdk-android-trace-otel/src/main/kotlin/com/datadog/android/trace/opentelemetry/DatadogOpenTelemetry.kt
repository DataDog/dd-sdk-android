/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.propagation.ContextPropagators

class DatadogOpenTelemetry(serviceName: String) : OpenTelemetry {

    private val tracerProvider = OtelTracerProvider.Builder()
        .setService(serviceName)
        .build()

    override fun getTracerProvider(): TracerProvider = tracerProvider

    override fun getPropagators(): ContextPropagators = ContextPropagators.noop()
}
