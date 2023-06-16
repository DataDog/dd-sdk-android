/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonObject
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class JsonObjectDeserializerTest {

    private val testedDeserializer = JsonObjectDeserializer(internalLogger = mock())

    @Test
    fun `𝕄 deserialize a serialized JsonObject 𝕎 deserialize()`(
        @Forgery fakeJsonObject: JsonObject
    ) {
        // Given
        val fakeModel = fakeJsonObject.toString()

        // When
        val deserialized = testedDeserializer.deserialize(fakeModel)

        // Then
        assertThat(deserialized.toString()).isEqualTo(fakeModel)
    }

    @Test
    fun `𝕄 return null 𝕎 deserialize() { json array }`(
        @Forgery fakeJsonObject: JsonObject
    ) {
        // Given
        val fakeModel = "[$fakeJsonObject]"

        // When
        val deserialized = testedDeserializer.deserialize(fakeModel)

        // Then
        assertThat(deserialized).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 deserialize() { malformed model }`(
        @StringForgery fakeModel: String
    ) {
        // When
        val deserialized = testedDeserializer.deserialize(fakeModel)

        // Then
        assertThat(deserialized).isNull()
    }
}
