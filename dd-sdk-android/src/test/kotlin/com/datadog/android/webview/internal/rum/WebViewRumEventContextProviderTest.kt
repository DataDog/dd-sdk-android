/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.rum

import android.util.Log
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebViewRumEventContextProviderTest {

    lateinit var testedContextProvider: WebViewRumEventContextProvider

    @BeforeEach
    fun `set up`() {
        testedContextProvider = WebViewRumEventContextProvider()
    }

    @Test
    fun `M return the active context W getRumContext()`() {
        assertThat(testedContextProvider.getRumContext())
            .isEqualTo(globalRumMonitorTestConfiguration.context)
    }

    @Test
    fun `M return null W getRumContext(){ applicationId was null }`() {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(applicationId = RumContext.NULL_UUID)
        )

        // Then
        assertThat(testedContextProvider.getRumContext()).isNull()
    }

    @Test
    fun `M return null W getRumContext(){ sessionId was null }`() {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(sessionId = RumContext.NULL_UUID)
        )

        // Then
        assertThat(testedContextProvider.getRumContext()).isNull()
    }

    @Test
    fun `M log a dev warning log W getRumContext(){ applicationId is null }`() {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(applicationId = RumContext.NULL_UUID)
        )

        // When
        testedContextProvider.getRumContext()

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M log an sdk error log W getRumContext(){ application is null }`() {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(applicationId = RumContext.NULL_UUID)
        )

        // When
        testedContextProvider.getRumContext()

        // Then
        verify(logger.mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_ERROR_MESSAGE
        )
    }

    @Test
    fun `M log a dev warning log W getRumContext(){ sessionId is null }`() {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(sessionId = RumContext.NULL_UUID)
        )

        // When
        testedContextProvider.getRumContext()

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
    }

    @Test
    fun `M log an sdk error log W getRumContext(){ sessionId is null }`() {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(sessionId = RumContext.NULL_UUID)
        )

        // When
        testedContextProvider.getRumContext()

        // Then
        verify(logger.mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_ERROR_MESSAGE
        )
    }

    @Test
    fun `M return without internal logging when retrying { sessionId is null }`(
        forge: Forge
    ) {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(sessionId = RumContext.NULL_UUID)
        )

        // When
        repeat(forge.anInt(min = 1, max = 10)) {
            testedContextProvider.getRumContext()
        }

        // Then
        verify(logger.mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_ERROR_MESSAGE
        )
        verifyNoMoreInteractions(logger.mockSdkLogHandler)
    }

    @Test
    fun `M return without internal logging when retrying { applicationId is null }`(
        forge: Forge
    ) {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(applicationId = RumContext.NULL_UUID)
        )

        // When
        repeat(forge.anInt(min = 1, max = 10)) {
            testedContextProvider.getRumContext()
        }

        // Then
        verify(logger.mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_ERROR_MESSAGE
        )
        verifyNoMoreInteractions(logger.mockSdkLogHandler)
    }

    @Test
    fun `M return without dev logging when retrying { sessionId is null }`(
        forge: Forge
    ) {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(sessionId = RumContext.NULL_UUID)
        )

        // When
        repeat(forge.anInt(min = 1, max = 10)) {
            testedContextProvider.getRumContext()
        }

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
        verifyNoMoreInteractions(logger.mockDevLogHandler)
    }

    @Test
    fun `M return without dev logging when retrying { applicationId is null }`(
        forge: Forge
    ) {
        // Given
        GlobalRum.updateRumContext(
            globalRumMonitorTestConfiguration.context
                .copy(applicationId = RumContext.NULL_UUID)
        )

        // When
        repeat(forge.anInt(min = 1, max = 10)) {
            testedContextProvider.getRumContext()
        }

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            Log.WARN,
            WebViewRumEventContextProvider.RUM_NOT_INITIALIZED_WARNING_MESSAGE
        )
        verifyNoMoreInteractions(logger.mockDevLogHandler)
    }

    companion object {
        val globalRumMonitorTestConfiguration = GlobalRumMonitorTestConfiguration()
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger, globalRumMonitorTestConfiguration)
        }
    }
}
