/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.utils.assertj.DeserializedActionEventAssert.Companion.assertThat
import com.datadog.android.rum.utils.assertj.DeserializedErrorEventAssert.Companion.assertThat
import com.datadog.android.rum.utils.assertj.DeserializedLongTaskEventAssert.Companion.assertThat
import com.datadog.android.rum.utils.assertj.DeserializedResourceEventAssert.Companion.assertThat
import com.datadog.android.rum.utils.assertj.DeserializedViewEventAssert.Companion.assertThat
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.telemetry.model.TelemetryDebugEvent
import com.datadog.android.telemetry.model.TelemetryErrorEvent
import com.google.gson.JsonParser
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventDeserializerTest {

    lateinit var testedDeserializer: RumEventDeserializer

    // we use a NoOpDataConstraints to avoid flaky tests
    private val serializer: RumEventSerializer = RumEventSerializer(
        mock(),
        mock {
            whenever(
                it.validateAttributes<Any?>(
                    any(),
                    anyOrNull(),
                    anyOrNull(),
                    anyOrNull()
                )
            ).thenAnswer {
                it.getArgument(0)
            }
            whenever(it.validateTags(any())).thenAnswer {
                it.getArgument(0)
            }
            whenever(it.validateTimings(any())).thenAnswer {
                it.getArgument(0)
            }
        }
    )

    @BeforeEach
    fun `set up`() {
        testedDeserializer = RumEventDeserializer(internalLogger = mock())
    }

    // region UnitTests

    @Test
    fun `𝕄 deserialize a serialized RUM ViewEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeViewEvent = forge.getForgery(ViewEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeViewEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent) as ViewEvent

        // THEN
        assertThat(deserializedEvent).isEqualTo(fakeViewEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM ResourceEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeResourceEvent = forge.getForgery(ResourceEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeResourceEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent) as ResourceEvent

        // THEN
        assertThat(deserializedEvent).isEqualTo(fakeResourceEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM ActionEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeActionEvent = forge.getForgery(ActionEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeActionEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent) as ActionEvent

        // THEN
        assertThat(deserializedEvent).isEqualTo(fakeActionEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM ErrorEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeErrorEvent = forge.getForgery(ErrorEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeErrorEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent) as ErrorEvent

        // THEN
        assertThat(deserializedEvent).isEqualTo(fakeErrorEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM LongTaskEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeLongTaskEvent = forge.getForgery(LongTaskEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeLongTaskEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent) as LongTaskEvent

        // THEN
        assertThat(deserializedEvent).isEqualTo(fakeLongTaskEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM TelemetryDebugEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeTelemetryDebugEvent = forge.getForgery(TelemetryDebugEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeTelemetryDebugEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)
            as TelemetryDebugEvent

        // THEN
        assertThat(deserializedEvent)
            .usingRecursiveComparison()
            .isEqualTo(fakeTelemetryDebugEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM TelemetryErrorEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeTelemetryErrorEvent = forge.getForgery(TelemetryErrorEvent::class.java)
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeTelemetryErrorEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)
            as TelemetryErrorEvent

        // THEN
        assertThat(deserializedEvent)
            .usingRecursiveComparison()
            .isEqualTo(fakeTelemetryErrorEvent)
    }

    @Test
    fun `𝕄 return null W deserialize { wrong bundled RUM event type }`() {
        // GIVEN
        val fakeBadFormatEvent = Any()
        val serializedEvent = JsonParser.parseString(serializer.serialize(fakeBadFormatEvent))
            .asJsonObject

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)

        // THEN
        assertThat(deserializedEvent).isNull()
    }

    @Test
    fun `𝕄 return null W deserialize { wrong status for telemetry type }`(
        forge: Forge
    ) {
        // GIVEN
        val fakeTelemetryEvent = forge.anElementFrom(
            forge.getForgery(TelemetryErrorEvent::class.java),
            forge.getForgery(TelemetryDebugEvent::class.java)
        )
        val serializedEvent = serializer.serialize(fakeTelemetryEvent)

        val eventWithWrongStatus = JsonParser.parseString(serializedEvent)
            .asJsonObject
            .apply {
                val telemetry = getAsJsonObject("telemetry")
                telemetry.addProperty("status", forge.anAlphabeticalString())
            }

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(eventWithWrongStatus)

        // THEN
        assertThat(deserializedEvent).isNull()
    }

    // endregion
}
