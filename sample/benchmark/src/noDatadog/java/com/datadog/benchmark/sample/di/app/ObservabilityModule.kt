/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.di.app

import com.datadog.benchmark.sample.NoOpObservabilityFeaturesInitializer
import com.datadog.benchmark.sample.ObservabilityFeaturesInitializer
import com.datadog.benchmark.sample.observability.ObservabilityActionType
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

@Module
internal interface ObservabilityModule {

    @Binds
    fun bindObservabilityFeaturesInitializer(
        impl: NoOpObservabilityFeaturesInitializer
    ): ObservabilityFeaturesInitializer

    companion object {
        @Provides
        fun provideLogger(): ObservabilityLogger {
            return object : ObservabilityLogger {
                override fun log(
                    priority: Int,
                    message: String,
                    throwable: Throwable?,
                    attributes: Map<String, Any?>
                ) {
                    // no-op
                }
            }
        }

        @Provides
        fun provideRumMonitor(): ObservabilityRumMonitor {
            return object : ObservabilityRumMonitor {
                override fun startView(key: Any, name: String, attributes: Map<String, Any?>) {
                    // no-op
                }

                override fun stopView(key: Any, attributes: Map<String, Any?>) {
                    // no-op
                }

                override fun addAction(
                    type: ObservabilityActionType,
                    name: String,
                    attributes: Map<String, Any?>
                ) {
                    // no-op
                }

                override fun startResource(
                    key: String,
                    method: ObservabilityResourceMethod,
                    url: String,
                    attributes: Map<String, Any?>
                ) {
                    // no-op
                }

                override fun stopResource(
                    key: String,
                    statusCode: Int?,
                    size: Long?,
                    kind: ObservabilityResourceKind,
                    attributes: Map<String, Any?>
                ) {
                    // no-op
                }

                override fun addError(
                    message: String,
                    source: ObservabilityErrorSource,
                    throwable: Throwable?,
                    attributes: Map<String, Any?>
                ) {
                    // no-op
                }
            }
        }

        @Provides
        fun provideTracer(): ObservabilityTracer {
            return object : ObservabilityTracer {
                override fun spanBuilder(spanName: String): ObservabilitySpanBuilder {
                    return NoOpSpanBuilder
                }
            }
        }
    }
}

private object NoOpSpan : ObservabilitySpan {
    override fun setAttribute(key: String, value: String): ObservabilitySpan = this
    override fun setError(): ObservabilitySpan = this
    override fun end() {
        // no-op
    }
}

private object NoOpSpanBuilder : ObservabilitySpanBuilder {
    override fun setParent(parentSpan: ObservabilitySpan): ObservabilitySpanBuilder = this
    override fun startSpan(): ObservabilitySpan = NoOpSpan
}
