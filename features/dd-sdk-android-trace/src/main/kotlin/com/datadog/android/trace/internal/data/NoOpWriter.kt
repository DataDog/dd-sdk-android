/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal.data

import com.datadog.opentracing.DDSpan
import com.datadog.trace.common.writer.Writer

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
