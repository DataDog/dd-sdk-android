/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class BatteryInfoTest {

    // region toMap Tests

    @Test
    fun `M create map with all values W toMap() { all fields present }`() {
        // Given
        val batteryInfo = BatteryInfo(
            batteryLevel = 0.75f,
            lowPowerMode = true
        )

        // When
        val result = batteryInfo.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(
                BatteryInfo.BATTERY_LEVEL_KEY to 0.75f,
                BatteryInfo.LOW_POWER_MODE_KEY to true
            )
        )
    }

    @Test
    fun `M create map with partial values W toMap() { some fields null }`() {
        // Given
        val batteryInfo = BatteryInfo(
            batteryLevel = 0.25f,
            lowPowerMode = null
        )

        // When
        val result = batteryInfo.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(BatteryInfo.BATTERY_LEVEL_KEY to 0.25f)
        )
    }

    @Test
    fun `M create empty map W toMap() { all fields null }`() {
        // Given
        val batteryInfo = BatteryInfo(
            batteryLevel = null,
            lowPowerMode = null
        )

        // When
        val result = batteryInfo.toMap()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M create map with only low power mode W toMap() { only low power mode set }`() {
        // Given
        val batteryInfo = BatteryInfo(
            batteryLevel = null,
            lowPowerMode = false
        )

        // When
        val result = batteryInfo.toMap()

        // Then
        assertThat(result).containsExactlyInAnyOrderEntriesOf(
            mapOf(BatteryInfo.LOW_POWER_MODE_KEY to false)
        )
    }

    // endregion

    // region fromMap Tests

    @Test
    fun `M create BatteryInfo with all values W fromMap() { complete map }`() {
        // Given
        val map = mapOf(
            BatteryInfo.BATTERY_LEVEL_KEY to 0.85f,
            BatteryInfo.LOW_POWER_MODE_KEY to true
        )

        // When
        val result = BatteryInfo.fromMap(map)

        // Then
        assertThat(result.batteryLevel).isEqualTo(0.85f)
        assertThat(result.lowPowerMode).isEqualTo(true)
    }

    @Test
    fun `M create BatteryInfo with partial values W fromMap() { partial map }`() {
        // Given
        val map = mapOf(BatteryInfo.BATTERY_LEVEL_KEY to 0.5f)

        // When
        val result = BatteryInfo.fromMap(map)

        // Then
        assertThat(result.batteryLevel).isEqualTo(0.5f)
        assertThat(result.lowPowerMode).isNull()
    }

    @Test
    fun `M create BatteryInfo with nulls W fromMap() { empty map }`() {
        // Given
        val map = emptyMap<String, Any>()

        // When
        val result = BatteryInfo.fromMap(map)

        // Then
        assertThat(result.batteryLevel).isNull()
        assertThat(result.lowPowerMode).isNull()
    }

    @Test
    fun `M handle wrong types W fromMap() { invalid types in map }`() {
        // Given
        val map = mapOf(
            BatteryInfo.BATTERY_LEVEL_KEY to "not a float",
            BatteryInfo.LOW_POWER_MODE_KEY to "not a boolean"
        )

        // When
        val result = BatteryInfo.fromMap(map)

        // Then - should gracefully handle wrong types by returning null
        assertThat(result.batteryLevel).isNull()
        assertThat(result.lowPowerMode).isNull()
    }

    @Test
    fun `M handle mixed valid and invalid types W fromMap() { partially correct map }`() {
        // Given
        val map = mapOf(
            BatteryInfo.BATTERY_LEVEL_KEY to 0.9f,
            BatteryInfo.LOW_POWER_MODE_KEY to "not a boolean"
        )

        // When
        val result = BatteryInfo.fromMap(map)

        // Then
        assertThat(result.batteryLevel).isEqualTo(0.9f)
        assertThat(result.lowPowerMode).isNull()
    }

    // endregion

    // region Round-trip Tests

    @Test
    fun `M preserve data integrity W toMap then fromMap { round-trip serialization }`() {
        // Given
        val originalBatteryInfo = BatteryInfo(
            batteryLevel = 0.67f,
            lowPowerMode = false
        )

        // When
        val map = originalBatteryInfo.toMap()
        val deserializedBatteryInfo = BatteryInfo.fromMap(map)

        // Then
        assertThat(deserializedBatteryInfo).isEqualTo(originalBatteryInfo)
    }

    @Test
    fun `M preserve nulls in round-trip W toMap then fromMap { null values }`() {
        // Given
        val originalBatteryInfo = BatteryInfo(
            batteryLevel = null,
            lowPowerMode = null
        )

        // When
        val map = originalBatteryInfo.toMap()
        val deserializedBatteryInfo = BatteryInfo.fromMap(map)

        // Then
        assertThat(deserializedBatteryInfo).isEqualTo(originalBatteryInfo)
    }

    // endregion

    // region Key Constants Tests

    @Test
    fun `M have correct key constants W accessing keys { verify key values }`() {
        // Then
        assertThat(BatteryInfo.BATTERY_LEVEL_KEY).isEqualTo("battery_level")
        assertThat(BatteryInfo.LOW_POWER_MODE_KEY).isEqualTo("low_power_mode")
    }

    // endregion
}
