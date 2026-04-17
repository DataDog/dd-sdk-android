/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.FeatureStorageConfiguration
import com.datadog.android.log.LogAttributes
import com.datadog.android.trace.event.SpanEventMapper
import com.datadog.android.trace.internal.data.CoreTraceWriter
import com.datadog.android.trace.internal.domain.event.CoreTracerSpanToSpanEventMapper
import com.datadog.android.trace.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicReference

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracingFeatureTest {

    private lateinit var testedFeature: TracingFeature

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockSpanEventMapper: SpanEventMapper

    @StringForgery(regex = "https://[a-z]+\\.com")
    lateinit var fakeEndpointUrl: String

    @BoolForgery
    var fakeNetworkInfoEnabled: Boolean = false

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger

        testedFeature = TracingFeature(
            mockSdkCore,
            fakeEndpointUrl,
            mockSpanEventMapper,
            fakeNetworkInfoEnabled
        )
    }

    @Test
    fun `M initialize core writer W initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        val traceWriter = testedFeature.coreTracerDataWriter as CoreTraceWriter
        val ddSpanToSpanEventMapper = traceWriter.ddSpanToSpanEventMapper
        assertThat(ddSpanToSpanEventMapper).isInstanceOf(CoreTracerSpanToSpanEventMapper::class.java)
        assertThat((ddSpanToSpanEventMapper as CoreTracerSpanToSpanEventMapper).networkInfoEnabled)
            .isEqualTo(fakeNetworkInfoEnabled)
    }

    @Test
    fun `M use the eventMapper for core writer W initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        val dataWriter = testedFeature.coreTracerDataWriter as? CoreTraceWriter
        val spanEventMapperWrapper = dataWriter?.eventMapper as? SpanEventMapperWrapper
        val spanEventMapper = spanEventMapperWrapper?.wrappedEventMapper
        assertThat(spanEventMapper).isSameAs(mockSpanEventMapper)
    }

    @Test
    fun `M provide tracing feature name W name()`() {
        // When+Then
        assertThat(testedFeature.name)
            .isEqualTo(Feature.TRACING_FEATURE_NAME)
    }

    @Test
    fun `M provide tracing request factory W requestFactory()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When+Then
        assertThat(testedFeature.requestFactory)
            .isInstanceOf(TracesRequestFactory::class.java)
    }

    @Test
    fun `M provide default storage configuration W storageConfiguration()`() {
        // When+Then
        assertThat(testedFeature.storageConfiguration)
            .isEqualTo(FeatureStorageConfiguration.DEFAULT)
    }

    @Test
    fun `M register context update receiver W initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        verify(mockSdkCore).setContextUpdateReceiver(testedFeature)
    }

    @Test
    fun `M register event receiver W initialize()`() {
        // When
        testedFeature.onInitialize(mock())

        // Then
        verify(mockSdkCore).setEventReceiver(Feature.TRACING_FEATURE_NAME, testedFeature)
    }

    @Test
    fun `M update tracked refs W onContextUpdate() {RUM context with session sample rate}`(
        @FloatForgery(min = 1f, max = 99f) fakeSessionRate: Float
    ) {
        // Given
        val fakeRef = AtomicReference(TracingFeature.NO_SESSION_REBASING_RATE)
        testedFeature.onReceive(SessionSampleRateRegistrationEvent(fakeRef))

        // When
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(LogAttributes.RUM_SESSION_SAMPLE_RATE to fakeSessionRate)
        )

        // Then
        assertThat(fakeRef.get()).isEqualTo(fakeSessionRate)
    }

    @Test
    fun `M not update tracked refs W onContextUpdate() {non-RUM feature}`(
        @FloatForgery(min = 1f, max = 99f) fakeSessionRate: Float,
        @StringForgery fakeFeatureName: String
    ) {
        // Given
        val fakeRef = AtomicReference(TracingFeature.NO_SESSION_REBASING_RATE)
        testedFeature.onReceive(SessionSampleRateRegistrationEvent(fakeRef))

        // When
        testedFeature.onContextUpdate(
            fakeFeatureName,
            mapOf(LogAttributes.RUM_SESSION_SAMPLE_RATE to fakeSessionRate)
        )

        // Then
        assertThat(fakeRef.get()).isEqualTo(TracingFeature.NO_SESSION_REBASING_RATE)
    }

    @Test
    fun `M track ref and initialize with current rate W onReceive() {SessionSampleRateRegistrationEvent}`(
        @FloatForgery(min = 1f, max = 99f) fakeSessionRate: Float
    ) {
        // Given
        testedFeature.onContextUpdate(
            Feature.RUM_FEATURE_NAME,
            mapOf(LogAttributes.RUM_SESSION_SAMPLE_RATE to fakeSessionRate)
        )
        val fakeRef = AtomicReference(0f)

        // When
        testedFeature.onReceive(SessionSampleRateRegistrationEvent(fakeRef))

        // Then
        assertThat(fakeRef.get()).isEqualTo(fakeSessionRate)
    }

    @Test
    fun `M initialize ref with default rate W onReceive() {no prior context update}`() {
        // Given
        val fakeRef = AtomicReference(0f)

        // When
        testedFeature.onReceive(SessionSampleRateRegistrationEvent(fakeRef))

        // Then
        assertThat(fakeRef.get()).isEqualTo(TracingFeature.NO_SESSION_REBASING_RATE)
    }

    @Test
    fun `M unregister receivers and clear refs W onStop()`() {
        // Given
        testedFeature.onInitialize(mock())

        // When
        testedFeature.onStop()

        // Then
        verify(mockSdkCore).removeContextUpdateReceiver(testedFeature)
        verify(mockSdkCore).removeEventReceiver(Feature.TRACING_FEATURE_NAME)
        assertThat(testedFeature.internalSessionSampleRate.get())
            .isEqualTo(TracingFeature.NO_SESSION_REBASING_RATE)
    }
}
