/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.trace.api

import com.datadog.android.trace.api.tracer.DatadogTracer

object GlobalDatadogTracerHolder {
    @get:Synchronized
    var tracer: DatadogTracer? = null
        private set

    @Synchronized
    fun registerIfAbsent(tracer: DatadogTracer) {
        if (this.tracer == null) {
            this.tracer = tracer
        }
    }
}
