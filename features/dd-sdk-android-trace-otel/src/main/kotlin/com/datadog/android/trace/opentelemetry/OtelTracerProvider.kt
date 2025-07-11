/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.opentelemetry

import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.TracingHeaderType
import com.datadog.android.trace.api.tracer.DatadogTracer
import com.datadog.android.trace.internal.DatadogTracingToolkit.setTraceId128BitGenerationEnabled
import com.datadog.android.trace.opentelemetry.internal.DatadogContextStorageWrapper
import com.datadog.android.trace.opentelemetry.internal.executeIfJavaFunctionPackageExists
import com.datadog.opentelemetry.trace.OtelTracerBuilder
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.api.trace.TracerBuilder
import io.opentelemetry.api.trace.TracerProvider
import io.opentelemetry.context.ContextStorage
import java.util.Locale

/**
 *  A class enabling Datadog OpenTelemetry features.
 *
 * It allows you to create [TracerProvider].
 *
 * You can have multiple [TracerProvider]s configured in your application, each with their own settings.
 *
 */
class OtelTracerProvider internal constructor(
    private val datadogTracer: DatadogTracer,
    private val internalLogger: InternalLogger
) : TracerProvider {

    private val tracers: MutableMap<String, Tracer> = mutableMapOf()
    override fun get(instrumentationName: String): Tracer {
        val tracer = tracers[instrumentationName]
        return if (tracer == null) {
            val newTracer = tracerBuilder(instrumentationName).build()
            tracers[instrumentationName] = newTracer
            newTracer
        } else {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.USER,
                { TRACER_ALREADY_EXISTS_WARNING_MESSAGE.format(Locale.US, instrumentationName) }
            )
            tracer
        }
    }

    override fun get(instrumentationName: String, instrumentationVersion: String): Tracer {
        val tracer = tracers[instrumentationName]
        return if (tracer == null) {
            val newTracer = tracerBuilder(instrumentationName)
                .setInstrumentationVersion(instrumentationVersion)
                .build()
            tracers[instrumentationName] = newTracer
            newTracer
        } else {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.USER,
                { TRACER_ALREADY_EXISTS_WARNING_MESSAGE.format(Locale.US, instrumentationName) }
            )
            tracer
        }
    }

    /** @inheritDoc */
    override fun tracerBuilder(instrumentationScopeName: String): TracerBuilder {
        val resolvedInstrumentationScopeName = resolveInstrumentationScopeName(instrumentationScopeName)
        return OtelTracerBuilder(
            resolvedInstrumentationScopeName,
            datadogTracer,
            internalLogger
        )
    }

    /**
     * A builder for creating [TracerProvider] instances.
     */
    class Builder internal constructor(
        private val sdkCore: FeatureSdkCore
    ) {
        private val builderDelegate = DatadogTracing.newTracerBuilder(sdkCore)
            .withPartialFlushMinSpans(DEFAULT_PARTIAL_MIN_FLUSH)
            .withTracingHeadersTypes(
                setOf(
                    TracingHeaderType.DATADOG,
                    TracingHeaderType.TRACECONTEXT
                )
            )
            .also { setTraceId128BitGenerationEnabled(it) }

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

        /**
         * @param sdkCore SDK instance to bind to. If not provided, default instance will be used.
         */
        @JvmOverloads
        constructor(sdkCore: SdkCore = Datadog.getInstance()) : this(sdkCore as FeatureSdkCore)

        // region Public API

        /**
         * Builds a [TracerProvider] based on the current state of this Builder.
         */
        fun build(): TracerProvider {
            return executeIfJavaFunctionPackageExists(
                sdkCore.internalLogger,
                TracerProvider.noop()
            ) {
                val tracingFeature = sdkCore.getFeature(Feature.TRACING_FEATURE_NAME)?.unwrap<Feature>()
                val internalCoreWriterProvider = tracingFeature as? InternalCoreWriterProvider
                if (tracingFeature != null && internalCoreWriterProvider != null) {
                    // update meta for the configuration telemetry reporting, can be done directly from this thread
                    sdkCore.updateFeatureContext(Feature.TRACING_FEATURE_NAME, useContextThread = false) {
                        it[IS_OPENTELEMETRY_ENABLED_CONFIG_KEY] = true
                        it[OPENTELEMETRY_API_VERSION_CONFIG_KEY] =
                            BuildConfig.OPENTELEMETRY_API_VERSION_NAME
                    }
                }

                OtelTracerProvider(createDatadogTracer(), sdkCore.internalLogger)
            }
        }

        private fun createDatadogTracer(): DatadogTracer {
            val datadogTracer = builderDelegate
                .withServiceName(serviceName)
                .build()

            GlobalDatadogTracer.registerIfAbsent(datadogTracer).let { registeredAsGlobal ->
                sdkCore.internalLogger.log(
                    InternalLogger.Level.INFO,
                    listOf(
                        InternalLogger.Target.USER,
                        InternalLogger.Target.MAINTAINER
                    ),
                    { if (registeredAsGlobal) USED_AS_GLOBAL_INFO_MESSAGE else NOT_USED_AS_GLOBAL_INFO_MESSAGE }
                )
            }

            return datadogTracer
        }

        /**
         * Sets the tracing header styles that may be injected by this tracer.
         * @param headerTypes the list of header types injected (default = datadog style headers)
         */
        fun setTracingHeaderTypes(headerTypes: Set<TracingHeaderType>): Builder {
            builderDelegate.withTracingHeadersTypes(headerTypes)
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
            builderDelegate.withPartialFlushMinSpans(threshold)
            return this
        }

        /**
         * Adds a global tag which will be appended to all spans created with the built tracer.
         * @param key the tag key
         * @param value the tag value
         */
        fun addTag(key: String, value: String): Builder {
            builderDelegate.withTag(key, value)
            return this
        }

        /**
         * Sets the sample rate of spans.
         * @param sampleRate the sample rate as a percentage between 0 and 100 (default is 100%)
         */
        fun setSampleRate(
            @FloatRange(from = 0.0, to = 100.0) sampleRate: Double
        ): Builder {
            // In case the sample rate is not set we should not specify it. The agent code under the hood
            // will provide different sampler based on this property and also different sampling priorities used
            // in the metrics
            // -1 MANUAL_DROP User indicated to drop the trace via configuration (sampling rate).
            // 0 AUTO_DROP Sampler indicated to drop the trace using a sampling rate provided by the Agent through
            // a remote configuration. The Agent API is not used in Android so this `sampling_priority:0` will never
            // be used.
            // 1 AUTO_KEEP Sampler indicated to keep the trace using a sampling rate from the default configuration
            // which right now is 100.0
            // (Default sampling priority value. or in our case no specified sample rate will be considered as 100)
            // 2 MANUAL_KEEP User indicated to keep the trace, either manually or via configuration (sampling rate)
            builderDelegate.withSampleRate(sampleRate)
            return this
        }

        /**
         * Sets the trace rate limit. This is the maximum number of traces per second that will be
         * accepted. Please not that this property is used in conjunction with the sample rate. If no sample rate
         * is provided this property and its related logic will be ignored.
         * @param traceRateLimit the trace rate limit as a value between 1 and Int.MAX_VALUE (default is Int.MAX_VALUE)
         */
        fun setTraceRateLimit(
            @IntRange(from = 1, to = Int.MAX_VALUE.toLong()) traceRateLimit: Int
        ): Builder {
            builderDelegate.withTraceLimit(traceRateLimit)
            return this
        }

        /**
         * Enables the trace bundling with the current active View. If this feature is enabled all
         * the spans from this moment on will be bundled with the current view information and you
         * will be able to see all the traces sent during a specific view in the RUM Explorer.
         * @param enabled true by default
         */
        fun setBundleWithRumEnabled(enabled: Boolean): Builder {
            builderDelegate.setBundleWithRumEnabled(enabled)
            return this
        }

        // endregion
    }

    // region Internal

    private fun resolveInstrumentationScopeName(instrumentationScopeName: String): String {
        return if (instrumentationScopeName.trim { it <= ' ' }.isEmpty()) {
            DEFAULT_TRACER_NAME
        } else {
            instrumentationScopeName
        }
    }

    override fun toString(): String {
        return "OtelTracerProvider/${super.toString()}"
    }

