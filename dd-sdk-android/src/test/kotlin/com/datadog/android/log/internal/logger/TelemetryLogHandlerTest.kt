/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.telemetry.internal.Telemetry
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import android.util.Log as AndroidLog

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TelemetryLogHandlerTest {

    @Mock
    lateinit var mockTelemetry: Telemetry

    lateinit var testedHandler: TelemetryLogHandler

    @BeforeEach
    fun setUp() {
        testedHandler = TelemetryLogHandler(mockTelemetry)
    }

    @Test
    fun `𝕄 report ERROR and WARN as error 𝕎 handleLog()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val level = forge.anElementFrom(AndroidLog.ERROR, AndroidLog.WARN)
        val throwable = forge.aNullable { forge.aThrowable() }

        // When
        testedHandler.handleLog(level, message, throwable)

        // Then
        verify(mockTelemetry).error(message, throwable)
        verifyNoMoreInteractions(mockTelemetry)
    }

    @Test
    fun `𝕄 report any non-ERROR or WARN as debug 𝕎 handleLog()`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        var level = forge.anInt()
        while (level in setOf(AndroidLog.ERROR, AndroidLog.WARN)) {
            level = forge.anInt()
        }
        val throwable = forge.aNullable { forge.aThrowable() }

        // When
        testedHandler.handleLog(level, message, throwable)

        // Then
        verify(mockTelemetry).debug(message)
        verifyNoMoreInteractions(mockTelemetry)
    }

    @Test
    fun `𝕄 report ERROR and WARN as error 𝕎 handleLog( { error strings } )`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val level = forge.anElementFrom(AndroidLog.ERROR, AndroidLog.WARN)
        val errorKind = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val errorStack = forge.anAlphabeticalString()

        // When
        testedHandler.handleLog(level, message, errorKind, errorMessage, errorStack)

        // Then
        verify(mockTelemetry).error(message, errorStack, errorKind)
        verifyNoMoreInteractions(mockTelemetry)
    }

    @Test
    fun `𝕄 report any non-ERROR or WARN as debug 𝕎 handleLog( { error strings } )`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        var level = forge.anInt()
        while (level in setOf(AndroidLog.ERROR, AndroidLog.WARN)) {
            level = forge.anInt()
        }
        val errorKind = forge.anAlphabeticalString()
        val errorMessage = forge.anAlphabeticalString()
        val errorStack = forge.anAlphabeticalString()

        // When
        testedHandler.handleLog(level, message, errorKind, errorMessage, errorStack)

        // Then
        verify(mockTelemetry).debug(message)
        verifyNoMoreInteractions(mockTelemetry)
    }
}
