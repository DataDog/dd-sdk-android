package com.datadog.android.tracing.internal.data

import datadog.opentracing.DDSpan
import datadog.trace.common.writer.Writer

internal class TracesWriter(
    val writer: com.datadog.android.core.internal.data.Writer<DDSpan>
) : Writer {

    override fun start() {
        // NO - OP
    }

    override fun write(trace: MutableList<DDSpan>?) {
        // TODO: RUM-184 Modify the Writer to accept also a list of models
        trace?.forEach{
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
