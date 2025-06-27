/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.trace.internal.data

import com.datadog.legacy.trace.common.writer.Writer
import com.datadog.opentracing.DDSpan

internal class NoOpWriter : Writer {
    override fun close() {
        // no-op
    }

    override fun write(trace: MutableList<DDSpan>?) {
        // no-op
    }

    override fun start() {
        // no-op
    }

    override fun incrementTraceCount() {
        // no-op
    }
}
