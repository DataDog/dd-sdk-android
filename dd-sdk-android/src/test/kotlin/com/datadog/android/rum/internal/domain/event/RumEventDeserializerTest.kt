/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.core.internal.utils.toJsonArray
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.fail
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
        mock {
            whenever(it.validateAttributes(any(), anyOrNull(), anyOrNull())).thenAnswer {
                it.getArgument(0)
            }
            whenever(it.validateTags(any())).thenAnswer {
                it.getArgument(0)
            }
        }
    )

    @BeforeEach
    fun `set up`() {
        testedDeserializer = RumEventDeserializer()
    }

    // region UnitTests

    @Test
    fun `𝕄 deserialize a serialized RUM ViewEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeViewEvent = forge.getForgery(ViewEvent::class.java)
        val fakeEvent: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = fakeViewEvent)
        val serializedEvent = serializer.serialize(fakeEvent)

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)

        // THEN
        assertThat(deserializedEvent).isNotNull()
        assertThat(deserializedEvent?.customTimings).isEqualTo(fakeEvent.customTimings)
        assertAttributes(fakeEvent.globalAttributes, deserializedEvent?.globalAttributes)
        assertAttributes(fakeEvent.userExtraAttributes, deserializedEvent?.userExtraAttributes)
        val deserializedViewEvent = deserializedEvent!!.event as ViewEvent
        assertThat(deserializedViewEvent)
            .isEqualTo(fakeViewEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM ResourceEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeResourceEvent = forge.getForgery(ResourceEvent::class.java)
        val fakeEvent: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = fakeResourceEvent)
        val serializedEvent = serializer.serialize(fakeEvent)

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)

        // THEN
        assertThat(deserializedEvent).isNotNull()
        assertThat(deserializedEvent?.customTimings).isEqualTo(fakeEvent.customTimings)
        assertAttributes(fakeEvent.globalAttributes, deserializedEvent?.globalAttributes)
        assertAttributes(fakeEvent.userExtraAttributes, deserializedEvent?.userExtraAttributes)
        val deserializedResourceEvent = deserializedEvent!!.event as ResourceEvent
        assertThat(deserializedResourceEvent).isEqualTo(fakeResourceEvent)
    }

    @Test
    fun `𝕄 deserialize a serialized RUM ActionEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeActionEvent = forge.getForgery(ActionEvent::class.java)
        val fakeEvent: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = fakeActionEvent)
        val serializedEvent = serializer.serialize(fakeEvent)

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)

        // THEN
        assertThat(deserializedEvent).isNotNull()
        assertThat(deserializedEvent?.customTimings).isEqualTo(fakeEvent.customTimings)
        assertAttributes(fakeEvent.globalAttributes, deserializedEvent?.globalAttributes)
        assertAttributes(fakeEvent.userExtraAttributes, deserializedEvent?.userExtraAttributes)
        val deserializedActionEvent = deserializedEvent!!.event as ActionEvent
        assertThat(deserializedActionEvent).isEqualToIgnoringGivenFields(
            fakeActionEvent,
            "dd"
        )
    }

    @Test
    fun `𝕄 deserialize a serialized RUM ErrorEvent 𝕎 deserialize()`(
        forge: Forge
    ) {
        // GIVEN
        val fakeErrorEvent = forge.getForgery(ErrorEvent::class.java)
        val fakeEvent: RumEvent = forge.getForgery(RumEvent::class.java)
            .copy(event = fakeErrorEvent)
        val serializedEvent = serializer.serialize(fakeEvent)

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)

        // THEN
        assertThat(deserializedEvent).isNotNull()
        assertThat(deserializedEvent?.customTimings).isEqualTo(fakeEvent.customTimings)
        assertAttributes(fakeEvent.globalAttributes, deserializedEvent?.globalAttributes)
        assertAttributes(fakeEvent.userExtraAttributes, deserializedEvent?.userExtraAttributes)
        val deserializedErrorEvent = deserializedEvent!!.event as ErrorEvent
        assertThat(deserializedErrorEvent).isEqualToIgnoringGivenFields(
            fakeErrorEvent,
            "dd"
        )
    }

    @Test
    fun `𝕄 return null W deserialize { wrong Json format }`() {
        // WHEN
        val deserializedEvent = testedDeserializer.deserialize("{]}")

        // THEN
        assertThat(deserializedEvent).isNull()
    }

    @Test
    fun `𝕄 return null W deserialize { wrong bundled RUM event type }`(
        @Forgery fakeEvent: RumEvent
    ) {
        // GIVEN
        val fakeBadFormatEvent = fakeEvent.copy(event = Any())
        val serializedEvent = serializer.serialize(fakeBadFormatEvent)

        // WHEN
        val deserializedEvent = testedDeserializer.deserialize(serializedEvent)

        // THEN
        assertThat(deserializedEvent).isNull()
    }

    // endregion

    // region Internal

    private fun assertAttributes(
        originalAttributes: Map<String, Any?>?,
        deserializedAttributes: Map<String, Any?>?
    ) {
        if (originalAttributes == null && deserializedAttributes == null) {
            return
        }
        if (originalAttributes != null && deserializedAttributes != null) {
            originalAttributes.filter {
                it.key.isNotBlank() && !RumEventSerializer.knownAttributes.contains(
                    it.key
                )
            }
                .forEach {
                    val value = it.value
                    val deserializedValue = deserializedAttributes[it.key] as JsonElement
                    when (value) {
                        null -> assertThat(deserializedValue).isEqualTo(
                            JsonNull.INSTANCE
                        )
                        is Boolean -> assertThat(deserializedValue.asBoolean).isEqualTo(value)
                        is Int -> assertThat(deserializedValue.asInt).isEqualTo(value)
                        is Long -> assertThat(deserializedValue.asLong).isEqualTo(value)
                        is Float -> assertThat(deserializedValue.asFloat).isEqualTo(value)
                        is Double -> assertThat(deserializedValue.asDouble).isEqualTo(value)
                        is String -> assertThat(deserializedValue.asString).isEqualTo(value)
                        is Date -> assertThat(deserializedValue.asLong).isEqualTo(value.time)
                        is JsonObject ->
                            assertThat(deserializedValue.asJsonObject.toString())
                                .isEqualTo(value.toString())
                        is JsonArray -> assertThat(deserializedValue.asJsonArray).isEqualTo(value)
                        is Iterable<*> -> assertThat(deserializedValue.asJsonArray).isEqualTo(
                            value.toJsonArray()
                        )
                        else -> assertThat(deserializedValue.asString).isEqualTo(value.toString())
                    }
                }
        } else {
            fail(
                "Original attributes:$originalAttributes are not the same " +
                    "as deserialized attributes: $deserializedAttributes"
            )
        }
    }

    // endregion
}
