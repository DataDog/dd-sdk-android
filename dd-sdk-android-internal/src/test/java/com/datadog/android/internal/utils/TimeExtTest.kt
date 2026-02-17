/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.Calendar
import java.util.TimeZone

internal class TimeExtTest {

    @Test
    fun `M format unix epoch W formatIsoUtc(0)`() {
        // When
        val formatted = formatIsoUtc(0L)

        // Then
        assertThat(formatted).isEqualTo("1970-01-01T00:00:00.000Z")
    }

    @Test
    fun `M format given millis as ISO-8601 UTC W formatIsoUtc(millis)`() {
        // Given
        val utc = TimeZone.getTimeZone("UTC")
        val cal = Calendar.getInstance(utc).apply {
            set(Calendar.YEAR, 2023)
            set(Calendar.MONTH, Calendar.MAY)
            set(Calendar.DAY_OF_MONTH, 6)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 8)
            set(Calendar.SECOND, 9)
            set(Calendar.MILLISECOND, 0)
        }
        val millis = cal.timeInMillis

        // When
        val formatted = formatIsoUtc(millis)

        // Then
        assertThat(formatted).isEqualTo("2023-05-06T07:08:09.000Z")
    }

    @Test
    fun `M ignore default timezone W formatIsoUtc(millis)`() {
        // Given
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        try {
            val utc = TimeZone.getTimeZone("UTC")
            val cal = Calendar.getInstance(utc).apply {
                set(1984, Calendar.JANUARY, 24, 12, 34, 56)
                set(Calendar.MILLISECOND, 0)
            }
            val millis = cal.timeInMillis

            // When
            val formatted = formatIsoUtc(millis)

            // Then
            assertThat(formatted).isEqualTo("1984-01-24T12:34:56.000Z")
        } finally {
            TimeZone.setDefault(previous)
        }
    }
}
