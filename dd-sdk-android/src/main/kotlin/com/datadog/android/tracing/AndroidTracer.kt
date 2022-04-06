/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing

import com.datadog.android.Datadog
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.utils.devLogger
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.Logger
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.tracing.internal.TracingFeature
import com.datadog.android.tracing.internal.data.TraceWriter
import com.datadog.android.tracing.internal.handlers.AndroidSpanLogsHandler
import com.datadog.opentracing.DDTracer
import com.datadog.opentracing.LogHandler
import com.datadog.trace.api.Config
import io.opentracing.Span
import io.opentracing.log.Fields
import java.security.SecureRandom
import java.util.Properties
import java.util.Random

/**
 *  A class enabling Datadog tracing features.
 *
 * It allows you to create [ DDSpan ] and send them to Datadog servers.
 *
 * You can have multiple tracers configured in your application, each with their own settings.
 *
 */
class AndroidTracer internal constructor(
    config: Config,
    writer: TraceWriter,
    random: Random,
    private val logsHandler: LogHandler,
    private val bundleWithRum: Boolean
) : DDTracer(config, writer, random) {

    // region Tracer

    override fun buildSpan(operationName: String): DDSpanBuilder {
        return DDSpanBuilder(operationName, scopeManager())
            .withLogHandler(logsHandler)
            .withRumContext()
    }

    // endregion

    /**
     * Builds a [AndroidTracer] instance.
     *
     */
    class Builder
    internal constructor(private val logsHandler: LogHandler) {

        private var bundleWithRumEnabled: Boolean = true
        private var serviceName: String = CoreFeature.serviceName
        private var partialFlushThreshold = DEFAULT_PARTIAL_MIN_FLUSH
        private var random: Random = SecureRandom()

        private val globalTags: MutableMap<String, String> = mutableMapOf()

        constructor() : this(
            AndroidSpanLogsHandler(
                Logger.Builder().setLoggerName(TRACE_LOGGER_NAME).build()
            )
        )

        // region Public API

        /**
         * Builds a [AndroidTracer] based on the current state of this Builder.
         */
        fun build(): AndroidTracer {
            if (!TracingFeature.isInitialized()) {
                devLogger.e(
                    TRACING_NOT_ENABLED_ERROR_MESSAGE + "\n" +
                        Datadog.MESSAGE_SDK_INITIALIZATION_GUIDE
                )
            }
            if (bundleWithRumEnabled && !RumFeature.isInitialized()) {
                devLogger.e(RUM_NOT_ENABLED_ERROR_MESSAGE)
                bundleWithRumEnabled = false
            }
            return AndroidTracer(
                config(),
                TraceWriter(TracingFeature.persistenceStrategy.getWriter()),
                random,
                logsHandler,
                bundleWithRumEnabled
            )
        }

        /**
         * Sets the service name that will appear in your traces.
         * @param serviceName the service name (default = application package name)
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

        /**
         * Adds a global tag which will be appended to all spans created with the built tracer.
         * @param key the tag key
         * @param value the tag value
         */
        fun addGlobalTag(key: String, value: String): Builder {
            this.globalTags[key] = value
            return this
        }

        /**
         * Enables the trace bundling with the current active View. If this feature is enabled all
         * the spans from this moment on will be bundled with the current view information and you
         * will be able to see all the traces sent during a specific view in the Rum Explorer.
         * @param enabled true by default
         */
        fun setBundleWithRumEnabled(enabled: Boolean): Builder {
            bundleWithRumEnabled = enabled
            return this
        }

        // endregion

        // region Internal

        internal fun withRandom(random: Random): Builder {
            this.random = random
            return this
        }

        internal fun properties(): Properties {
            val properties = Properties()
            properties.setProperty(Config.SERVICE_NAME, serviceName)
            properties.setProperty(
                Config.PARTIAL_FLUSH_MIN_SPANS,
                partialFlushThreshold.toString()
            )
            properties.setProperty(
                Config.TAGS,
                globalTags.map { "${it.key}:${it.value}" }.joinToString(",")
            )
            return properties
        }

        private fun config(): Config {
            return Config.get(properties())
        }

        // endregion
    }

    // region Internal

    private fun DDSpanBuilder.withRumContext(): DDSpanBuilder {
        return if (bundleWithRum) {
            val rumContext = GlobalRum.getRumContext()
            withTag(LogAttributes.RUM_APPLICATION_ID, rumContext.applicationId)
                .withTag(LogAttributes.RUM_SESSION_ID, rumContext.sessionId)
                .withTag(LogAttributes.RUM_VIEW_ID, rumContext.viewId)
                .withTag(LogAttributes.RUM_ACTION_ID, rumContext.actionId)
        } else {
            this
        }
    }

    // endregion

    companion object {
        internal const val TRACING_NOT_ENABLED_ERROR_MESSAGE =
            "You're trying to create an AndroidTracer instance, " +
                "but either the SDK was not initialized or the Tracing feature was " +
                "disabled in your Configuration. No tracing data will be sent."
        internal const val RUM_NOT_ENABLED_ERROR_MESSAGE =
            "You're trying to bundle the traces with a RUM context, " +
                "but the RUM feature was disabled in your Configuration. " +
                "No RUM context will be attached to your traces in this case."

        // the minimum closed spans required for triggering a flush and deliver
        // everything to the writer
        internal const val DEFAULT_PARTIAL_MIN_FLUSH = 5

        internal const val TRACE_LOGGER_NAME = "trace"

        internal const val TRACE_ID_BIT_SIZE = 63

        /**
         * Helper method to attach a Throwable to a specific Span.
         * The Throwable information (class name, message and stacktrace) will be added to the
         * provided Span as standard Error Tags.
         * @param span the active Span
         * @param throwable the Throwable you wan to log
         */
        @JvmStatic
        fun logThrowable(span: Span, throwable: Throwable) {
            val fieldsMap = mapOf(Fields.ERROR_OBJECT to throwable)
            span.log(fieldsMap)
        }

        /**
         * Helper method to attach an error message to a specific Span.
         * The error message will be added to the provided Span as a standard Error Tag.
         * @param span the active Span
         * @param message the error message you want to attach
         */
        @JvmStatic
        fun logErrorMessage(span: Span, message: String) {
            val fieldsMap = mapOf(Fields.MESSAGE to message)
            span.log(fieldsMap)
        }
    }
}
