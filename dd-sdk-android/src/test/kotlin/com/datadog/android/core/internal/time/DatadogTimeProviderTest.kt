/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.time

import android.content.Context
import android.content.SharedPreferences
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DatadogTimeProviderTest {

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var mockPreferences: SharedPreferences
    @Mock
    lateinit var mockPreferencesEditor: SharedPreferences.Editor

    lateinit var testedProvider: MutableTimeProvider

    @BeforeEach
    fun `set up`() {
        whenever(mockContext.applicationContext) doReturn mockContext
        whenever(
            mockContext.getSharedPreferences(
                DatadogTimeProvider.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
        ) doReturn mockPreferences
        whenever(mockPreferences.edit()) doReturn mockPreferencesEditor
        whenever(mockPreferencesEditor.putLong(any(), any())) doReturn mockPreferencesEditor

        testedProvider = DatadogTimeProvider(mockContext)
    }

    @Test
    fun `device timestamp is always synced with System`(
        forge: Forge
    ) {
        val timestamps = mutableListOf<Long>()

        val start = System.currentTimeMillis()
        for (i in 0..10) {
            timestamps.add(testedProvider.getDeviceTimestamp())
            testedProvider.updateOffset(forge.aLong())
        }
        val end = System.currentTimeMillis()

        timestamps.forEach {
            assertThat(it)
                .isBetween(start, end)
        }
    }

    @Test
    fun `server timestamp is same as device when offset is not yet known`() {

        val start = System.currentTimeMillis()
        val timestamp = testedProvider.getServerTimestamp()
        val end = System.currentTimeMillis()

        assertThat(timestamp)
            .isBetween(start, end)
    }

    @Test
    fun `server timestamp is offset when first offset is known`(
        @LongForgery(min = -ONE_YEAR, max = ONE_YEAR) offset: Long
    ) {
        testedProvider.updateOffset(offset)

        val start = System.currentTimeMillis()
        val timestamp = testedProvider.getServerTimestamp()
        val end = System.currentTimeMillis()

        assertThat(timestamp)
            .isBetween(start + offset, end + offset)
    }

    @Test
    fun `server timestamp is offset with many offset samples`(
        @LongForgery(min = ONE_DAY, max = ONE_MONTH) averageOffset: Long,
        @LongForgery(min = ONE_SECOND, max = HALF_MINUTE) deviation: Long,
        @IntForgery(min = 10, max = 100) count: Int,
        forge: Forge
    ) {
        for (i in 0 until count) {
            val offset = forge.aGaussianLong(averageOffset, deviation)
            testedProvider.updateOffset(offset)
        }
        testedProvider.updateOffset(averageOffset + HALF_MINUTE)
        val start = System.currentTimeMillis()

        val timestamp = testedProvider.getServerTimestamp()
        val end = System.currentTimeMillis()

        assertThat(timestamp)
            .isBetween(
                start + averageOffset - deviation,
                end + averageOffset + deviation
            )
    }

    @Test
    fun `restart offset sampling if deviation is too high`(
        @LongForgery(min = ONE_DAY, max = ONE_MONTH) averageOffset: Long,
        @LongForgery(min = ONE_SECOND, max = HALF_MINUTE) deviation: Long,
        @IntForgery(min = 10, max = 100) count: Int,
        forge: Forge
    ) {
        for (i in 0 until count) {
            val offset = forge.aGaussianLong(averageOffset, deviation)
            testedProvider.updateOffset(offset)
        }

        testedProvider.updateOffset(averageOffset + ONE_MONTH)
        val start = System.currentTimeMillis()

        val timestamp = testedProvider.getServerTimestamp()
        val end = System.currentTimeMillis()

        assertThat(timestamp)
            .isBetween(
                start + averageOffset + ONE_MONTH,
                end + averageOffset + ONE_MONTH
            )
    }

    @Test
    fun `persist offset on first update`(
        @LongForgery(min = -ONE_YEAR, max = ONE_YEAR) offset: Long
    ) {

        testedProvider.updateOffset(offset)

        verify(mockPreferencesEditor).putLong(
            DatadogTimeProvider.PREF_OFFSET_MS,
            offset
        )
    }

    @Test
    fun `persist offset with many offset samples`(
        @LongForgery(min = ONE_DAY, max = ONE_MONTH) averageOffset: Long,
        @LongForgery(min = ONE_SECOND, max = HALF_MINUTE) deviation: Long,
        @IntForgery(min = 10, max = 100) count: Int,
        forge: Forge
    ) {
        for (i in 0 until count) {
            val offset = forge.aGaussianLong(averageOffset, deviation)
            testedProvider.updateOffset(offset)
        }

        argumentCaptor<Long>().apply {
            verify(mockPreferencesEditor, times(count)).putLong(
                eq(DatadogTimeProvider.PREF_OFFSET_MS),
                capture()
            )
            assertThat(lastValue)
                .isCloseTo(averageOffset, Offset.offset(deviation))
        }
    }

    @Test
    fun `restore offset on init`(
        @LongForgery(min = ONE_MONTH, max = ONE_YEAR) offset: Long
    ) {
        whenever(mockPreferences.getLong(DatadogTimeProvider.PREF_OFFSET_MS, 0L)) doReturn offset
        val newProvider = DatadogTimeProvider(mockContext)

        val start = System.currentTimeMillis()
        val timestamp = newProvider.getServerTimestamp()
        val end = System.currentTimeMillis()

        assertThat(timestamp)
            .isBetween(start + offset, end + offset)
    }

    companion object {
        private const val ONE_SECOND = 1000L
        private const val HALF_MINUTE = 30 * ONE_SECOND
        private const val ONE_MINUTE = 60L * ONE_SECOND
        private const val ONE_HOUR = 60L * ONE_MINUTE
        private const val ONE_DAY = 24L * ONE_HOUR
        private const val ONE_MONTH = 30L * ONE_DAY
        private const val ONE_YEAR = 365L * ONE_DAY
    }
}
