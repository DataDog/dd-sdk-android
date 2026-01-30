/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import com.datadog.android.log.Logger
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.benchmark.DatadogBaseMeter
import com.datadog.benchmark.sample.DatadogFeaturesInitializer
import com.datadog.benchmark.sample.ObservabilityFeaturesInitializer
import com.datadog.benchmark.sample.observability.ObservabilityActionType
import com.datadog.benchmark.sample.observability.ObservabilityMeter
import com.datadog.benchmark.sample.observability.ObservabilityErrorSource
import com.datadog.benchmark.sample.observability.ObservabilityLogger
import com.datadog.benchmark.sample.observability.ObservabilityResourceKind
import com.datadog.benchmark.sample.observability.ObservabilityResourceMethod
import com.datadog.benchmark.sample.observability.ObservabilityRumMonitor
import com.datadog.benchmark.sample.observability.ObservabilitySpan
import com.datadog.benchmark.sample.observability.ObservabilitySpanBuilder
import com.datadog.benchmark.sample.observability.ObservabilityTracer
import dagger.Binds
import dagger.Module
import dagger.Provides
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context

@Module
internal interface ObservabilityModule {

    @Binds
    fun bindObservabilityFeaturesInitializer(
        impl: DatadogFeaturesInitializer
    ): ObservabilityFeaturesInitializer

