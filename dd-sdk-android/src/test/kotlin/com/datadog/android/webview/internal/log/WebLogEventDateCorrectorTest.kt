/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import android.util.Log
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.log.internal.utils.ISO_8601
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.aFormattedTimestamp
import com.datadog.android.utils.mockSdkLogHandler
import com.datadog.android.utils.restoreSdkLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.lang.IllegalArgumentException
import java.text.Format
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class WebLogEventDateCorrectorTest {
    lateinit var testedWebLogEventDateCorrector: WebLogEventDateCorrector

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    lateinit var originalSdkLogHandler: LogHandler

    @Mock
    lateinit var mockSdkLogHandler: LogHandler

    @BeforeEach
    fun `set up`() {
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(mockTimeProvider)
        originalSdkLogHandler = mockSdkLogHandler(mockSdkLogHandler)
    }

    @AfterEach
    fun `tear down`() {
        restoreSdkLogHandler(originalSdkLogHandler)
    }

    @Test
    fun `M apply the time correction W correctDate`(forge: Forge) {
        // Given
        val simpleDateFormat = SimpleDateFormat(ISO_8601).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val calendar = GregorianCalendar.getInstance().apply {
            set(Calendar.YEAR, forge.anInt(min = 1, max = 4000))
            set(Calendar.MONTH, forge.anInt(min = 0, max = 11))
            set(Calendar.DAY_OF_MONTH, forge.anInt(min = 1, max = 28))
            set(Calendar.HOUR_OF_DAY, forge.anInt(min = 0, max = 23))
            set(Calendar.MINUTE, forge.anInt(min = 0, max = 59))
            set(Calendar.SECOND, forge.anInt(min = 0, max = 59))
            set(Calendar.MILLISECOND, forge.anInt(min = 0, max = 999))
        }
        val fakeTimeOffset = forge.aLong(
            min = -TimeUnit.DAYS.toMillis(365),
            max = TimeUnit.DAYS.toMillis(365)
        )
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(fakeTimeOffset)
        val formattedFakeDate = simpleDateFormat.format(calendar.time)

        // When
        val correctedDate = testedWebLogEventDateCorrector.correctDate(formattedFakeDate)

        // Then
        val expectedDate = simpleDateFormat.format(Date(calendar.timeInMillis + fakeTimeOffset))
        assertThat(correctedDate).isEqualTo(expectedDate)
    }

    @Test
    fun `M log an sdk error log W correctDate { bad date format }`(forge: Forge) {
        // Given
        val fakeBadFormatDate = forge.anAlphabeticalString()

        // When
        testedWebLogEventDateCorrector.correctDate(fakeBadFormatDate)

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                WebLogEventDateCorrector.DATE_PARSING_ERROR_MESSAGE.format(
                    Locale.US,
                    fakeBadFormatDate
                )
            ),
            argThat {
                this is ParseException
            },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    @Test
    fun `M return null W correctDate { bad date format }`(forge: Forge) {
        // Given
        val fakeBadFormatDate = forge.anAlphabeticalString()

        // When
        assertThat(testedWebLogEventDateCorrector.correctDate(fakeBadFormatDate)).isNull()
    }

    @Test
    fun `M log an sdk error log W correctDate { parse date throws NPE }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeNullPointerException = NullPointerException()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever(mockSimpleDateFormat.parse(fakeDate))
            .thenThrow(fakeNullPointerException)
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        testedWebLogEventDateCorrector.correctDate(fakeDate)

        // Then
        verify(mockSdkLogHandler).handleLog(
            eq(Log.ERROR),
            eq(
                WebLogEventDateCorrector.DATE_PARSING_ERROR_MESSAGE.format(
                    Locale.US,
                    fakeDate
                )
            ),
            argThat {
                this is NullPointerException
            },
            eq(emptyMap()),
            eq(emptySet()),
            eq(null)
        )
    }

    @Test
    fun `M return null W correctDate { parse date throws NPE }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeNullPointerException = NullPointerException()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever(mockSimpleDateFormat.parse(fakeDate))
            .thenThrow(fakeNullPointerException)
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        assertThat(testedWebLogEventDateCorrector.correctDate(fakeDate)).isNull()
    }

    @Test
    fun `M log an sdk error log W correctDate { format throws IAE }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeIllegalArgumentException = IllegalArgumentException()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever((mockSimpleDateFormat as Format).format(any()))
            .thenThrow(fakeIllegalArgumentException)
        whenever(mockSimpleDateFormat.parse(any())).thenReturn(Date())
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        testedWebLogEventDateCorrector.correctDate(fakeDate)

        // Then
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebLogEventDateCorrector.OFFSET_CORRECTION_ERROR_MESSAGE.format(Locale.US),
            fakeIllegalArgumentException
        )
    }

    @Test
    fun `M return null W correctDate { format throws IAE }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeIllegalArgumentException = IllegalArgumentException()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever((mockSimpleDateFormat as Format).format(any()))
            .thenThrow(fakeIllegalArgumentException)
        whenever(mockSimpleDateFormat.parse(any())).thenReturn(Date())
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        assertThat(testedWebLogEventDateCorrector.correctDate(fakeDate)).isNull()
    }

    @Test
    fun `M log an sdk error log W correctDate { format throws NPE }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeNullPointerException = NullPointerException()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever((mockSimpleDateFormat as Format).format(any()))
            .thenThrow(fakeNullPointerException)
        whenever(mockSimpleDateFormat.parse(any())).thenReturn(Date())
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        testedWebLogEventDateCorrector.correctDate(fakeDate)

        // Then
        verify(mockSdkLogHandler).handleLog(
            Log.ERROR,
            WebLogEventDateCorrector.OFFSET_CORRECTION_ERROR_MESSAGE.format(Locale.US),
            fakeNullPointerException
        )
    }

    @Test
    fun `M return null W correctDate { format throws NPE }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeIllegalArgumentException = NullPointerException()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever((mockSimpleDateFormat as Format).format(any()))
            .thenThrow(fakeIllegalArgumentException)
        whenever(mockSimpleDateFormat.parse(any())).thenReturn(Date())
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        assertThat(testedWebLogEventDateCorrector.correctDate(fakeDate)).isNull()
    }

    @Test
    fun `M return null W correctDate { parsing date returns null }`(forge: Forge) {
        // Given
        val mockSimpleDateFormat: SimpleDateFormat = mock()
        val fakeDate = forge.aFormattedTimestamp()
        whenever(mockTimeProvider.getServerOffsetMillis()).thenReturn(forge.aLong())
        whenever(mockSimpleDateFormat.parse(any())).thenReturn(null)
        testedWebLogEventDateCorrector = WebLogEventDateCorrector(
            mockTimeProvider,
            mockSimpleDateFormat
        )

        // When
        assertThat(testedWebLogEventDateCorrector.correctDate(fakeDate)).isNull()
    }
}
