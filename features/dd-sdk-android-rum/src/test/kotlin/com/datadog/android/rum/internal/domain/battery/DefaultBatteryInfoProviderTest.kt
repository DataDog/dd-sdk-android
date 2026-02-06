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
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY
import android.os.Build
import android.os.PowerManager
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
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
    lateinit var mockTimeProvider: TimeProvider

    @Mock
    lateinit var mockBatteryManager: BatteryManager

    @LongForgery(min = 0L)
    private var fakeStartTimeMs: Long = 0L

    private val shortPollingInterval = 200

    @BeforeEach
    fun setup() {
        whenever(mockApplicationContext.applicationInfo) doReturn ApplicationInfo().apply {
            targetSdkVersion = 0
        }
        whenever(mockApplicationContext.contentResolver) doReturn mockContentResolver
        whenever(mockTimeProvider.getDeviceElapsedRealtimeMillis()) doReturn fakeStartTimeMs
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50
        initializeBatteryManager()
    }

    // region getBatteryState

    @Test
    fun `M return complete battery info W getBatteryState() { all services available }`(
        @BoolForgery fakeLowPowerMode: Boolean,
        @IntForgery(0, 100) fakeBatteryLevel: Int,
        @IntForgery(min = Build.VERSION_CODES.P) fakeTargetSdk: Int
    ) {
        // Given
        // needed or battery level 0 causes flakiness with retrieval code
        whenever(mockApplicationContext.applicationInfo) doReturn ApplicationInfo().apply {
            targetSdkVersion = fakeTargetSdk
        }
        whenever(mockPowerManager.isPowerSaveMode) doReturn fakeLowPowerMode
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn fakeBatteryLevel
        initializeBatteryManager()

        // When
        val batteryInfo = testedProvider.getState()
        val expectedBatteryPct = fakeBatteryLevel / 100f

        // Then
        assertThat(batteryInfo.lowPowerMode).isEqualTo(fakeLowPowerMode)
        assertThat(batteryInfo.batteryLevel).isEqualTo(expectedBatteryPct)
    }

    @Test
    fun `M return battery info with nulls W getBatteryState() { services unavailable }`() {
        // Given
        initializeBatteryManager(null, null)

        // When
        val batteryInfo = testedProvider.getState()

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
        // When
        testedProvider.cleanup()

        // Then
        val receiverCaptor = argumentCaptor<BroadcastReceiver>()
        verify(mockApplicationContext).registerReceiver(receiverCaptor.capture(), any())
        verify(mockApplicationContext).unregisterReceiver(receiverCaptor.firstValue)
    }

    @Test
    fun `M retain initial state W cleanup() then getState() { no re-initialization }`() {
        // When - cleanup (no re-initialization happens)
        testedProvider.cleanup()
        val batteryInfo = testedProvider.getState()

        // Then - should retain the initial state from constructor
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
        assertThat(batteryInfo.batteryLevel).isEqualTo(0.5f)
    }

    // endregion

    // region Polling Tests

    @Test
    fun `M update battery level W getState() { after polling interval }`() {
        // Given
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 75
        assertThat(testedProvider.getState().batteryLevel).isEqualTo(0.75f)

        // When
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 50
        whenever(mockTimeProvider.getDeviceElapsedRealtimeMillis()) doReturn fakeStartTimeMs + shortPollingInterval

        // Then
        assertThat(testedProvider.getState().batteryLevel).isEqualTo(0.5f)
    }

    // endregion

    // region Error Handling Tests

    @Test
    fun `M return null battery level W getBatteryLevel() { Integer MIN_VALUE }`(
        @IntForgery(min = Build.VERSION_CODES.P) fakeTargetSdk: Int
    ) {
        // Given
        whenever(mockApplicationContext.applicationInfo) doReturn ApplicationInfo().apply {
            targetSdkVersion = fakeTargetSdk
        }
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn Integer.MIN_VALUE
        initializeBatteryManager()

        // When
        val batteryInfo = testedProvider.getState()

        // Then
        assertThat(batteryInfo.batteryLevel).isNull()
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
    }

    @Test
    fun `M return null battery level W getBatteryLevel() { zero - on old api }`(
        @IntForgery(max = Build.VERSION_CODES.P) fakeTargetSdk: Int
    ) {
        // Given
        whenever(mockApplicationContext.applicationInfo) doReturn ApplicationInfo().apply {
            targetSdkVersion = fakeTargetSdk
        }
        whenever(mockPowerManager.isPowerSaveMode) doReturn false
        whenever(mockBatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)) doReturn 0
        initializeBatteryManager()

        // When
        val batteryInfo = testedProvider.getState()

        // Then
        assertThat(batteryInfo.batteryLevel).isNull()
        assertThat(batteryInfo.lowPowerMode).isEqualTo(false)
    }

    // endregion

    private fun initializeBatteryManager(
        powerManager: PowerManager? = mockPowerManager,
        batteryManager: BatteryManager? = mockBatteryManager
    ) {
        testedProvider = DefaultBatteryInfoProvider(
            applicationContext = mockApplicationContext,
            timeProvider = mockTimeProvider,
            batteryLevelPollInterval = shortPollingInterval,
            powerManager = powerManager,
            batteryManager = batteryManager
        )
    }
}
