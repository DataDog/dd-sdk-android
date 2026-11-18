/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration

import android.app.ActivityManager.RunningAppProcessInfo
import com.datadog.android.api.feature.Feature
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.rum.RumContext
import com.datadog.android.rum.DdRumContentProvider
import com.datadog.android.rum.ExperimentalRumApi
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.configuration.ViewEventWriteConfig
import com.datadog.android.rum.integration.tests.assertj.hasRumEvent
import com.datadog.android.rum.integration.tests.elmyr.RumIntegrationForgeConfigurator
import com.datadog.android.rum.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.tests.assertj.StubEventsAssert.Companion.assertThat
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.AutoClose
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(RumIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@OptIn(ExperimentalRumApi::class)
internal class ApplicationLaunchRumTest {

    @AutoClose
    private lateinit var stubSdkCore: StubSDKCore

    @Mock
    private lateinit var mockSdkCore: InternalSdkCore

    @StringForgery
    private lateinit var fakeApplicationId: String

    private var previousProcessImportance: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        previousProcessImportance = DdRumContentProvider.processImportance
        DdRumContentProvider.processImportance = RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        whenever(mockSdkCore.name) doReturn forge.anAlphabeticalString()
        stubSdkCore = StubSDKCore(forge, mockSdkCore = mockSdkCore)
    }

    @AfterEach
    fun `tear down`() {
        DdRumContentProvider.processImportance = previousProcessImportance
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M write ApplicationLaunch and crash context W enable { foreground }`(
        alwaysFullView: Boolean,
        @LongForgery(min = 1, max = 10000) fakeInitializationDelayMs: Long
    ) {
        // Given
        stubSdkCore.advanceTimeBy(fakeInitializationDelayMs)
        val viewEventWriteConfig = if (alwaysFullView) {
            ViewEventWriteConfig.AlwaysFullView
        } else {
            ViewEventWriteConfig.FullViewOnlyAtStart
        }
        val configuration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setSessionSampleRate(100f)
            .setViewEventWriteConfig(viewEventWriteConfig)
            .build()

        // When
        Rum.enable(configuration, stubSdkCore)

        // Then
        val events = stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)
        assertThat(events)
            .hasSize(1)
            .hasRumEvent(0) {
                hasType("view")
                hasApplicationId(fakeApplicationId)
                hasViewName("ApplicationLaunch")
                hasViewIsActive(true)
                hasDocumentVersion(2)
                hasActionCount(0)
                hasErrorCount(0)
                hasResourceCount(0)
            }
        val event = JsonParser.parseString(events.single().eventData).asJsonObject
        assertThat(event.getAsJsonObject("view").get("time_spent").asLong)
            .isEqualTo(TimeUnit.MILLISECONDS.toNanos(fakeInitializationDelayMs))
        assertThat(event.getAsJsonObject("session").get("id").asString).isNotEqualTo(RumContext.NULL_UUID)
        argumentCaptor<ByteArray> {
            verify(mockSdkCore).writeLastViewEvent(capture())
            assertThat(String(firstValue, Charsets.UTF_8)).isEqualTo(events.single().eventData)
        }
    }

    @ParameterizedTest
    @CsvSource("true, 0.0", "false, 100.0")
    fun `M not write ApplicationLaunch W enable { sampled out or background }`(
        foreground: Boolean,
        sampleRate: Float
    ) {
        // Given
        DdRumContentProvider.processImportance = if (foreground) {
            RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        } else {
            RunningAppProcessInfo.IMPORTANCE_SERVICE
        }
        val configuration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .trackBackgroundEvents(true)
            .setSessionSampleRate(sampleRate)
            .build()

        // When
        Rum.enable(configuration, stubSdkCore)

        // Then
        assertThat(stubSdkCore.eventsWritten(Feature.RUM_FEATURE_NAME)).hasSize(0)
        verify(mockSdkCore, never()).writeLastViewEvent(any())
    }

    @Test
    fun `M replace ApplicationLaunch crash context W startView`(
        @StringForgery fakeViewKey: String,
        @StringForgery fakeViewName: String
    ) {
        // Given
        val configuration = RumConfiguration.Builder(fakeApplicationId)
            .trackNonFatalAnrs(false)
            .setSessionSampleRate(100f)
            .build()
        Rum.enable(configuration, stubSdkCore)

        // When
        GlobalRumMonitor.get(stubSdkCore).startView(fakeViewKey, fakeViewName)

        // Then
        val crashContexts = argumentCaptor<ByteArray>()
        verify(mockSdkCore, atLeastOnce()).writeLastViewEvent(crashContexts.capture())
        val initialView = JsonParser.parseString(String(crashContexts.firstValue, Charsets.UTF_8)).asJsonObject
        val currentView = JsonParser.parseString(String(crashContexts.lastValue, Charsets.UTF_8)).asJsonObject
        assertThat(initialView.getAsJsonObject("view").get("name").asString).isEqualTo("ApplicationLaunch")
        assertThat(currentView.getAsJsonObject("view").get("name").asString).isEqualTo(fakeViewName)
        assertThat(currentView.getAsJsonObject("session").get("id"))
            .isEqualTo(initialView.getAsJsonObject("session").get("id"))
    }

    companion object {
        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> = listOf(MainLooperTestConfiguration())
    }
}
