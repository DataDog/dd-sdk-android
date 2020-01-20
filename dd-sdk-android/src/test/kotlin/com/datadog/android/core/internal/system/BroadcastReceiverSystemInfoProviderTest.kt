/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.system

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.datadog.android.log.assertj.SystemInfoAssert.Companion.assertThat
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.same
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class BroadcastReceiverSystemInfoProviderTest {

    lateinit var testedProvider: BroadcastReceiverSystemInfoProvider

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var mockIntent: Intent
    @Mock
    lateinit var mockPowerMgr: PowerManager

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.getSystemService(Context.POWER_SERVICE)) doReturn mockPowerMgr

        testedProvider = BroadcastReceiverSystemInfoProvider()
    }

    @Test
    fun `initial state is unknown`() {
        val systemInfo = testedProvider.getLatestSystemInfo()

        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryStatus(SystemInfo.BatteryStatus.UNKNOWN)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `read system info on register Lollipop`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int,
        @BoolForgery powerSaveMode: Boolean
    ) {
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

        testedProvider.register(mockContext)
        val systemInfo = testedProvider.getLatestSystemInfo()

        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasBatteryStatus(status)
            .hasPowerSaveMode(powerSaveMode)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.KITKAT)
    fun `read system info on register KitKat`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int,
        @BoolForgery powerSaveMode: Boolean
    ) {
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

        testedProvider.register(mockContext)
        val systemInfo = testedProvider.getLatestSystemInfo()

        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasBatteryStatus(status)
            .hasPowerSaveMode(false)
    }

    @Test
    fun `battery changed (null)`() {
        whenever(mockIntent.getIntExtra(any(), any())) doAnswer {
            it.arguments[1] as Int
        }
        whenever(mockIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED
        testedProvider.onReceive(mockContext, mockIntent)

        val systemInfo = testedProvider.getLatestSystemInfo()

        assertThat(systemInfo)
            .hasBatteryLevel(-1)
            .hasBatteryStatus(SystemInfo.BatteryStatus.UNKNOWN)
    }

    @Test
    fun `battery changed (not null)`(
        @Forgery status: SystemInfo.BatteryStatus,
        @IntForgery(min = 0, max = 100) level: Int,
        @IntForgery(min = 50, max = 10000) scale: Int
    ) {
        val scaledLevel = (level * scale) / 100
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_STATUS), any()))
            .doReturn(status.androidStatus())
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_LEVEL), any())) doReturn scaledLevel
        whenever(mockIntent.getIntExtra(eq(BatteryManager.EXTRA_SCALE), any())) doReturn scale
        whenever(mockIntent.action) doReturn Intent.ACTION_BATTERY_CHANGED
        testedProvider.onReceive(mockContext, mockIntent)

        val systemInfo = testedProvider.getLatestSystemInfo()

        assertThat(systemInfo)
            .hasBatteryLevel(level, scale)
            .hasBatteryStatus(status)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `power save mode changed Lollipop`(
        @BoolForgery powerSaveMode: Boolean
    ) {
        whenever(mockPowerMgr.isPowerSaveMode) doReturn powerSaveMode
        whenever(mockIntent.action) doReturn PowerManager.ACTION_POWER_SAVE_MODE_CHANGED
        testedProvider.onReceive(mockContext, mockIntent)

        val systemInfo = testedProvider.getLatestSystemInfo()

        assertThat(systemInfo)
            .hasPowerSaveMode(powerSaveMode)
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
