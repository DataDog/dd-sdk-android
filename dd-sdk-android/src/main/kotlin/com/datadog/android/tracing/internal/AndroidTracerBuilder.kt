package com.datadog.android.tracing.internal

import com.datadog.android.core.internal.data.Writer
import com.datadog.android.tracing.TracerBuilder
import com.datadog.android.tracing.internal.data.TracesWriter
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import java.util.Properties

internal class AndroidTracerBuilder(
    val serviceName: String,
    val writer: Writer<DDSpan>
) : TracerBuilder {
    companion object {
        private const val SERVICE_NAME = "service.name"
    }

    private val properties: Properties = Properties()

    init {
        properties.setProperty(SERVICE_NAME, serviceName)
    }

    override fun build(): DDTracer {
        return DDTracer(Config.get(), TracesWriter(writer))
    }

    internal fun config() = Config.get(properties)
}
