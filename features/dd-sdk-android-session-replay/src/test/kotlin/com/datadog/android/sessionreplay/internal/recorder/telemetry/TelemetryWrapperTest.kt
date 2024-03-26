/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.telemetry

import com.datadog.android.api.InternalLogger
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
internal class TelemetryWrapperTest {
    private lateinit var testedTelemetryWrapper: TelemetryWrapper

    @StringForgery
    private lateinit var fakeCallerClass: String

    @StringForgery
    private lateinit var fakeOperationName: String

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return null W event is sampled out`() {
        // Given
        testedTelemetryWrapper = TelemetryWrapper(
            logger = mockInternalLogger,
            samplingRate = 0f
        )

        // When
        val result = testedTelemetryWrapper.startMethodCalled(
            operationName = fakeOperationName,
            callerClass = fakeCallerClass
        )

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M return telemetry event W sampled in`() {
        // Given
        testedTelemetryWrapper = TelemetryWrapper(
            logger = mockInternalLogger,
            samplingRate = 100f
        )

        // When
        val result = testedTelemetryWrapper.startMethodCalled(
            operationName = fakeOperationName,
            callerClass = fakeCallerClass
        )

        // Then
        assertThat(result).isInstanceOf(MethodCalledTelemetry::class.java)
    }
}
