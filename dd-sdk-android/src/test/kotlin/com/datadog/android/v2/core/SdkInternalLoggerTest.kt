/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.util.Log
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkInternalLoggerTest {

    private val testedInternalLogger = SdkInternalLogger

    @Test
    fun `𝕄 send dev log 𝕎 log { USER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        val fakeAttributes = forge.exhaustiveAttributes()

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.USER,
            fakeMessage,
            fakeThrowable,
            fakeAttributes
        )

        // Then
        verify(logger.mockDevLogHandler)
            .handleLog(
                fakeLevel.toLogLevel(),
                fakeMessage,
                fakeThrowable,
                fakeAttributes
            )
    }

    @Test
    fun `𝕄 send sdk log 𝕎 log { MAINTAINER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        val fakeAttributes = forge.exhaustiveAttributes()

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.MAINTAINER,
            fakeMessage,
            fakeThrowable,
            fakeAttributes
        )

        // Then
        verify(logger.mockSdkLogHandler)
            .handleLog(
                fakeLevel.toLogLevel(),
                fakeMessage,
                fakeThrowable,
                fakeAttributes
            )
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, no throwable + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val fakeAttributes = forge.exhaustiveAttributes()

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            null,
            fakeAttributes
        )

        // Then
        verify(rumMonitor.mockInstance)
            .sendDebugTelemetryEvent(fakeMessage)
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, no throwable + warn or error }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.WARN, InternalLogger.Level.ERROR)
        val fakeAttributes = forge.exhaustiveAttributes()

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            null,
            fakeAttributes
        )

        // Then
        verify(rumMonitor.mockInstance)
            .sendErrorTelemetryEvent(fakeMessage, null)
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, with throwable}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aThrowable()
        val fakeAttributes = forge.exhaustiveAttributes()

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            fakeThrowable,
            fakeAttributes
        )

        // Then
        verify(rumMonitor.mockInstance)
            .sendErrorTelemetryEvent(fakeMessage, fakeThrowable)
    }

    private fun InternalLogger.Level.toLogLevel(): Int {
        return when (this) {
            InternalLogger.Level.DEBUG -> Log.DEBUG
            InternalLogger.Level.INFO -> Log.INFO
            InternalLogger.Level.WARN -> Log.WARN
            InternalLogger.Level.ERROR -> Log.ERROR
        }
    }

    companion object {
        val logger = LoggerTestConfiguration()
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, rumMonitor)
        }
    }
}
