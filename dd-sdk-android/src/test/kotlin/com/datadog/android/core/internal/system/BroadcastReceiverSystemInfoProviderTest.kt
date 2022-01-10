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
import com.datadog.android.log.assertj.SystemInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
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

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(Context.POWER_SERVICE)) doReturn mockPowerMgr
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.BASE

        testedProvider = BroadcastReceiverSystemInfoProvider(mockBuildSdkVersionProvider)
    }

    @Test
    fun `M ignore W unregister() {register not called}`() {
        // When
        testedProvider.unregister(mockContext)

        // Then
        verifyZeroInteractions(mockContext)
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
            .hasBatteryStatus(SystemInfo.BatteryStatus.UNKNOWN)
            .hasPowerSaveMode(false)
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
        val scaledLevel = (level * scale) / 100
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any()))
            .doReturn(scaledLevel)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
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
            .hasBatteryStatus(status)
            .hasPowerSaveMode(powerSaveMode)
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
        val scaledLevel = (level * scale) / 100
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any()))
            .doReturn(scaledLevel)
        whenever(batteryIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
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
            .hasBatteryStatus(status)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `M update data W onReceive() {battery changed, null value}`() {
        // Given
        whenever(mockIntent.getIntExtra(any(), any())) doAnswer {
            it.arguments[1] as Int
        }
        whenever(mockIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryStatus(SystemInfo.BatteryStatus.UNKNOWN)
    }

    @Test
    fun `M update data W onReceive() {battery changed, not null value}`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        // Given
        val scaledLevel = (level * scale) / 100
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn scaledLevel
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(mockIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED

        // When
        testedProvider.onReceive(mockContext, mockIntent)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasBatteryStatus(status)
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
            .hasBatteryStatus(SystemInfo.BatteryStatus.UNKNOWN)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `M ignore W onReceive() {null intent}`() {
        // When
        testedProvider.onReceive(mockContext, null)
        val systemInfo = testedProvider.getLatestSystemInfo()

        // Then
        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryStatus(SystemInfo.BatteryStatus.UNKNOWN)
            .hasPowerSaveMode(false)
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
