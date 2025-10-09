/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.log.internal.LogsFeature
import com.datadog.android.log.internal.net.LogsRequestFactory
import com.datadog.android.utils.forge.Configurator
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
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
internal class LogsTest {

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @BeforeEach
    fun `set up`() {
        whenever(mockSdkCore.internalLogger) doReturn mock()
    }

    @Test
    fun `M register logs feature W enable()`(
        @StringForgery fakePackageName: String,
        @Forgery fakeLogsConfiguration: LogsConfiguration
    ) {
        // When
        Logs.enable(fakeLogsConfiguration, mockSdkCore)

        // Then
        argumentCaptor<LogsFeature> {
            verify(mockSdkCore).registerFeature(capture())

            lastValue.onInitialize(
                appContext = mock { whenever(it.packageName) doReturn fakePackageName }
            )
            assertThat(lastValue.eventMapper).isEqualTo(fakeLogsConfiguration.eventMapper)
            assertThat((lastValue.requestFactory as LogsRequestFactory).customEndpointUrl)
                .isEqualTo(fakeLogsConfiguration.customEndpointUrl)
        }
    }

    @Test
    fun `M return true W isEnabled() { core returns feature }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn mock()

        // When
        val result = Logs.isEnabled(mockSdkCore)

        // Then
        assertThat(result).isTrue
    }

    @Test
    fun `M return false W isEnabled() { core returns null }`() {
        // Given
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null

        // When
        val result = Logs.isEnabled(mockSdkCore)

        // Then
        assertThat(result).isFalse
    }

    @Test
    fun `M log user error W addAttribute { logs not enabled }`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null

        // When
        Logs.addAttribute(key, value, mockSdkCore)

        // Then
        argumentCaptor<() -> String> {
            verify(mockSdkCore.internalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null),
                eq(false)
            )
            assertThat(lastValue()).isEqualTo(Logs.LOGS_NOT_ENABLED_MESSAGE)
        }
    }

    @Test
    fun `M log user error W removeAttribute { logs not enabled }`(
        @StringForgery key: String
    ) {
        // Given
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn null

        // When
        Logs.removeAttribute(key, mockSdkCore)

        // Then
        argumentCaptor<() -> String> {
            verify(mockSdkCore.internalLogger).log(
                eq(InternalLogger.Level.ERROR),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false),
                eq(null),
                eq(false)
            )
            assertThat(lastValue()).isEqualTo(Logs.LOGS_NOT_ENABLED_MESSAGE)
        }
    }

    @Test
    fun `M forward attributes to Feature W addAttribute`(
        @StringForgery key: String,
        @StringForgery value: String
    ) {
        // Given
        val mockFeatureScope: FeatureScope = mock()
        val mockLogsFeature: LogsFeature = mock()
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn mockFeatureScope
        whenever(mockFeatureScope.unwrap<LogsFeature>()) doReturn mockLogsFeature

        // When
        Logs.addAttribute(key, value, mockSdkCore)

        // Then
        verify(mockLogsFeature).addAttribute(key, value)
    }

    @Test
    fun `M forward remove attributes to Feature W removeAttribute`(
        @StringForgery key: String
    ) {
        // Given
        val mockFeatureScope: FeatureScope = mock()
        val mockLogsFeature: LogsFeature = mock()
        whenever(mockSdkCore.getFeature(Feature.LOGS_FEATURE_NAME)) doReturn mockFeatureScope
        whenever(mockFeatureScope.unwrap<LogsFeature>()) doReturn mockLogsFeature

        // When
        Logs.removeAttribute(key, mockSdkCore)

        // Then
        verify(mockLogsFeature).removeAttribute(key)
    }
}
