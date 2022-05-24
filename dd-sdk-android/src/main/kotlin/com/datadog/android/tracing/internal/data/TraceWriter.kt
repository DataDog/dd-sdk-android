/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.data

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.opentracing.DDSpan
import com.datadog.trace.common.writer.Writer

internal class TraceWriter(
    val writer: DataWriter<DDSpan>
) : Writer {

    // region Writer
    override fun start() {
        // NO - OP
    }

    override fun write(trace: MutableList<DDSpan>?) {
        trace?.let {
            @Suppress("ThreadSafety") // TODO RUMM-1503 delegate to another thread
            writer.write(it)
        }
    }

    override fun close() {
        // NO - OP
    }

    override fun incrementTraceCount() {
        // NO - OP
    }

    // endregion
}
