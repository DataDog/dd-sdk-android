/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.log.internal.utils.DEBUG_WITH_TELEMETRY_LEVEL
import com.datadog.android.log.internal.utils.ERROR_WITH_TELEMETRY_LEVEL
import com.datadog.android.log.internal.utils.WARN_WITH_TELEMETRY_LEVEL
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
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
internal class InternalLogHandlerTest {

    @Mock
    lateinit var mockLogcatLogHandler: LogHandler

    @Mock
    lateinit var mockTelemetryLogHandler: LogHandler

    lateinit var testedHandler: InternalLogHandler

    @BeforeEach
    fun setUp() {
        testedHandler = InternalLogHandler(mockLogcatLogHandler, mockTelemetryLogHandler)
    }

    @Test
    fun `𝕄 report logcat only 𝕎 handleLog() { no telemetry flag }`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val level = forge.anElementFrom(
            AndroidLog.ASSERT,
            AndroidLog.VERBOSE,
            AndroidLog.DEBUG,
            AndroidLog.INFO,
            AndroidLog.WARN,
            AndroidLog.ERROR
        )

        // When
        testedHandler.handleLog(
            level,
            message
        )

        // Then
        verify(mockLogcatLogHandler).handleLog(level, message)
        verifyZeroInteractions(mockTelemetryLogHandler)
    }

    @Test
    fun `𝕄 report logcat and telemetry 𝕎 handleLog() { with telemetry flag }`(
        @StringForgery message: String,
        forge: Forge
    ) {
        // Given
        val level = forge.anElementFrom(
            ERROR_WITH_TELEMETRY_LEVEL,
            WARN_WITH_TELEMETRY_LEVEL,
            DEBUG_WITH_TELEMETRY_LEVEL
        )

        val expectedLevels = mapOf(
            ERROR_WITH_TELEMETRY_LEVEL to AndroidLog.ERROR,
            WARN_WITH_TELEMETRY_LEVEL to AndroidLog.WARN,
            DEBUG_WITH_TELEMETRY_LEVEL to AndroidLog.DEBUG
        )

        // When
        testedHandler.handleLog(
            level,
            message
        )

        // Then
        verify(mockLogcatLogHandler).handleLog(expectedLevels.getValue(level), message)
        verify(mockTelemetryLogHandler).handleLog(expectedLevels.getValue(level), message)
    }

    @Test
    fun `𝕄 report logcat only 𝕎 handleLog( { error strings } ) { no telemetry flag }`(
        @StringForgery message: String,
        @StringForgery errorKind: String,
        @StringForgery errorStack: String,
        forge: Forge
    ) {
        // Given
        val level = forge.anElementFrom(
            AndroidLog.ASSERT,
            AndroidLog.VERBOSE,
            AndroidLog.DEBUG,
            AndroidLog.INFO,
            AndroidLog.WARN,
            AndroidLog.ERROR
        )

        // When
        testedHandler.handleLog(
            level,
            message,
            errorKind,
            null,
            errorStack
        )

        // Then
        verify(mockLogcatLogHandler).handleLog(level, message, errorKind, null, errorStack)
        verifyZeroInteractions(mockTelemetryLogHandler)
    }

    @Test
    fun `𝕄 report logcat and telemetry 𝕎 handleLog( { error strings } ) { with telemetry flag }`(
        @StringForgery message: String,
        @StringForgery errorKind: String,
        @StringForgery errorStack: String,
        forge: Forge
    ) {
        // Given
        val level = forge.anElementFrom(
            ERROR_WITH_TELEMETRY_LEVEL,
            WARN_WITH_TELEMETRY_LEVEL,
            DEBUG_WITH_TELEMETRY_LEVEL
        )

        val expectedLevels = mapOf(
            ERROR_WITH_TELEMETRY_LEVEL to AndroidLog.ERROR,
            WARN_WITH_TELEMETRY_LEVEL to AndroidLog.WARN,
            DEBUG_WITH_TELEMETRY_LEVEL to AndroidLog.DEBUG
        )

        // When
        testedHandler.handleLog(
            level,
            message,
            errorKind,
            null,
            errorStack
        )

        // Then
        verify(mockLogcatLogHandler).handleLog(
            expectedLevels.getValue(level),
            message,
            errorKind,
            null,
            errorStack
        )
        verify(mockTelemetryLogHandler).handleLog(
            expectedLevels.getValue(level),
            message,
            errorKind,
            null,
            errorStack
        )
    }
}
