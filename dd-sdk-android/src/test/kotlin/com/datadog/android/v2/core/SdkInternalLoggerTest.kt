/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.android.v2.api.SdkCore
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkInternalLoggerTest {

    @Mock
    lateinit var mockDevLogHandler: LogcatLogHandler

    @Mock
    lateinit var mockSdkLogHandler: LogcatLogHandler

    private lateinit var testedInternalLogger: SdkInternalLogger

    @BeforeEach
    fun `set up`() {
        testedInternalLogger = SdkInternalLogger(
            devLogHandlerFactory = { mockDevLogHandler },
            sdkLogHandlerFactory = { mockSdkLogHandler }
        )
        Datadog.initialized.set(true)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.initialized.set(false)
        Datadog.globalSdkCore = NoOpSdkCore()
    }

    @Test
    fun `𝕄 send dev log 𝕎 log { USER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.USER,
            fakeMessage,
            fakeThrowable
        )

        // Then
        verify(mockDevLogHandler)
            .log(
                fakeLevel.toLogLevel(),
                fakeMessage,
                fakeThrowable
            )
    }

    @Test
    fun `𝕄 send dev log with condition 𝕎 log { USER target }`(
        @IntForgery(min = Log.VERBOSE, max = (Log.ASSERT + 1)) sdkVerbosity: Int
    ) {
        // Given
        val mockSdkCore: SdkCore = mock()
        whenever(mockSdkCore.getVerbosity()) doReturn sdkVerbosity
        Datadog.globalSdkCore = mockSdkCore

        // When
        testedInternalLogger = SdkInternalLogger(
            sdkLogHandlerFactory = { mockSdkLogHandler }
        )

        // Then
        val predicate = testedInternalLogger.devLogger.predicate
        for (i in 0..10) {
            if (i >= sdkVerbosity) {
                Assertions.assertThat(predicate(i)).isTrue
            } else {
                Assertions.assertThat(predicate(i)).isFalse
            }
        }
    }

    @Test
    fun `𝕄 send sdk log 𝕎 log { MAINTAINER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.MAINTAINER,
            fakeMessage,
            fakeThrowable
        )

        // Then
        verify(mockSdkLogHandler)
            .log(
                fakeLevel.toLogLevel(),
                fakeMessage,
                fakeThrowable
            )
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, no throwable + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            null
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

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            null
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

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            fakeThrowable
        )

        // Then
        verify(rumMonitor.mockInstance)
            .sendErrorTelemetryEvent(fakeMessage, fakeThrowable)
    }

    private fun InternalLogger.Level.toLogLevel(): Int {
        return when (this) {
            InternalLogger.Level.VERBOSE -> Log.VERBOSE
            InternalLogger.Level.DEBUG -> Log.DEBUG
            InternalLogger.Level.INFO -> Log.INFO
            InternalLogger.Level.WARN -> Log.WARN
            InternalLogger.Level.ERROR -> Log.ERROR
        }
    }

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
