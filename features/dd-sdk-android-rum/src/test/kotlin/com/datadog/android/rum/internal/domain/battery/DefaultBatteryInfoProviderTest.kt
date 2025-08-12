/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.battery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import android.os.Build
import android.os.PowerManager
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
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
    }

    // region getBatteryState

    @Test
    fun `M return complete battery info W getBatteryState() { all services available }`(
        @BoolForgery fakeLowPowerMode: Boolean,
        @IntForgery(0, 100) fakeBatteryLevel: Int
    ) {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn fakeLowPowerMode
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn fakeBatteryLevel

        // Create provider (initialization happens in constructor)
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )

        // When
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())
        val batteryPct = fakeBatteryLevel / 100f
        val expectedBatteryPct = (batteryPct * 10f).roundToInt() / 10f

        // Then
        assertThat(batteryInfo.lowPowerMode).isEqualTo(fakeLowPowerMode)
        assertThat(batteryInfo.batteryLevel).isEqualTo(expectedBatteryPct)
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
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())

        // Then
        assertThat(batteryInfo).isNotNull
        assertThat(batteryInfo.lowPowerMode).isNull()
        assertThat(batteryInfo.batteryLevel).isNull()
    }

    // endregion

    // region BroadcastReceiver Tests

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Test
    fun `M register broadcast receiver W constructor { initialization }`() {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50

        // When - provider is created (initialization happens in constructor)
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )

        // Then
        val receiverCaptor = argumentCaptor<BroadcastReceiver>()
        val filterCaptor = argumentCaptor<IntentFilter>()
        verify(mockApplicationContext).registerReceiver(receiverCaptor.capture(), filterCaptor.capture())
    }

    // endregion

    // region Cleanup Tests

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Test
    fun `M unregister receiver W cleanup() { after initialization }`() {
        // Given - create provider (initialization happens in constructor)
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )

        // When
        testedProvider.cleanup()

        // Then
        val receiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockApplicationContext).registerReceiver(receiverCaptor.capture(), any())
        verify(mockApplicationContext).unregisterReceiver(receiverCaptor.firstValue)
    }

    @Test
    fun `M retain initial state W cleanup() then getState() { no re-initialization }`() {
        // Given - create provider
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )

        // When - cleanup (no re-initialization happens)
        testedProvider.cleanup()
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())

        // Then - should retain the initial state from constructor
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
        assertThat(batteryInfo.batteryLevel).isEqualTo(0.5f)
    }

    // endregion

    // region Polling Tests

    @Test
    fun `M respect polling interval W getState() { multiple rapid calls }`() {
        // Given
        val shortInterval = 500 // 500ms for testing
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50

        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager,
            batteryLevelPollInterval = shortInterval
        )

        // When - rapid calls within polling interval
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 75
        testedProvider.getState()

        // Then - should still have initial battery level (50) from constructor, not 75
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())
        assertThat(batteryInfo.batteryLevel).isEqualTo(0.5f) // Still 50, not 75
    }

    @Test
    fun `M update battery level W getState() { after polling interval }`() {
        // Given
        val shortInterval = 50 // 50ms for testing
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50

        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager,
            batteryLevelPollInterval = shortInterval
        )

        // When
        Thread.sleep(shortInterval + 10L)
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 75
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())

        assertThat(batteryInfo.batteryLevel).isEqualTo(0.8f) // Now 75
    }

    // endregion

    // region Error Handling Tests

    @Test
    @TestTargetApi(Build.VERSION_CODES.Q)
    fun `M return null battery level W getBatteryLevel() { Integer MIN_VALUE }`() {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn Integer.MIN_VALUE

        // Create provider (initialization happens in constructor)
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )

        // When
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())

        // Then
        assertThat(batteryInfo.batteryLevel).isNull()
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.P)
    fun `M return null battery level W getBatteryLevel() { zero - on old api }`() {
        // Given
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 0

        // Create provider (initialization happens in constructor)
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            powerManager = mockPowerManager,
            batteryManager = mockBatteryManager
        )

        // When
        val batteryInfo = BatteryInfo.fromMap(testedProvider.getState())

        // Then
        assertThat(batteryInfo.batteryLevel).isNull()
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
    }

    // endregion
}
