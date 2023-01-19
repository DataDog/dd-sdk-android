/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.tracing.TracingHeaderType
import com.datadog.android.tracing.TracingInterceptor
import com.datadog.android.tracing.TracingInterceptorTest
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doThrow
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import io.opentracing.Tracer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogInterceptorWithoutRumTest : TracingInterceptorTest() {

    @Mock
    lateinit var mockRumAttributesProvider: RumResourceAttributesProvider

    override fun instantiateTestedInterceptor(
        tracedHosts: Map<String, Set<TracingHeaderType>>,
        factory: (Set<TracingHeaderType>) -> Tracer
    ): TracingInterceptor {
        return DatadogInterceptor(
            tracedHosts = tracedHosts,
            tracedRequestListener = mockRequestListener,
            firstPartyHostResolver = mockResolver,
            rumResourceAttributesProvider = mockRumAttributesProvider,
            traceSampler = mockTraceSampler,
            localTracerFactory = factory
        )
    }

    override fun getExpectedOrigin(): String {
        return DatadogInterceptor.ORIGIN_RUM
    }

    @Test
    fun `𝕄 warn that RUM is not enabled 𝕎 intercept()`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)
        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(logger.mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            DatadogInterceptor.WARN_RUM_DISABLED
        )
    }

    @Test
    fun `𝕄 do nothing 𝕎 intercept() for successful request`(
        @IntForgery(min = 200, max = 300) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
        verifyNoMoreInteractions(rumMonitor.mockInstance)
        verifyZeroInteractions(mockRumAttributesProvider)
    }

    @Test
    fun `𝕄 do nothing RUM Resource 𝕎 intercept() for failing request`(
        @IntForgery(min = 400, max = 500) statusCode: Int
    ) {
        // Given
        stubChain(mockChain, statusCode)

        // When
        testedInterceptor.intercept(mockChain)

        // Then
        verify(rumMonitor.mockInstance).notifyInterceptorInstantiated()
        verifyNoMoreInteractions(rumMonitor.mockInstance)
        verifyZeroInteractions(mockRumAttributesProvider)
    }

    @Test
    fun `𝕄 do nothing 𝕎 intercept() for throwing request`(
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
        verifyZeroInteractions(mockRumAttributesProvider)
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, rumMonitor)
        }
    }
}
