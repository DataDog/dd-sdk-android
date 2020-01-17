/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.tracing.internal.data.TracesWriter
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import java.util.Properties

/**
 * Used to provide a [TracerBuilder] which will produce a [Tracer].

 * The [Tracer] instance will be used to use the Datadog
 * tracing features throughout your application.
 *
 * You can have multiple tracers configured in your application, each with their own settings.
 */
class Tracer internal constructor(config: Config, writer: TracesWriter) : DDTracer(config, writer) {

    companion object {
        internal const val DEFAULT_SERVICE_NAME = "android"
    }

    /**
     * Used to provide a [TracerBuilder] which will produce a [DDTracer].

     * The [DDTracer] instance will be used to use the Datadog
     * tracing features throughout your application.
     *
     * You can have multiple tracers configured in your application, each with their own settings.
     */
    class TracerBuilder {
        companion object {
            private const val SERVICE_NAME_KEY = "service.name"
        }

        private var serviceName: String = DEFAULT_SERVICE_NAME
        private val writer: Writer<DDSpan> = Datadog.getTracingStrategy().getWriter()
        private val properties: Properties = Properties()

        // region Public API

        /**
         * Builds a [Tracer] based on the current state of this Builder.
         */
        fun build(): Tracer {
            updateProperties()
            return Tracer(Config.get(), TracesWriter(writer))
        }

        /**
         * Sets the service name that will appear in your traces.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): TracerBuilder {
            this.serviceName = serviceName
            return this
        }

        // endregion

        // region Internal

        private fun updateProperties() {
            properties.setProperty(SERVICE_NAME_KEY, serviceName)
        }

        // endregion


        internal fun config() = Config.get(properties)
    }
}
