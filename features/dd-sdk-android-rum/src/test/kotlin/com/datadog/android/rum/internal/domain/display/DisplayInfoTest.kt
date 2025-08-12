/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.display

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DisplayInfoTest {

    // region toMap Tests

    @Test
    fun `M create map with brightness W toMap() { brightness present }`() {
        // Given
        val displayInfo = DisplayInfo(screenBrightness = 0.8f)

        // When
        val result = displayInfo.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(DisplayInfo.SCREEN_BRIGHTNESS_KEY to 0.8f)
        )
    }

    @Test
    fun `M create map with integer brightness W toMap() { integer brightness }`() {
        // Given
        val displayInfo = DisplayInfo(screenBrightness = 255)

        // When
        val result = displayInfo.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(DisplayInfo.SCREEN_BRIGHTNESS_KEY to 255)
        )
    }

    @Test
    fun `M create empty map W toMap() { null brightness }`() {
        // Given
        val displayInfo = DisplayInfo(screenBrightness = null)

        // When
        val result = displayInfo.toMap()

        // Then
        assertThat(result).isEmpty()
    }

    // endregion

    // region fromMap Tests

    @Test
    fun `M create DisplayInfo with integer brightness W fromMap()`() {
        // Given
        val map = mapOf(DisplayInfo.SCREEN_BRIGHTNESS_KEY to 128)

        // When
        val result = DisplayInfo.fromMap(map)

        // Then
        assertThat(result.screenBrightness).isEqualTo(128)
    }

    @Test
    fun `M create DisplayInfo with null brightness W fromMap() { empty map }`() {
        // Given
        val map = emptyMap<String, Any>()

        // When
        val result = DisplayInfo.fromMap(map)

        // Then
        assertThat(result.screenBrightness).isNull()
    }

    @Test
    fun `M handle wrong type W fromMap() { invalid type in map }`() {
        // Given
        val map = mapOf(DisplayInfo.SCREEN_BRIGHTNESS_KEY to "not a number")

        // When
        val result = DisplayInfo.fromMap(map)

        // Then - should gracefully handle wrong type by returning null
        assertThat(result.screenBrightness).isNull()
    }

    // endregion

    // region Round-trip Tests

    @Test
    fun `M preserve data integrity W toMap then fromMap { float round-trip }`() {
        // Given
        val originalDisplayInfo = DisplayInfo(screenBrightness = 0.42f)

        // When
        val map = originalDisplayInfo.toMap()
        val deserializedDisplayInfo = DisplayInfo.fromMap(map)

        // Then
        assertThat(deserializedDisplayInfo).isEqualTo(originalDisplayInfo)
    }

    @Test
    fun `M preserve data integrity W toMap then fromMap { integer round-trip }`() {
        // Given
        val originalDisplayInfo = DisplayInfo(screenBrightness = 200)

        // When
        val map = originalDisplayInfo.toMap()
        val deserializedDisplayInfo = DisplayInfo.fromMap(map)

        // Then
        assertThat(deserializedDisplayInfo).isEqualTo(originalDisplayInfo)
    }

    @Test
    fun `M preserve null in round-trip W toMap then fromMap { null value }`() {
        // Given
        val originalDisplayInfo = DisplayInfo(screenBrightness = null)

        // When
        val map = originalDisplayInfo.toMap()
        val deserializedDisplayInfo = DisplayInfo.fromMap(map)

        // Then
        assertThat(deserializedDisplayInfo).isEqualTo(originalDisplayInfo)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `M handle edge brightness values W round-trip { boundary values }`() {
        val testCases = listOf(
            0.0f,
            1.0f,
            0,
            255,
            Float.MIN_VALUE,
            Float.MAX_VALUE
        )

        testCases.forEach { brightness ->
            // Given
            val originalDisplayInfo = DisplayInfo(screenBrightness = brightness)

            // When
            val map = originalDisplayInfo.toMap()
            val deserializedDisplayInfo = DisplayInfo.fromMap(map)

            // Then
            assertThat(deserializedDisplayInfo)
                .isEqualTo(originalDisplayInfo)
        }
    }

    // endregion
}
