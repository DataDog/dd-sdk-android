/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.v2.core

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.FeatureScope
import com.datadog.android.v2.api.FeatureSdkCore
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.forge.aThrowable
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class SdkInternalLoggerTest {

    @Mock
    lateinit var mockDevLogHandler: LogcatLogHandler

    @Mock
    lateinit var mockSdkLogHandler: LogcatLogHandler

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @StringForgery(type = StringForgeryType.ALPHA_NUMERICAL)
    lateinit var fakeInstanceName: String

    private lateinit var testedInternalLogger: SdkInternalLogger

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.name) doReturn fakeInstanceName

        testedInternalLogger = SdkInternalLogger(
            sdkCore = mockSdkCore,
            devLogHandlerFactory = { mockDevLogHandler },
            sdkLogHandlerFactory = { mockSdkLogHandler }
        )
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
                "[$fakeInstanceName]: $fakeMessage",
                fakeThrowable
            )
    }

    @Test
    fun `𝕄 send dev log with condition 𝕎 log { USER target }`(
        @IntForgery(min = Log.VERBOSE, max = (Log.ASSERT + 1)) sdkVerbosity: Int
    ) {
        // Given
        Datadog.setVerbosity(sdkVerbosity)

        // When
        testedInternalLogger = SdkInternalLogger(
            sdkCore = mockSdkCore,
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
                "[$fakeInstanceName]: $fakeMessage",
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
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            null
        )

        // Then
        verify(mockRumFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "telemetry_debug",
                    "message" to fakeMessage
                )
            )
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, no throwable + warn or error }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.WARN, InternalLogger.Level.ERROR)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            null
        )

        // Then
        verify(mockRumFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "telemetry_error",
                    "message" to fakeMessage,
                    "throwable" to null
                )
            )
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, with throwable}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aThrowable()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            fakeMessage,
            fakeThrowable
        )

        // Then
        verify(mockRumFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "telemetry_error",
                    "message" to fakeMessage,
                    "throwable" to fakeThrowable
                )
            )
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
}