    companion object {
        @Provides
        fun provideLogger(logger: Logger): ObservabilityLogger {
            return object : ObservabilityLogger {
                override fun log(
                    priority: Int,
                    message: String,
                    throwable: Throwable?,
                    attributes: Map<String, Any?>
                ) {
                    logger.log(
                        priority = priority,
                        message = message,
                        throwable = throwable,
                        attributes = attributes
                    )
                }
            }
        }

        @Provides
        fun provideRumMonitor(rumMonitor: RumMonitor): ObservabilityRumMonitor {
            return object : ObservabilityRumMonitor {
                override fun startView(key: Any, name: String, attributes: Map<String, Any?>) {
                    rumMonitor.startView(key = key, name = name, attributes = attributes)
                }

                override fun stopView(key: Any, attributes: Map<String, Any?>) {
                    rumMonitor.stopView(key = key, attributes = attributes)
                }

                override fun addAction(
                    type: ObservabilityActionType,
                    name: String,
                    attributes: Map<String, Any?>
                ) {
                    rumMonitor.addAction(
                        type = type.toRumActionType(),
                        name = name,
                        attributes = attributes
                    )
                }

                override fun startResource(
                    key: String,
                    method: ObservabilityResourceMethod,
                    url: String,
                    attributes: Map<String, Any?>
                ) {
                    rumMonitor.startResource(
                        key = key,
                        method = method.toRumResourceMethod(),
                        url = url,
                        attributes = attributes
                    )
                }

                override fun stopResource(
                    key: String,
                    statusCode: Int?,
                    size: Long?,
                    kind: ObservabilityResourceKind,
                    attributes: Map<String, Any?>
                ) {
                    rumMonitor.stopResource(
                        key = key,
                        statusCode = statusCode,
                        size = size,
                        kind = kind.toRumResourceKind(),
                        attributes = attributes
                    )
                }

                override fun addError(
                    message: String,
                    source: ObservabilityErrorSource,
                    throwable: Throwable?,
                    attributes: Map<String, Any?>
                ) {
                    rumMonitor.addError(
                        message = message,
                        source = source.toRumErrorSource(),
                        throwable = throwable,
                        attributes = attributes
                    )
                }
            }
        }

        private fun ObservabilityActionType.toRumActionType(): RumActionType {
            return when (this) {
                ObservabilityActionType.TAP -> RumActionType.TAP
                ObservabilityActionType.SCROLL -> RumActionType.SCROLL
                ObservabilityActionType.SWIPE -> RumActionType.SWIPE
                ObservabilityActionType.CLICK -> RumActionType.CLICK
                ObservabilityActionType.BACK -> RumActionType.BACK
                ObservabilityActionType.CUSTOM -> RumActionType.CUSTOM
            }
        }

        private fun ObservabilityResourceMethod.toRumResourceMethod(): RumResourceMethod {
            return when (this) {
                ObservabilityResourceMethod.POST -> RumResourceMethod.POST
                ObservabilityResourceMethod.GET -> RumResourceMethod.GET
                ObservabilityResourceMethod.HEAD -> RumResourceMethod.HEAD
                ObservabilityResourceMethod.PUT -> RumResourceMethod.PUT
                ObservabilityResourceMethod.DELETE -> RumResourceMethod.DELETE
                ObservabilityResourceMethod.PATCH -> RumResourceMethod.PATCH
                ObservabilityResourceMethod.CONNECT -> RumResourceMethod.CONNECT
                ObservabilityResourceMethod.TRACE -> RumResourceMethod.TRACE
                ObservabilityResourceMethod.OPTIONS -> RumResourceMethod.OPTIONS
            }
        }

        private fun ObservabilityResourceKind.toRumResourceKind(): RumResourceKind {
            return when (this) {
                ObservabilityResourceKind.DOCUMENT -> RumResourceKind.DOCUMENT
                ObservabilityResourceKind.IMAGE -> RumResourceKind.IMAGE
                ObservabilityResourceKind.XHR -> RumResourceKind.XHR
                ObservabilityResourceKind.BEACON -> RumResourceKind.BEACON
                ObservabilityResourceKind.CSS -> RumResourceKind.CSS
                ObservabilityResourceKind.JS -> RumResourceKind.JS
                ObservabilityResourceKind.FONT -> RumResourceKind.FONT
                ObservabilityResourceKind.MEDIA -> RumResourceKind.MEDIA
                ObservabilityResourceKind.OTHER -> RumResourceKind.OTHER
                ObservabilityResourceKind.NATIVE -> RumResourceKind.NATIVE
            }
        }

        private fun ObservabilityErrorSource.toRumErrorSource(): RumErrorSource {
            return when (this) {
                ObservabilityErrorSource.NETWORK -> RumErrorSource.NETWORK
                ObservabilityErrorSource.SOURCE -> RumErrorSource.SOURCE
                ObservabilityErrorSource.CONSOLE -> RumErrorSource.CONSOLE
                ObservabilityErrorSource.LOGGER -> RumErrorSource.LOGGER
                ObservabilityErrorSource.AGENT -> RumErrorSource.AGENT
                ObservabilityErrorSource.WEBVIEW -> RumErrorSource.WEBVIEW
            }
        }

        @Provides
        fun provideTracer(tracer: Tracer): ObservabilityTracer {
            return object : ObservabilityTracer {
                override fun spanBuilder(spanName: String): ObservabilitySpanBuilder {
                    return OtelSpanBuilderWrapper(tracer.spanBuilder(spanName))
                }
            }
        }

        @Provides
        fun provideMeter(meter: DatadogBaseMeter): ObservabilityMeter {
            return object : ObservabilityMeter {
                override fun startMeasuring() {
                    meter.startMeasuring()
                }

                override fun stopMeasuring() {
                    meter.stopMeasuring()
                }
            }
        }
    }
}

private class OtelSpanWrapper(private val span: Span) : ObservabilitySpan {
    override fun setAttribute(key: String, value: String): ObservabilitySpan {
        span.setAttribute(key, value)
        return this
    }

    override fun setError(): ObservabilitySpan {
        span.setStatus(StatusCode.ERROR)
        return this
    }

    override fun end() {
        span.end()
    }

    fun getUnderlyingSpan(): Span = span
}

private class OtelSpanBuilderWrapper(
    private val spanBuilder: io.opentelemetry.api.trace.SpanBuilder
) : ObservabilitySpanBuilder {
    override fun setParent(parentSpan: ObservabilitySpan): ObservabilitySpanBuilder {
        if (parentSpan is OtelSpanWrapper) {
            spanBuilder.setParent(Context.current().with(parentSpan.getUnderlyingSpan()))
        }
        return this
    }

    override fun startSpan(): ObservabilitySpan {
        return OtelSpanWrapper(spanBuilder.startSpan())
    }
}
