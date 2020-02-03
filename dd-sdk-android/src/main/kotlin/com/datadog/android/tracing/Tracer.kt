/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.tracing.internal.TracesFeature
import com.datadog.android.tracing.internal.data.TraceWriter
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

        private var serviceName: String = TracesFeature.serviceName
        private var partialFlushThreshold = DEFAULT_PARTIAL_MIN_FLUSH

        // region Public API

        /**
         * Builds a [Tracer] based on the current state of this Builder.
         */
        fun build(): Tracer {
            return Tracer(
                config(),
                TraceWriter(TracesFeature.persistenceStrategy.getWriter())
            )
        }

        /**
         * Sets the service name that will appear in your traces.
         * @param serviceName the service name (default = "android")
         */
        fun setServiceName(serviceName: String): Builder {
            this.serviceName = serviceName
            return this
        }

        /**
         * Sets the partial flush threshold. When this threshold is reached (you have a specific
         * amount of spans closed waiting) the flush mechanism will be triggered and all the pending
         * closed spans will be processed in order to be sent to the intake.
         * @param threshold the threshold value (default = 5)
         */
        fun setPartialFlushThreshold(threshold: Int): Builder {
            this.partialFlushThreshold = threshold
            return this
        }

        // endregion

        // region Internal

        internal fun properties(): Properties {
            val properties = Properties()
            properties.setProperty(Config.SERVICE_NAME, serviceName)
            properties.setProperty(
                Config.PARTIAL_FLUSH_MIN_SPANS,
                partialFlushThreshold.toString()
            )
            return properties
        }

        private fun config(): Config {
            return Config.get(properties())
        }

        // endregion

        companion object {
            // the minimum closed spans required for triggering a flush and deliver
            // everything to the writer
            internal const val DEFAULT_PARTIAL_MIN_FLUSH = 5
        }
    }
}
