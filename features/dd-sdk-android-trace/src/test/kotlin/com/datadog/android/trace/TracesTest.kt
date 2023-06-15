/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.trace

import com.datadog.android.trace.internal.TracingFeature
import com.datadog.android.trace.internal.net.TracesRequestFactory
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.FeatureSdkCore
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracesTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `𝕄 register traces feature 𝕎 enable()`(
        @StringForgery fakePackageName: String,
        @Forgery fakeTracesConfiguration: TracesConfiguration
    ) {
        // When
        Traces.enable(fakeTracesConfiguration, mockSdkCore)

        // Then
        argumentCaptor<TracingFeature> {
            verify(mockSdkCore).registerFeature(capture())

            lastValue.onInitialize(
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
            assertThat(lastValue.spanEventMapper).isEqualTo(fakeTracesConfiguration.eventMapper)
            assertThat((lastValue.requestFactory as TracesRequestFactory).customEndpointUrl)
                .isEqualTo(fakeTracesConfiguration.customEndpointUrl)
        }
    }
}
