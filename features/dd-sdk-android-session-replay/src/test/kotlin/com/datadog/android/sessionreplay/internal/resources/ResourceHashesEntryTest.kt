/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.resources.ResourceHashesEntry.Companion.LAST_UPDATE_DATE_KEY
import com.datadog.android.sessionreplay.internal.resources.ResourceHashesEntry.Companion.RESOURCE_HASHES_KEY
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
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
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceHashesEntryTest {
    private lateinit var testedResourceHashesEntry: ResourceHashesEntry

    @Test
    fun `M return valid json W toJson()`(
        @LongForgery fakeUpdateDate: Long,
        @StringForgery fakeHash: String
    ) {
        // Given
        val fakeHashSet = mutableSetOf<String>()
        fakeHashSet.add(fakeHash)
        testedResourceHashesEntry = ResourceHashesEntry(
            lastUpdateDateNs = fakeUpdateDate,
            resourceHashes = fakeHashSet
        )

        // When
        val actualJson = testedResourceHashesEntry.toJson().asJsonObject

        // Then
        assertThat(actualJson.get(LAST_UPDATE_DATE_KEY).asLong).isEqualTo(fakeUpdateDate)
        assertThat(actualJson.get(RESOURCE_HASHES_KEY).asString?.split(",")?.toSet())
            .isEqualTo(fakeHashSet)
    }

    @Test
    fun `M return entry  W fromJson() { valid json }`(
        @LongForgery fakeUpdateDate: Long,
        @StringForgery fakeHash: String
    ) {
        // Given
        val fakeHashSet = mutableSetOf<String>()
        fakeHashSet.add(fakeHash)
        val expectedEntry = ResourceHashesEntry(
            lastUpdateDateNs = fakeUpdateDate,
            resourceHashes = fakeHashSet
        )
        val json = expectedEntry.toJson().toString()

        // When
        val actualEntry = ResourceHashesEntry.fromJson(json)

        // Then
        assertThat(actualEntry).isEqualTo(expectedEntry)
    }

    @Test
    fun `M throw exception W fromJson() { invalid json }`(
        @StringForgery fakeInvalidJson: String
    ) {
        // Given
        var gotException = false

        // When
        try {
            ResourceHashesEntry.fromJson(fakeInvalidJson)
        } catch (e: JsonParseException) {
            gotException = true
        }

        // Then
        assertThat(gotException).isTrue()
    }

    @Test
    fun `M throw exception  W fromJson() { NumberFormatException on updateDate }`(
        @StringForgery fakeHash: String
    ) {
        // Given
        var gotException = false
        val fakeHashSet = mutableSetOf<String>()
        fakeHashSet.add(fakeHash)
        val json = JsonObject()
        json.addProperty(LAST_UPDATE_DATE_KEY, fakeHash)
        json.addProperty(RESOURCE_HASHES_KEY, fakeHashSet.joinToString(","))

        // When
        try {
            ResourceHashesEntry.fromJson(json.toString())
        } catch (e: JsonParseException) {
            gotException = true
        }

        // Then
        assertThat(gotException).isTrue()
    }

    @Test
    fun `M throw exception  W fromJson() { invalid json }`(
        @StringForgery fakeInvalidJson: String
    ) {
        // Given
        var gotException = false

        // When
        try {
            ResourceHashesEntry.fromJson(fakeInvalidJson)
        } catch (e: JsonParseException) {
            gotException = true
        }

        // Then
        assertThat(gotException).isTrue()
    }

    @Test
    fun `M return null W fromJson() { missing lastUpdateDate }`(
        @StringForgery fakeHash: String
    ) {
        // Given
        val fakeHashSet = mutableSetOf<String>()
        fakeHashSet.add(fakeHash)
        val json = JsonObject()
        json.addProperty(RESOURCE_HASHES_KEY, fakeHashSet.joinToString(","))

        // Then
        assertThat(ResourceHashesEntry.fromJson(json.toString())).isNull()
    }

    @Test
    fun `M return null W fromJson() { missing resourceHashes }`(
        @LongForgery fakeUpdateDate: Long
    ) {
        // Given
        val json = JsonObject()
        json.addProperty(LAST_UPDATE_DATE_KEY, fakeUpdateDate)

        // Then
        assertThat(ResourceHashesEntry.fromJson(json.toString())).isNull()
    }
}
