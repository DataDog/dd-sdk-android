/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.forge.aStringNotMatchingSet
import com.datadog.android.rum.utils.verifyLog
import com.datadog.android.telemetry.model.TelemetryConfigurationEvent
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.datadog.android.telemetry.model.TelemetryUsageEvent
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock
import java.util.Locale

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class TelemetryEventExtTest {

    private lateinit var fakeInvalidSource: String
    private lateinit var fakeValidTelemetrySource: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        // we are using the TelemetryDebugEvent.Source here as the source enum for all the events is
        // generated from the same _common-schema.json
        fakeInvalidSource = forge.aStringNotMatchingSet(
            TelemetryDebugEvent.Source.values()
                .map {
                    it.toJson().asString
                }.toSet()
        )
        fakeValidTelemetrySource = forge.aValueFrom(TelemetryDebugEvent.Source::class.java)
            .toJson().asString
    }

    // region TelemetryDebugEvent

    @Test
    fun `M resolve the TelemetryDebugEvent source W telemetryDebugEventSource`() {
        assertThat(
            TelemetryDebugEvent.Source.tryFromSource(fakeValidTelemetrySource, mock())
                ?.toJson()?.asString
        )
            .isEqualTo(fakeValidTelemetrySource)
    }

    @Test
    fun `M return null W telemetryDebugEventSource { unknown source }`() {
        assertThat(TelemetryDebugEvent.Source.tryFromSource(fakeInvalidSource, mock())).isNull()
    }

    @Test
    fun `M send an error dev log W telemetryDebugEventSource { unknown source }`() {
        // When
        val mockInternalLogger = mock<InternalLogger>()
        TelemetryDebugEvent.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region TelemetryErrorEvent

    @Test
    fun `M resolve the TelemetryErrorEvent source W telemetryErrorEventSource`() {
        assertThat(
            TelemetryErrorEvent.Source.tryFromSource(fakeValidTelemetrySource, mock())
                ?.toJson()?.asString
        )
            .isEqualTo(fakeValidTelemetrySource)
    }

    @Test
    fun `M return null W telemetryErrorEventSource { unknown source }`() {
        assertThat(TelemetryErrorEvent.Source.tryFromSource(fakeInvalidSource, mock())).isNull()
    }

    @Test
    fun `M send an error dev log W telemetryErrorEventSource { unknown source }`() {
        // When
        val mockInternalLogger = mock<InternalLogger>()
        TelemetryErrorEvent.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region TelemetryUsageEvent

    @Test
    fun `M resolve the TelemetryUsageEvent source W telemetryUsageEventSource`() {
        assertThat(
            TelemetryUsageEvent.Source.tryFromSource(fakeValidTelemetrySource, mock())
                ?.toJson()?.asString
        )
            .isEqualTo(fakeValidTelemetrySource)
    }

    @Test
    fun `M return null W telemetryUsageEventSource { unknown source }`() {
        assertThat(TelemetryUsageEvent.Source.tryFromSource(fakeInvalidSource, mock())).isNull()
    }

    @Test
    fun `M send an error dev log W telemetryUsageEventSource { unknown source }`() {
        // When
        val mockInternalLogger = mock<InternalLogger>()
        TelemetryUsageEvent.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion

    // region TelemetryConfigurationEvent

    @Test
    fun `M resolve the TelemetryConfigurationEvent source W telemetryConfigurationEventSource`() {
        assertThat(
            TelemetryConfigurationEvent.Source.tryFromSource(fakeValidTelemetrySource, mock())
                ?.toJson()?.asString
        )
            .isEqualTo(fakeValidTelemetrySource)
    }

    @Test
    fun `M return null W telemetryConfigurationEventSource { unknown source }`() {
        assertThat(
            TelemetryConfigurationEvent.Source.tryFromSource(fakeInvalidSource, mock())
        ).isNull()
    }

    @Test
    fun `M send an error dev log W telemetryConfigurationEventSource { unknown source }`() {
        // When
        val mockInternalLogger = mock<InternalLogger>()
        TelemetryConfigurationEvent.Source.tryFromSource(fakeInvalidSource, mockInternalLogger)

        // Then
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            UNKNOWN_SOURCE_WARNING_MESSAGE_FORMAT.format(Locale.US, fakeInvalidSource),
            NoSuchElementException::class.java
        )
    }

    // endregion
}
