/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core

import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.utils.forge.Configurator
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    lateinit var mockUserLogHandler: LogcatLogHandler

    @Mock
    lateinit var mockMaintainerLogHandler: LogcatLogHandler

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
            userLogHandlerFactory = { mockUserLogHandler },
            maintainerLogHandlerFactory = { mockMaintainerLogHandler }
        )
    }

    fun callWithLambda(i: Int, lambda: () -> String) {
        if (i == 0) {
            print(lambda())
        }
    }

    fun callWithString(i: Int, str: String) {
        if (i == 0) {
            print(str)
        }
    }

    // region Target.USER

    @Test
    fun `𝕄 send user log 𝕎 log { USER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockUserLogHandler.canLog(any())) doReturn true

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.USER,
            mockLambda,
            fakeThrowable
        )

        // Then
        verify(mockUserLogHandler)
            .log(
                fakeLevel.toLogLevel(),
                "[$fakeInstanceName]: $fakeMessage",
                fakeThrowable
            )
    }

    @Test
    fun `𝕄 send user log only once 𝕎 log { USER target, onlyOnce=true }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockUserLogHandler.canLog(any())) doReturn true

        // When
        repeat(10) {
            testedInternalLogger.log(
                fakeLevel,
                InternalLogger.Target.USER,
                mockLambda,
                fakeThrowable,
                true
            )
        }

        // Then
        verify(mockUserLogHandler)
            .log(
                fakeLevel.toLogLevel(),
                "[$fakeInstanceName]: $fakeMessage",
                fakeThrowable
            )
    }

    @Test
    fun `𝕄 send user log with condition 𝕎 log { USER target }`(
        @IntForgery(min = Log.VERBOSE, max = (Log.ASSERT + 1)) sdkVerbosity: Int
    ) {
        // Given
        Datadog.setVerbosity(sdkVerbosity)

        // When
        testedInternalLogger = SdkInternalLogger(
            sdkCore = mockSdkCore,
            maintainerLogHandlerFactory = { mockMaintainerLogHandler }
        )

        // Then
        val predicate = testedInternalLogger.userLogger.predicate
        for (i in 0..10) {
            if (i >= sdkVerbosity) {
                Assertions.assertThat(predicate(i)).isTrue
            } else {
                Assertions.assertThat(predicate(i)).isFalse
            }
        }
    }

    @Test
    fun `𝕄 not evaluate lambda 𝕎 log { USER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockUserLogHandler.canLog(any())) doReturn false

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.USER,
            mockLambda,
            fakeThrowable,
            true
        )

        // Then
        verify(mockLambda, never()).invoke()
    }

    // endregion

    @Test
    fun `𝕄 send maintainer log 𝕎 log { MAINTAINER target }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockMaintainerLogHandler.canLog(any())) doReturn true

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.MAINTAINER,
            mockLambda,
            fakeThrowable
        )

        // Then
        verify(mockMaintainerLogHandler).log(
            fakeLevel.toLogLevel(),
            "[$fakeInstanceName]: $fakeMessage",
            fakeThrowable
        )
    }

    @Test
    fun `𝕄 send maintainer log only once 𝕎 log { MAINTAINER target, onlyOnce=true }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aNullable { forge.aThrowable() }
        whenever(mockMaintainerLogHandler.canLog(any())) doReturn true

        // When
        repeat(10) {
            testedInternalLogger.log(
                fakeLevel,
                InternalLogger.Target.MAINTAINER,
                mockLambda,
                fakeThrowable,
                true
            )
        }

        // Then
        verify(mockMaintainerLogHandler).log(
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
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
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
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, additional properties + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val fakeAdditionalProperties = forge.aMap {
            forge.anAlphabeticalString() to forge.aNullable { anAlphabeticalString() }
        }
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            null,
            additionalProperties = fakeAdditionalProperties
        )

        // Then
        verify(mockRumFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "telemetry_debug",
                    "message" to fakeMessage,
                    "additionalProperties" to fakeAdditionalProperties
                )
            )
    }

    @Test
    fun `𝕄 send telemetry log 𝕎 log { TELEMETRY target, additional prop empty + info or debug }`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
            null,
            additionalProperties = emptyMap()
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
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.WARN, InternalLogger.Level.ERROR)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
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
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.aValueFrom(InternalLogger.Level::class.java)
        val fakeThrowable = forge.aThrowable()
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        testedInternalLogger.log(
            fakeLevel,
            InternalLogger.Target.TELEMETRY,
            mockLambda,
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

    @Test
    fun `𝕄 send telemetry log only once 𝕎 log { TELEMETRY target, onlyOnce=true}`(
        @StringForgery fakeMessage: String,
        forge: Forge
    ) {
        // Given
        val mockLambda: () -> String = mock()
        whenever(mockLambda.invoke()) doReturn fakeMessage
        val fakeLevel = forge.anElementFrom(InternalLogger.Level.INFO, InternalLogger.Level.DEBUG)
        val mockRumFeatureScope = mock<FeatureScope>()
        whenever(mockSdkCore.getFeature(Feature.RUM_FEATURE_NAME)) doReturn mockRumFeatureScope

        // When
        repeat(10) {
            testedInternalLogger.log(
                fakeLevel,
                InternalLogger.Target.TELEMETRY,
                mockLambda,
                null,
                true
            )
        }

        // Then
        verify(mockRumFeatureScope)
            .sendEvent(
                mapOf(
                    "type" to "telemetry_debug",
                    "message" to fakeMessage
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
