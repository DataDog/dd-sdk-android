/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android

import com.datadog.android.telemetry.internal.Telemetry
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class InternalProxyTest {

    @Test
    fun `M proxy telemetry to RumMonitor W debug()`(
        @StringForgery message: String
    ) {
        // Given
        val mockTelemetry = mock(Telemetry::class.java)
        val proxy = _InternalProxy(mockTelemetry)

        // When
        proxy._telemetry.debug(message)

        // Then
        verify(mockTelemetry).debug(message)
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error()`(
        @StringForgery message: String,
        @StringForgery stack: String,
        @StringForgery kind: String
    ) {
        // Given
        val mockTelemetry = mock(Telemetry::class.java)
        val proxy = _InternalProxy(mockTelemetry)

        // When
        proxy._telemetry.error(message, stack, kind)

        // Then
        verify(mockTelemetry).error(message, stack, kind)
    }

    @Test
    fun `M proxy telemetry to RumMonitor W error({message, throwable})`(
        @StringForgery message: String,
        @Forgery throwable: Throwable
    ) {
        // Given
        val mockTelemetry = mock(Telemetry::class.java)
        val proxy = _InternalProxy(mockTelemetry)

        // When
        proxy._telemetry.error(message, throwable)

        // Then
        verify(mockTelemetry).error(message, throwable)
    }
}
