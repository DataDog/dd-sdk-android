/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.tracing

import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer

/**
 * An interface enabling Datadog tracing features.
 *
 * It allows you to create a custom DDTracer which can be used to create and use a [DDSpan]
 * for tracing your application workflow.
 *
 * You can have multiple loggers configured in your application, each with their own settings.
 */
interface TracerBuilder {
    /**
     * Builds a [DDTracer] based on the current state of this Builder.
     */
    fun build(): DDTracer
}
