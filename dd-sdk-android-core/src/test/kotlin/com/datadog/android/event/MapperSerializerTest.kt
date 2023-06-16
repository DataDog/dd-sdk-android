/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.event

import com.datadog.android.core.persistence.Serializer
import com.datadog.android.utils.forge.Configurator
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MapperSerializerTest {

    lateinit var testedMapperSerializer: MapperSerializer<String>

    @Mock
    lateinit var mockMapper: EventMapper<String>

    @Mock
    lateinit var mockSerializer: Serializer<String>

    @BeforeEach
    fun `set up`() {
        testedMapperSerializer = MapperSerializer(mockMapper, mockSerializer)
    }

    @Test
    fun `𝕄 return null 𝕎 serialize() {mapper returns null}`(
        @StringForgery input: String
    ) {
        // Given
        whenever(mockMapper.map(input)) doReturn null

        // When
        val result = testedMapperSerializer.serialize(input)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return null 𝕎 serialize() {serializer returns null}`(
        @StringForgery input: String,
        @StringForgery mapped: String
    ) {
        // Given
        whenever(mockMapper.map(input)) doReturn mapped
        whenever(mockSerializer.serialize(mapped)) doReturn null

        // When
        val result = testedMapperSerializer.serialize(input)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `𝕄 return mapped then serialized data 𝕎 serialize()`(
        @StringForgery input: String,
        @StringForgery mapped: String,
        @StringForgery serialized: String
    ) {
        // Given
        whenever(mockMapper.map(input)) doReturn mapped
        whenever(mockSerializer.serialize(mapped)) doReturn serialized

        // When
        val result = testedMapperSerializer.serialize(input)

        // Then
        assertThat(result).isEqualTo(serialized)
    }
}
