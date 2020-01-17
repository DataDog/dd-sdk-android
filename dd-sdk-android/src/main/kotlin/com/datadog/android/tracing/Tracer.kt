/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.Datadog
import com.datadog.android.core.internal.data.Writer
import com.datadog.android.tracing.internal.data.TraceWriter
import datadog.opentracing.DDSpan
import datadog.opentracing.DDTracer
import datadog.trace.api.Config
import java.util.Properties

/**
 *  A class enabling Datadog tracing features.
 *
 * It allows you to create [ DDSpan ] and send them to Datadog servers.
 *
 * You can have multiple tracers configured in your application, each with their own settings.
 *
 */
class Tracer internal constructor(config: Config, writer: TraceWriter) : DDTracer(config, writer) {

    /**
     * Builds a [Tracer] instance.
     *
     */
    class Builder {

        private var serviceName: String = DEFAULT_SERVICE_NAME
        private val writer: Writer<DDSpan> = Datadog.getTracingStrategy().getWriter()

        // region Public API

        /**
         * Builds a [Tracer] based on the current state of this Builder.
         */
        fun build(): Tracer {
            return Tracer(config(), TraceWriter(writer))
        }

        /**
         * Sets the service name that will appear in your traces.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): Builder {
            this.serviceName = serviceName
            return this
        }

        // endregion

        // region Internal

        private fun properties(): Properties {
            val properties = Properties()
            properties.setProperty(SERVICE_NAME_KEY, serviceName)
            return properties
        }

        private fun config() = Config.get(properties())

        // endregion
        companion object {
            private const val SERVICE_NAME_KEY = "service.name"
            internal const val DEFAULT_SERVICE_NAME = "android"
        }
    }
}
