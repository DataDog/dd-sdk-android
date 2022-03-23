/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.core.internal.persistence.DataWriter
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.domain.event.RumEventSourceProvider
import com.datadog.android.rum.internal.domain.scope.RumRawEvent
import com.datadog.android.telemetry.assertj.TelemetryDebugEventAssert
import com.datadog.android.telemetry.assertj.TelemetryErrorEventAssert
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.forge.aThrowable
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TelemetryEventHandlerTest {

    private lateinit var testedTelemetryHandler: TelemetryEventHandler

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockWriter: DataWriter<Any>

    @Mock
    lateinit var mockSourceProvider: RumEventSourceProvider

    @StringForgery
    lateinit var mockServiceName: String

    @StringForgery
    lateinit var mockSdkVersion: String

    private var fakeServerOffset: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeServerOffset = forge.aLong(-50000, 50000)

        whenever(mockTimeProvider.getServerOffsetMillis()) doReturn fakeServerOffset

        whenever(mockSourceProvider.telemetryDebugEventSource) doReturn
            TelemetryDebugEvent.Source.ANDROID
        whenever(mockSourceProvider.telemetryErrorEventSource) doReturn
            TelemetryErrorEvent.Source.ANDROID

        testedTelemetryHandler =
            TelemetryEventHandler(
                mockServiceName,
                mockSdkVersion,
                mockSourceProvider,
                mockTimeProvider
            )
    }

    @Test
    fun `𝕄 create debug event 𝕎 handleEvent(SendTelemetry) { debug event status }`(forge: Forge) {
        // Given
        val debugRawEvent = forge.createRumRawTelemetryDebugEvent()

        val rumContext = GlobalRum.getRumContext()

        // When
        testedTelemetryHandler.handleEvent(debugRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryDebugEvent> {
            verify(mockWriter).write(capture())
            TelemetryDebugEventAssert.assertThat(lastValue).apply {
                hasDate(debugRawEvent.eventTime.timestamp + fakeServerOffset)
                hasSource(TelemetryDebugEvent.Source.ANDROID)
                hasMessage(debugRawEvent.message)
                hasService(mockServiceName)
                hasVersion(mockSdkVersion)
                hasApplicationId(rumContext.applicationId)
                hasSessionId(rumContext.sessionId)
                hasViewId(rumContext.viewId)
                hasActionId(rumContext.actionId)
            }
        }
    }

    @Test
    fun `𝕄 create error event 𝕎 handleEvent(SendTelemetry) { error event status }`(forge: Forge) {
        // Given
        val errorRawEvent = forge.createRumRawTelemetryErrorEvent()

        val rumContext = GlobalRum.getRumContext()

        // When
        testedTelemetryHandler.handleEvent(errorRawEvent, mockWriter)

        // Then
        argumentCaptor<TelemetryErrorEvent> {
            verify(mockWriter).write(capture())
            TelemetryErrorEventAssert.assertThat(lastValue).apply {
                hasDate(errorRawEvent.eventTime.timestamp + fakeServerOffset)
                hasSource(TelemetryErrorEvent.Source.ANDROID)
                hasMessage(errorRawEvent.message)
                hasService(mockServiceName)
                hasVersion(mockSdkVersion)
                hasApplicationId(rumContext.applicationId)
                hasSessionId(rumContext.sessionId)
                hasViewId(rumContext.viewId)
                hasActionId(rumContext.actionId)
                hasErrorStack(errorRawEvent.throwable?.loggableStackTrace())
                hasErrorKind(errorRawEvent.throwable?.javaClass?.canonicalName)
            }
        }
    }

    // region private

    private fun Forge.createRumRawTelemetryDebugEvent(): RumRawEvent.SendTelemetry {
        return RumRawEvent.SendTelemetry(
            TelemetryType.DEBUG,
            aString(),
            null
        )
    }

    private fun Forge.createRumRawTelemetryErrorEvent(): RumRawEvent.SendTelemetry {
        return RumRawEvent.SendTelemetry(
            TelemetryType.ERROR,
            aString(),
            aNullable { aThrowable() }
        )
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
