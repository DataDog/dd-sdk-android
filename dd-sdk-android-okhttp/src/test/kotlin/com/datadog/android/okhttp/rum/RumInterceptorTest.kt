/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.rum

import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.okhttp.trace.NoOpTracedRequestListener
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptorNotSendingSpanTest
import com.datadog.android.okhttp.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.BaseConfigurator
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(BaseConfigurator::class)
internal class RumInterceptorTest : TracingInterceptorNotSendingSpanTest() {

    @Test
    fun `M instantiate with default values W init()`() {
        // Given
        whenever(mockSdkCore.firstPartyHostResolver) doReturn mock()

        // When
        val interceptor = RumInterceptor(mockSdkCore)

        // Then
        assertThat(interceptor.tracedHosts).isEmpty()
        assertThat(interceptor.rumResourceAttributesProvider)
            .isInstanceOf(NoOpRumResourceAttributesProvider::class.java)
        assertThat(interceptor.tracedRequestListener)
            .isInstanceOf(NoOpTracedRequestListener::class.java)
        assertThat(interceptor.traceSampler)
            .isInstanceOf(RateBasedSampler::class.java)
        val traceSampler = interceptor.traceSampler as RateBasedSampler
        assertThat(traceSampler.getSamplingRate())
            .isEqualTo(TracingInterceptor.DEFAULT_TRACE_SAMPLING_RATE / 100)
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