// endregion

    internal companion object {
        init {
            // We need to add the DatadogContextStorageWrapper and this should be executed before
            // before io.opentelemetry.context.LazyStorage is loaded in the class loader to take effect.
            // For now we should assume that our users are using `OtelTracerProvider` first in their code.
            // Later on maybe we should consider a method
            // DatdogOpenTelemetry.initialize() to be called in the Application#onCreate class.
            executeIfJavaFunctionPackageExists(
                null,
                null
            ) {
                // suppressing the lint warning as we call this safely on Android 23 and below
                @Suppress("NewApi")
                ContextStorage.addWrapper(DatadogContextStorageWrapper())
            }
        }

        internal const val IS_OPENTELEMETRY_ENABLED_CONFIG_KEY = "is_opentelemetry_enabled"
        internal const val OPENTELEMETRY_API_VERSION_CONFIG_KEY = "opentelemetry_api_version"
        internal const val RUM_APPLICATION_ID_KEY = "application_id"
        internal const val RUM_SESSION_ID_KEY = "session_id"
        internal const val RUM_VIEW_ID_KEY = "view_id"
        internal const val RUM_ACTION_ID_KEY = "action_id"
        internal const val TRACER_ALREADY_EXISTS_WARNING_MESSAGE =
            "Tracer for %s already exists. Returning existing instance."
        internal const val DEFAULT_TRACER_NAME = "android"
        internal const val KEEP_ALL_SAMPLE_RATE_PERCENT = 100.0

        internal const val DEFAULT_SERVICE_NAME_IS_MISSING_ERROR_MESSAGE =
            "Default service name is missing during" +
                " OtelTracerProvider creation, did you initialize SDK?"
        internal const val RUM_CONTEXT_MISSING_ERROR_MESSAGE =
            "You are trying to bundle the traces with a RUM context, " +
                "but the RUM context is missing. " +
                "You should check if the RUM feature is enabled for your SDK instance."
        internal const val USED_AS_GLOBAL_INFO_MESSAGE =
            "OpenTracer's DatadogTracer instance will be used as global instance"
        internal const val NOT_USED_AS_GLOBAL_INFO_MESSAGE =
            "OpenTracer's DatadogTracer instance will not be used as global instance"

        // the minimum closed spans required for triggering a flush and deliver
        // everything to the writer
        internal const val DEFAULT_PARTIAL_MIN_FLUSH = 5
    }
}
