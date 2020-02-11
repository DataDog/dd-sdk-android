package com.datadog.android.tracing.internal.data

import datadog.opentracing.DDSpan
import datadog.trace.common.writer.Writer

internal class TraceWriter(
    val writer: com.datadog.android.core.internal.data.Writer<DDSpan>
) : Writer {

    override fun start() {
        // NO - OP
    }

    override fun write(trace: MutableList<DDSpan>?) {
        trace?.let {
            writer.write(it)
        }
    }

    override fun close() {
        // NO - OP
    }

    override fun incrementTraceCount() {
        // NO - OP
    }
}
