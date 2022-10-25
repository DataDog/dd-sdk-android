/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import android.util.Log
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.utils.config.LoggerTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aStringNotMatchingSet
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
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
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventSourceProviderTest {

    lateinit var testedRumEventSourceProvider: RumEventSourceProvider

    lateinit var fakeInvalidSource: String
    lateinit var fakeValidRumSource: String
    lateinit var fakeValidTelemetrySource: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        // we are using the ViewEvent.Source here as the source enum for all the events is
        // generated from the same _common-schema.json
        fakeInvalidSource = forge.aStringNotMatchingSet(
            ViewEvent.Source.values()
                .map {
                    it.toJson().asString
                }.toSet()
        )
        fakeValidRumSource = forge.aValueFrom(ViewEvent.Source::class.java).toJson().asString
        fakeValidTelemetrySource = forge.aValueFrom(TelemetryDebugEvent.Source::class.java).toJson().asString
    }

    // region ViewEvent

    @Test
    fun `M resolve the ViewEvent source W viewEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidRumSource)

        // Then
        assertThat(testedRumEventSourceProvider.viewEventSource)
            .isEqualTo(ViewEvent.Source.fromJson(fakeValidRumSource))
    }

    @Test
    fun `M return null W viewEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.viewEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W viewEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.viewEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    // region ActionEvent

    @Test
    fun `M resolve the Action source W actionEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidRumSource)

        // Then
        assertThat(testedRumEventSourceProvider.actionEventSource)
            .isEqualTo(ActionEvent.Source.fromJson(fakeValidRumSource))
    }

    @Test
    fun `M return null W actionEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.actionEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W actionEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.actionEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    // region ErrorEvent

    @Test
    fun `M resolve the ErrorEvent source W errorEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidRumSource)

        // Then
        assertThat(testedRumEventSourceProvider.errorEventSource)
            .isEqualTo(ErrorEvent.ErrorEventSource.fromJson(fakeValidRumSource))
    }

    @Test
    fun `M return null W errorEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.errorEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W errorEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.errorEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    // region ResourceEvent

    @Test
    fun `M resolve the ResourceEvent source W resourceEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidRumSource)

        // Then
        assertThat(testedRumEventSourceProvider.resourceEventSource)
            .isEqualTo(ResourceEvent.Source.fromJson(fakeValidRumSource))
    }

    @Test
    fun `M return null W resourceEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.resourceEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W resourceEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.resourceEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    // region LongTaskEvent

    @Test
    fun `M resolve the LongTaskEvent source W longTaskEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidRumSource)

        // Then
        assertThat(testedRumEventSourceProvider.longTaskEventSource)
            .isEqualTo(LongTaskEvent.Source.fromJson(fakeValidRumSource))
    }

    @Test
    fun `M return null W longTaskEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.longTaskEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W longTaskEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.longTaskEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    // region TelemetryDebugEvent

    @Test
    fun `M resolve the TelemetryDebugEvent source W telemetryDebugEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidTelemetrySource)

        // Then
        assertThat(testedRumEventSourceProvider.telemetryDebugEventSource)
            .isEqualTo(TelemetryDebugEvent.Source.fromJson(fakeValidTelemetrySource))
    }

    @Test
    fun `M return null W telemetryDebugEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.telemetryDebugEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W telemetryDebugEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.telemetryDebugEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    // region TelemetryErrorEvent

    @Test
    fun `M resolve the TelemetryErrorEvent source W telemetryErrorEventSource`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeValidTelemetrySource)

        // Then
        assertThat(testedRumEventSourceProvider.telemetryErrorEventSource)
            .isEqualTo(TelemetryErrorEvent.Source.fromJson(fakeValidTelemetrySource))
    }

    @Test
    fun `M return null W telemetryErrorEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // Then
        assertThat(testedRumEventSourceProvider.telemetryErrorEventSource).isNull()
    }

    @Test
    fun `M send an error dev log W telemetryErrorEventSource { unknown source }`() {
        // Given
        testedRumEventSourceProvider = RumEventSourceProvider(fakeInvalidSource)

        // When
        testedRumEventSourceProvider.telemetryErrorEventSource

        // Then
        verify(logger.mockDevLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                RumEventSourceProvider.UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT
                    .format(Locale.US, fakeInvalidSource)
            ),
            argThat { this is NoSuchElementException },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    // endregion

    companion object {
        val logger = LoggerTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(logger)
        }
    }
}
