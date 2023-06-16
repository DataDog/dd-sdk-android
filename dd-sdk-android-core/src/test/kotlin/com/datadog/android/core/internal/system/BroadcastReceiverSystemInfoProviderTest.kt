/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.datadog.android.utils.assertj.SystemInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(value = Configurator::class)
internal class BroadcastReceiverSystemInfoProviderTest {

    lateinit var testedProvider: BroadcastReceiverSystemInfoProvider

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockIntent: Intent

    @Mock
    lateinit var mockPowerMgr: PowerManager

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @IntForgery
    var fakePluggedStatus: Int = 0

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(Context.POWER_SERVICE)) doReturn mockPowerMgr
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.BASE

        testedProvider =
            BroadcastReceiverSystemInfoProvider(mockBuildSdkVersionProvider, mockInternalLogger)
    }

    @Test
    fun `M ignore W unregister() {register not called}`() {
        // When
        testedProvider.unregister(mockContext)

        // Then
        verifyNoInteractions(mockContext)
    }

    @Test
    fun `M unregister only once W unregister()+unregister()`() {
        // Given
        val countDownLatch = CountDownLatch(2)
        testedProvider.register(mockContext)

        // When
        Thread {
            testedProvider.unregister(mockContext)
            countDownLatch.countDown()
        }.start()
        Thread {
            testedProvider.unregister(mockContext)
            countDownLatch.countDown()
        }.start()

        // Then
        countDownLatch.await(3, TimeUnit.SECONDS)
        verify(mockContext).unregisterReceiver(testedProvider)
    }

    @Test
    fun `M return unknown W getLatestSystemInfo() {not registered}`() {
        // When
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryFullOrCharging(false)
            .hasPowerSaveMode(false)
            .hasOnExternalPowerSource(false)
    }

    @RepeatedTest(10)
    fun `M read system info W register() {Lollipop}`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int,
        @BoolForgery powerSaveMode: Boolean
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

        val batteryIntent: Intent = mock()
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_PLUGGED), any()))
            .doReturn(fakePluggedStatus)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any()))
            .doReturn(scaledLevel)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(batteryIntent.getBooleanExtra(eq(BatteryManager.EXTRA_PRESENT), any()))
            .doReturn(true)
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED
        val powerSaveModeIntent: Intent = mock()
        whenever(mockPowerMgr.isPowerSaveMode) doReturn powerSaveMode
        whenever(powerSaveModeIntent.action) doReturn PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
        doReturn(batteryIntent, powerSaveModeIntent)
            .whenever(mockContext).registerReceiver(same(testedProvider), any())

        // When
        testedProvider.register(mockContext)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasPowerSaveMode(powerSaveMode)
            .hasOnExternalPowerSource(false)
    }

    @Test
    fun `M read system info W register() {KitKat}`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int,
        @BoolForgery powerSaveMode: Boolean
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.KITKAT

        val batteryIntent: Intent = mock()
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_PLUGGED), any()))
            .doReturn(fakePluggedStatus)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any()))
            .doReturn(scaledLevel)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(batteryIntent.getBooleanExtra(eq(BatteryManager.EXTRA_PRESENT), any()))
            .doReturn(true)
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        val powerSaveModeIntent: Intent = mock()
        whenever(mockPowerMgr.isPowerSaveMode) doReturn powerSaveMode
        whenever(powerSaveModeIntent.action) doReturn PowerManager.ACTION_POWER_SAVE_MODE_CHANGED

        whenever(mockContext.registerReceiver(same(testedProvider), any()))
            .doReturn(batteryIntent, powerSaveModeIntent)

        // When
        testedProvider.register(mockContext)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasPowerSaveMode(false)
            .hasOnExternalPowerSource(false)
    }

    @Test
    fun `M update data W onReceive() {battery changed, null value}`() {
        // Given
        whenever(mockIntent.getIntExtra(any(), any())) doAnswer {
            it.arguments[1] as Int
        }
        whenever(mockIntent.getBooleanExtra(any(), any())) doAnswer {
            it.arguments[1] as Boolean
        }
        whenever(mockIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryFullOrCharging(false)
            .hasOnExternalPowerSource(false)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `M update data W onReceive() {battery changed, not null value}`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        // Given
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn scaledLevel
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(mockIntent.getBooleanExtra(eq(BatteryManager.EXTRA_PRESENT), any()))
            .doReturn(true)
        whenever(mockIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasOnExternalPowerSource(false)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `M update data W onReceive() {power save changed, Lollipop+}`(
        @BoolForgery powerSaveMode: Boolean
    ) {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

        whenever(mockPowerMgr.isPowerSaveMode) doReturn powerSaveMode
        whenever(mockIntent.action) doReturn PowerManager.ACTION_POWER_SAVE_MODE_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasPowerSaveMode(powerSaveMode)
    }

    @Test
    fun `M update data W onReceive() {power save changed, no PowerManager}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.LOLLIPOP

        whenever(mockContext.getSystemService(Context.POWER_SERVICE)) doReturn null
        whenever(mockIntent.action) doReturn PowerManager.ACTION_POWER_SAVE_MODE_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `M update data W onReceive() {power save changed, KitKat}`() {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.KITKAT
        whenever(mockIntent.action) doReturn PowerManager.ACTION_POWER_SAVE_MODE_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `M ignore W onReceive() {unknown action}`(
        @StringForgery action: String
    ) {
        // Given
        whenever(mockIntent.action) doReturn action

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryFullOrCharging(false)
            .hasPowerSaveMode(false)
            .hasOnExternalPowerSource(false)
    }

    @Test
    fun `M ignore W onReceive() {null intent}`() {
        // When
        testedProvider.onReceive(mockContext, null)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryFullOrCharging(false)
            .hasPowerSaveMode(false)
            .hasOnExternalPowerSource(false)
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            BatteryManager.BATTERY_STATUS_DISCHARGING,
            BatteryManager.BATTERY_STATUS_UNKNOWN,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING
        ]
    )
    fun `M set batteryFullOrCharging to false W onReceive { battery status not charging or full }`(
        status: Int,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        // Given
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        val batteryIntent: Intent = mock()
        whenever(
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
        ).thenReturn(status)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn
            scaledLevel
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, batteryIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo).hasBatteryFullOrCharging(false)
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            BatteryManager.BATTERY_STATUS_CHARGING,
            BatteryManager.BATTERY_STATUS_FULL
        ]
    )
    fun `M set batteryFullOrCharging to true W onReceive { battery status charging or full }`(
        status: Int,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        // Given
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        val batteryIntent: Intent = mock()
        whenever(
            batteryIntent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )
        ).thenReturn(status)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn
            scaledLevel
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, batteryIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo).hasBatteryFullOrCharging(true)
    }

    @ParameterizedTest
    @ValueSource(
        ints = [
            BatteryManager.BATTERY_PLUGGED_AC,
            BatteryManager.BATTERY_PLUGGED_WIRELESS,
            BatteryManager.BATTERY_PLUGGED_USB
        ]
    )
    fun `M set onExternalPowerSource to true W onReceive { on external power source }`(
        pluggedInStatus: Int,
        @IntForgery(
            min = 0,
            max = 100
        ) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        // Given
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        val batteryIntent: Intent = mock()
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn
            scaledLevel
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_PLUGGED), any()))
            .doReturn(pluggedInStatus)
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, batteryIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo).hasOnExternalPowerSource(true)
    }

    @Test
    fun `M set onExternalPowerSource to false W onReceive { not on external power source }`(
        @IntForgery(
            min = 0,
            max = 100
        ) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        // Given
        val scaledLevel = ((level * scale) / 100f).roundToInt()
        val batteryIntent: Intent = mock()
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn
            scaledLevel
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_PLUGGED), any()))
            .doReturn(0)
        whenever(batteryIntent.getBooleanExtra(eq(BatteryManager.EXTRA_PRESENT), any()))
            .doReturn(true)
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, batteryIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo).hasOnExternalPowerSource(false)
    }

    @Test
    fun `M set onExternalPowerSource to true W onReceive { battery absent }`() {
        // Given
        val batteryIntent: Intent = mock()
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn 100
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn 0
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_PLUGGED), any())) doReturn 0
        whenever(batteryIntent.getBooleanExtra(eq(BatteryManager.EXTRA_PRESENT), any()))
            .doReturn(false)
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, batteryIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo).hasOnExternalPowerSource(true)
    }

    @Test
    fun `M set onExternalPowerSource to false W onReceive { battery present }`() {
        // Given
        val batteryIntent: Intent = mock()
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn 100
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn 0
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_PLUGGED), any())) doReturn 0
        whenever(batteryIntent.getBooleanExtra(eq(BatteryManager.EXTRA_PRESENT), any()))
            .doReturn(true)
        whenever(batteryIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, batteryIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo).hasOnExternalPowerSource(false)
    }

    // endregion

    // region Internal

    fun SystemInfo.BatteryStatus.androidStatus(): Int {
        return when (this) {
            SystemInfo.BatteryStatus.UNKNOWN -> BatteryManager.BATTERY_STATUS_UNKNOWN
            SystemInfo.BatteryStatus.CHARGING -> BatteryManager.BATTERY_STATUS_CHARGING
            SystemInfo.BatteryStatus.DISCHARGING -> BatteryManager.BATTERY_STATUS_DISCHARGING
            SystemInfo.BatteryStatus.NOT_CHARGING -> BatteryManager.BATTERY_STATUS_NOT_CHARGING
            SystemInfo.BatteryStatus.FULL -> BatteryManager.BATTERY_STATUS_FULL
        }
    }

    // endregion
}
