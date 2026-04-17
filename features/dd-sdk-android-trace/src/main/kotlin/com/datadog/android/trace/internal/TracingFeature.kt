/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import android.content.Context
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureContextUpdateReceiver
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.feature.StorageBackedFeature
import com.datadog.android.api.net.RequestFactory
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.InternalCoreWriterProvider
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.data.CoreTraceWriter
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.domain.event.SpanEventSerializer
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.trace.common.writer.NoOpWriter
import com.datadog.trace.common.writer.Writer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracing feature class, which needs to be registered with Datadog SDK instance.
 */
internal class TracingFeature(
    private val sdkCore: FeatureSdkCore,
    customEndpointUrl: String?,
    internal val spanEventMapper: SpanEventMapper,
    internal val networkInfoEnabled: Boolean
) : InternalCoreWriterProvider, StorageBackedFeature, FeatureContextUpdateReceiver, FeatureEventReceiver {

    internal var coreTracerDataWriter: Writer = NoOpWriter()
    internal val initialized = AtomicBoolean(false)

    internal val internalSessionSampleRate = AtomicReference(NO_SESSION_REBASING_RATE)
    private val sessionSampleRateRefs = mutableSetOf<AtomicReference<Float>>()
    internal val registrationLock = Any()

    // region Feature

    override val name: String = Feature.TRACING_FEATURE_NAME

    override fun onInitialize(appContext: Context) {
        coreTracerDataWriter = createDataWriter(sdkCore)
        sdkCore.setContextUpdateReceiver(this)
        sdkCore.setEventReceiver(name, this)
        initialized.set(true)
    }

    override val requestFactory: RequestFactory by lazy {
        TracesRequestFactory(
            customEndpointUrl,
            sdkCore.internalLogger
        )
    }

    override val storageConfiguration: FeatureStorageConfiguration =
        FeatureStorageConfiguration.DEFAULT

    override fun onStop() {
        sdkCore.removeContextUpdateReceiver(this)
        sdkCore.removeEventReceiver(name)
        resetSessionSampleRateTracking()
        initialized.set(false)
    }

    // endregion

    // region FeatureContextUpdateReceiver

    override fun onContextUpdate(featureName: String, context: Map<String, Any?>) {
        if (featureName == Feature.RUM_FEATURE_NAME) {
            val rate = resolveSessionSampleRate(context[LogAttributes.RUM_SESSION_SAMPLE_RATE])
            synchronized(registrationLock) {
                internalSessionSampleRate.set(rate)
                sessionSampleRateRefs.forEach { it.set(rate) }
            }
        }
    }

    // endregion

    // region FeatureEventReceiver

    override fun onReceive(event: Any) {
        if (event is SessionSampleRateRegistrationEvent) {
            synchronized(registrationLock) {
                sessionSampleRateRefs.add(event.ref)
                event.ref.set(internalSessionSampleRate.get())
            }
        }
    }

    // endregion

    // region InternalCoreWriterProvider

    override fun getCoreTracerWriter() = DatadogSpanWriterWrapper(coreTracerDataWriter)

    // endregion

    private fun resetSessionSampleRateTracking() {
        synchronized(registrationLock) {
            sessionSampleRateRefs.clear()
            internalSessionSampleRate.set(NO_SESSION_REBASING_RATE)
        }
    }

    private fun createDataWriter(sdkCore: FeatureSdkCore): Writer {
        val internalLogger = sdkCore.internalLogger
        return CoreTraceWriter(
            sdkCore,
            ddSpanToSpanEventMapper = CoreTracerSpanToSpanEventMapper(networkInfoEnabled),
            eventMapper = SpanEventMapperWrapper(spanEventMapper, internalLogger),
            serializer = SpanEventSerializer(internalLogger),
            internalLogger = internalLogger
        )
    }

    internal companion object {
        // Sentinel value meaning "assume all RUM sessions are sampled" so that no session-based
        // rebasing is applied until the RUM feature publishes its actual session sample rate.
        // With this value the effective rate is: traceSampleRate * 100 / 100 = traceSampleRate.
        internal const val NO_SESSION_REBASING_RATE: Float = 100f

        private fun resolveSessionSampleRate(rawSessionSampleRate: Any?): Float {
            return (rawSessionSampleRate as? Number)?.toFloat() ?: NO_SESSION_REBASING_RATE
        }
    }
}
