/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.rum.utils.verifyLog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumEventMetaDeserializerTest {

    private lateinit var testedDeserializer: RumEventMetaDeserializer

    private val serializer = RumEventMetaSerializer()

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedDeserializer = RumEventMetaDeserializer(mockInternalLogger)
    }

    @Test
    fun `M deserialize metadata W deserialize()`(
        @Forgery fakeMeta: RumEventMeta
    ) {
        // Given
        val serializedMeta = serializer.serialize(fakeMeta).toByteArray()

        // When
        val deserialized = testedDeserializer.deserialize(serializedMeta)

        // Then
        assertThat(deserialized).isEqualTo(fakeMeta)
    }

    @Test
    fun `M return null W deserialize() { not a json }`(
        @StringForgery metadata: String
    ) {
        // When
        val result = testedDeserializer.deserialize(metadata.toByteArray())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMetaDeserializer.DESERIALIZATION_ERROR,
            JsonParseException::class.java
        )
    }

    @Test
    fun `M return null W deserialize() { not a json object }`() {
        // When
        val result = testedDeserializer.deserialize("[]".toByteArray())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMetaDeserializer.DESERIALIZATION_ERROR,
            JsonParseException::class.java
        )
    }

    @Test
    fun `M return null W deserialize() { missing properties }`(
        @Forgery fakeMeta: RumEventMeta,
        forge: Forge
    ) {
        // Given
        val fakeMetaJson = JsonParser.parseString(serializer.serialize(fakeMeta))
            .asJsonObject
            .apply {
                // Only remove required properties that should cause deserialization to fail
                // hasAccessibility is optional and has backward compatibility handling
                val requiredKeys = listOf(
                    RumEventMeta.TYPE_KEY,
                    RumEventMeta.VIEW_ID_KEY,
                    RumEventMeta.DOCUMENT_VERSION_KEY
                )
                remove(forge.anElementFrom(requiredKeys))
            }

        // When
        val result = testedDeserializer.deserialize(fakeMetaJson.toBytes())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMetaDeserializer.DESERIALIZATION_ERROR,
            JsonParseException::class.java
        )
    }

    @Test
    fun `M deserialize metadata W deserialize() { missing optional hasAccessibility property }`(
        @Forgery fakeMeta: RumEventMeta
    ) {
        // Given
        val fakeMetaJson = JsonParser.parseString(serializer.serialize(fakeMeta))
            .asJsonObject
            .apply {
                remove(RumEventMeta.HAS_ACCESSIBILITY_KEY)
            }

        // When
        val result = testedDeserializer.deserialize(fakeMetaJson.toBytes())

        // Then
        assertThat(result).isNotNull()
        if (result is RumEventMeta.View) {
            // hasAccessibility should default to false for backward compatibility
            assertThat(result.hasAccessibility).isEqualTo(false)
        }
    }

    @Test
    fun `M return null W deserialize() { unexpected type of property }`(
        @Forgery fakeMeta: RumEventMeta,
        forge: Forge
    ) {
        // Given
        val fakeMetaJson = JsonParser.parseString(serializer.serialize(fakeMeta))
            .asJsonObject
            .apply {
                val key = forge.anElementFrom(keySet())
                add(key, JsonArray())
            }

        // When
        val result = testedDeserializer.deserialize(fakeMetaJson.toBytes())

        // Then
        assertThat(result).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            RumEventMetaDeserializer.DESERIALIZATION_ERROR,
            JsonParseException::class.java
        )
    }

    // region private

    private fun JsonObject.toBytes(): ByteArray = toString().toByteArray()

    // endregion
}
