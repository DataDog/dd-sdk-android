/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.SdkCore
import com.datadog.android.okhttp.internal.utils.forge.OkHttpConfigurator
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptorTest
import com.datadog.android.okhttp.utils.verifyLog
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.trace.TracingHeaderType
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.trace.bootstrap.instrumentation.api.AgentTracer
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(OkHttpConfigurator::class)
internal class DatadogInterceptorWithoutRumTest : TracingInterceptorTest() {

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    override fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>>,
        globalTracerProvider: () -> AgentTracer.TracerAPI?,
        localTracerFactory: (SdkCore, Set<TracingHeaderType>) -> AgentTracer.TracerAPI
    ): TracingInterceptor {
        return DatadogInterceptor(
            sdkInstanceName = null,
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            rumResourceAttributesProvider = mockRumAttributesProvider,
            traceSampler = mockTraceSampler,
            traceContextInjection = TraceContextInjection.ALL,
            redacted404ResourceName = fakeRedacted404Resources,
            localTracerFactory = localTracerFactory,
            globalTracerProvider = globalTracerProvider
        )
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @Test
    fun `M warn that RUM is not enabled W intercept()`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        // When
        testedInterceptor.intercept(mockChain)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.INFO,
            InternalLogger.Target.USER,
            DatadogInterceptor.WARN_RUM_DISABLED.format(Locale.US, "Default SDK instance")
        )
    }

    @Test
    fun `M do nothing W intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
        verifyNoMoreInteractions(rumMonitor.mockInstance)
        verifyNoInteractions(mockRumAttributesProvider)
    }

    @Test
    fun `M do nothing RUM Resource W intercept() for failing request`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
        verifyNoMoreInteractions(rumMonitor.mockInstance)
        verifyNoInteractions(mockRumAttributesProvider)
    }

    @Test
    fun `M do nothing W intercept() for throwing request`(
        @Forgery throwable: Throwable
    ) {
        // Given
        whenever(mockChain.request()) doReturn fakeRequest
        whenever(mockChain.proceed(any())) doThrow throwable

        // When
        assertThrows<Throwable>(throwable.message.orEmpty()) {
            testedInterceptor.intercept(mockChain)
        }

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
        verifyNoMoreInteractions(rumMonitor.mockInstance)
        verifyNoInteractions(mockRumAttributesProvider)
    }
}
