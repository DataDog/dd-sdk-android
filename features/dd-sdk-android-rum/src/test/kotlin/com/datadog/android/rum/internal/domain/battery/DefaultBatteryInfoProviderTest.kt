/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import android.content.ContentResolver
import android.content.Context
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import android.os.PowerManager
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DefaultBatteryInfoProviderTest {
    private lateinit var testedProvider: DefaultBatteryInfoProvider

    @Mock
    lateinit var mockApplicationContext: Context

    @Mock
    lateinit var mockContentResolver: ContentResolver

    @Mock
    lateinit var mockPowerManager: PowerManager

    @Mock
    lateinit var mockBatteryManager: BatteryManager

    @BeforeEach
    fun setup() {
        whenever(mockApplicationContext.contentResolver) doReturn mockContentResolver

        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )
    }

    // region getBatteryState

    @Test
    fun `M return complete battery info W getBatteryState() { all services available }`() {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 85

        // When
        val batteryInfo = testedProvider.getBatteryState()

        // Then
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
        assertThat(batteryInfo.batteryLevel).isEqualTo(0.9f)
    }

    @Test
    fun `M return battery info with nulls W getBatteryState() { services unavailable }`() {
        // Given
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = null,
            batteryManager = null
        )

        // When
        val batteryInfo = testedProvider.getBatteryState()

        // Then
        assertThat(batteryInfo).isNotNull
        assertThat(batteryInfo.lowPowerMode).isNull()
        assertThat(batteryInfo.batteryLevel).isNull()
    }

    // endregion

    // region Battery Level Tests

    @ParameterizedTest
    @ValueSource(ints = [0, 25, 50, 75, 100])
    fun `M return correct battery level W getBatteryState() { various battery percentages }`(batteryPercentage: Int) {
        // Given
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn batteryPercentage
        whenever(mockPowerManager.isPowerSaveMode) doReturn false

        // When
        val batteryInfo = testedProvider.getBatteryState()

        // Then
        val expectedLevel = ((batteryPercentage / 100f) * 10.0f).roundToInt() / 10.0f
        assertThat(batteryInfo.batteryLevel).isEqualTo(expectedLevel)
    }

    // endregion

    // region Power Save Mode Tests

    @Test
    fun `M return true W getBatteryState() { power save mode enabled }`() {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn true
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50

        // When
        val batteryInfo = testedProvider.getBatteryState()

        // Then
        assertThat(batteryInfo.lowPowerMode).isEqualTo(true)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `M handle low battery percentage W getBatteryState() { edge case calculations }`() {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn true
        // Test edge case with very low battery
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 1

        // When
        val batteryInfo = testedProvider.getBatteryState()

        // Then
        assertThat(batteryInfo).isNotNull
        assertThat(batteryInfo.lowPowerMode).isEqualTo(true)
        assertThat(batteryInfo.batteryLevel).isEqualTo(0.0f)
    }

    // endregion
}
