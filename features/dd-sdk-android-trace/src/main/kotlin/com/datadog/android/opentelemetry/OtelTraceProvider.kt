/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.opentelemetry

import androidx.annotation.FloatRange
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.opentelemetry.trace.OtelSpanBuilder
import com.datadog.android.opentelemetry.trace.OtelTracerBuilder
import com.datadog.android.trace.AndroidTracer
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.internal.TracingFeature
import com.datadog.android.trace.internal.data.NoOpOtelWriter
import com.datadog.trace.api.Config
import datadog.trace.api.IdGenerationStrategy
import datadog.trace.bootstrap.instrumentation.api.AgentTracer
import datadog.trace.core.CoreTracer
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider
import java.security.SecureRandom
import java.util.Properties
import java.util.Random

class OtelTraceProvider(private val coreTracer: AgentTracer.TracerAPI) : TracerProvider {

    //  internal const val TRACE_ID_BIT_SIZE = 63
    private val tracers: MutableMap<String, Tracer> = mutableMapOf()

    override fun get(instrumentationName: String): Tracer {
        this.tracers.putIfAbsent(instrumentationName, tracerBuilder(instrumentationName).build())
        return tracers[instrumentationName]!!
    }

    override fun get(instrumentationName: String, instrumentationVersion: String): Tracer {
        this.tracers.putIfAbsent(
        instrumentationName,
            tracerBuilder(instrumentationName)
            .setInstrumentationVersion(instrumentationVersion)
            .build()
        )
        return tracers[instrumentationName]!!
    }

    override fun tracerBuilder(instrumentationScopeName: String): TracerBuilder {
        var instrumentationScopeName = instrumentationScopeName
        if (instrumentationScopeName.trim { it <= ' ' }.isEmpty()) {
            // TODO: RUMM-0000 add a proper log here
            instrumentationScopeName = DEFAULT_TRACER_NAME
        }
        return OtelTracerBuilder(instrumentationScopeName, coreTracer)
    }

    class Builder
    internal constructor(
        private val sdkCore: FeatureSdkCore
    ) {

        private var tracingHeaderTypes: Set<TracingHeaderType> =
            setOf(TracingHeaderType.DATADOG, TracingHeaderType.TRACECONTEXT)
        private var bundleWithRumEnabled: Boolean = true
        private var sampleRate: Double = DEFAULT_SAMPLE_RATE

        // TODO RUMM-0000 should have a nicer call chain
        private var serviceName: String = ""
            get() {
                return field.ifEmpty {
                    val service = sdkCore.service
                    if (service.isEmpty()) {
                        sdkCore.internalLogger.log(
                            InternalLogger.Level.ERROR,
                            InternalLogger.Target.USER,
                            { DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE }
                        )
                    }
                    service
                }
            }
        private var partialFlushThreshold = DEFAULT_PARTIAL_MIN_FLUSH
        private var random: Random = SecureRandom()

        private val globalTags: MutableMap<String, String> = mutableMapOf()

        /**
         * @param sdkCore SDK instance to bind to. If not provided, default instance will be used.
         */
        @JvmOverloads
        constructor(sdkCore: SdkCore = Datadog.getInstance()) : this(sdkCore as FeatureSdkCore)

        // region Public API

        /**
         * Builds a [AndroidTracer] based on the current state of this Builder.
         */
        fun build(): OtelTraceProvider {
            val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)
                ?.unwrap<TracingFeature>()
            val rumFeature = sdkCore.getFeature(Feature.RUM_FEATURE_NAME)

            if (tracingFeature == null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.ERROR,
                    InternalLogger.Target.USER,
                    { TRACING_NOT_ENABLED_ERROR_MESSAGE }
                )
            }

            if (bundleWithRumEnabled && rumFeature == null) {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { RUM_NOT_ENABLED_ERROR_MESSAGE }
                )
                bundleWithRumEnabled = false
            }
            // TODO: RUM-0000 Add a logs handler here maybe
            val coreTracer = CoreTracer.CoreTracerBuilder()
                .withProperties(properties())
                .serviceName(serviceName)
                .writer(tracingFeature?.otelDataWriter ?: NoOpOtelWriter())
                .idGenerationStrategy(IdGenerationStrategy.fromName("SECURE_RANDOM", false))
                .build()
            return OtelTraceProvider(coreTracer)
        }

        /**
         * Sets the tracing header styles that may be injected by this tracer.
         * @param headerTypes the list of header types injected (default = datadog style headers)
         */
        fun setTracingHeaderTypes(headerTypes: Set<TracingHeaderType>): Builder {
            this.tracingHeaderTypes = headerTypes
            return this
        }

        /**
         * Sets the service name that will appear in your traces.
         * @param service the service name (default = application package name)
         */
        fun setService(service: String): Builder {
            this.serviceName = service
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
         *
         * @param key the tag key
         * @param value the tag value
         */
        @Deprecated(
            replaceWith = ReplaceWith("addTag"),
            message = "addGlobalTag is deprecated, please use addTag instead",
            level = DeprecationLevel.WARNING
        )
        fun addGlobalTag(key: String, value: String): Builder {
            return addTag(key, value)
        }

        /**
         * Adds a global tag which will be appended to all spans created with the built tracer.
         * @param key the tag key
         * @param value the tag value
         */
        fun addTag(key: String, value: String): Builder {
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

        /**
         * Sets the sample rate of spans.
         * @param sampleRate the sample rate as a percentage between 0 and 100 (default is 100%)
         */
        fun setSampleRate(
            @FloatRange(from = 0.0, to = 100.0) sampleRate: Double
        ): Builder {
            this.sampleRate = sampleRate
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
            properties.setProperty(
                Config.TRACE_SAMPLE_RATE,
                (sampleRate / DEFAULT_SAMPLE_RATE).toString()
            )

            val propagationStyles = tracingHeaderTypes.joinToString(",")
            properties.setProperty(Config.PROPAGATION_STYLE_EXTRACT, propagationStyles)
            properties.setProperty(Config.PROPAGATION_STYLE_INJECT, propagationStyles)

            return properties
        }

        private fun config(): Config {
            return Config.get(properties())
        }

        // endregion
    }

    // region Internal

    private fun OtelSpanBuilder.withRumContext(): OtelSpanBuilder {
//        return if (bundleWithRum) {
//            val rumContext = sdkCore.getFeatureContext(Feature.RUM_FEATURE_NAME)
//            withTag(LogAttributes.RUM_APPLICATION_ID, rumContext["application_id"] as? String)
//                .withTag(LogAttributes.RUM_SESSION_ID, rumContext["session_id"] as? String)
//                .withTag(LogAttributes.RUM_VIEW_ID, rumContext["view_id"] as? String)
//                .withTag(LogAttributes.RUM_ACTION_ID, rumContext["action_id"] as? String)
//        } else {
//            this
//        }
        return this
    }

    // endregion

    override fun toString(): String {
        return "AndroidTracer/${super.toString()}"
    }

    companion object {
        internal const val DEFAULT_TRACER_NAME = "android"
        internal const val DEFAULT_SAMPLE_RATE = 100.0

        internal const val TRACING_NOT_ENABLED_ERROR_MESSAGE =
            "You're trying to create an AndroidTracer instance, " +
                    "but either the SDK was not initialized or the Tracing feature was " +
                    "disabled in your Configuration. No tracing data will be sent."
        internal const val RUM_NOT_ENABLED_ERROR_MESSAGE =
            "You're trying to bundle the traces with a RUM context, " +
                    "but the RUM feature was disabled in your Configuration. " +
                    "No RUM context will be attached to your traces in this case."
        internal const val DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE =
            "Default service name is missing during" +
                    " Builder creation, did you initialize SDK?"

        // the minimum closed spans required for triggering a flush and deliver
        // everything to the writer
        internal const val DEFAULT_PARTIAL_MIN_FLUSH = 5

        // TODO: RUM-0000 provide these methods equivalents also for OtelSpan
//
//        /**
//         * Helper method to attach a Throwable to a specific Span.
//         * The Throwable information (class name, message and stacktrace) will be added to the
//         * provided Span as standard Error Tags.
//         * @param span the active Span
//         * @param throwable the Throwable you wan to log
//         */
//        @JvmStatic
//        fun logThrowable(span: Span, throwable: Throwable) {
//            val fieldsMap = mapOf(Fields.ERROR_OBJECT to throwable)
//            span.log(fieldsMap)
//        }

//        /**
//         * Helper method to attach an error message to a specific Span.
//         * The error message will be added to the provided Span as a standard Error Tag.
//         * @param span the active Span
//         * @param message the error message you want to attach
//         */
//        @JvmStatic
//        fun logErrorMessage(span: Span, message: String) {
//            val fieldsMap = mapOf(Fields.MESSAGE to message)
//            span.log(fieldsMap)
//        }
    }
}
