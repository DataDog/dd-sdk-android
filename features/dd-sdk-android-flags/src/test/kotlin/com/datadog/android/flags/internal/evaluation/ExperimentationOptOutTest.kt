/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.evaluation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

internal class ExperimentationOptOutTest {
    @ParameterizedTest
    @CsvSource(
        "2026-10-21T10:00:00.123456789Z, 2026-10-21T14:30:00.123456789+04:30, true",
        "2026-10-21T10:00:00.123456789Z, 2026-10-21T10:00:00.123456788Z, false",
        "2026-10-21T10:00:00.123456789123Z, 2026-10-21T10:00:00.123456789Z, true",
        "2026-10-21T10:00:00.100Z, 2026-10-21T10:00:00.1Z, true",
        "2026-10-21T10:00:00Z, 2026-10-21T10:00:00.000000000Z, true",
        "2026-10-21T10:00:00Z, 2026-10-21T10:00:00.000000001Z, false",
        "2026-10-21T10:00:00Z, junk, false",
        "0000-01-01T00:00:00Z, 0000-01-01T01:00:00+01:00, true",
        "1582-10-10T00:00:00Z, 1582-10-09T23:00:00-01:00, true",
        "2026-01-01T00:00:00+23:59, 2025-12-31T00:01:00Z, true",
        "1500-02-28T00:00:00Z, 1500-02-29T00:00:00Z, false"
    )
    fun `M compare instants at nanosecond precision W timestampsMatch()`(
        expected: String,
        actual: String,
        matches: Boolean
    ) {
        // When / Then
        assertThat(ExperimentationOptOut.timestampsMatch(expected, actual)).isEqualTo(matches)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "2026-10-21T10:00:00Z",
            "2026-10-21t10:00:00z",
            "2099-01-01T00:00:00.123456789+04:30",
            "2024-02-29T23:59:59-05:00",
            "2026-10-21T10:00:00.1+00:00",
            "0000-01-01T00:00:00Z",
            "0000-02-29T00:00:00Z",
            "1582-10-10T00:00:00Z",
            "9999-12-31T23:59:59.999999999Z"
        ]
    )
    fun `M accept RFC3339 timestamps W isValidTimestamp()`(value: String) {
        // When / Then
        assertThat(ExperimentationOptOut.isValidTimestamp(value)).isTrue()
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(
        strings = [
            "", "true", "123", "today", "2026-10-21", "2026-10-21T10:00:00",
            "2026-10-21T24:00:00Z", "2026-10-21T10:60:00Z", "2026-02-29T10:00:00Z",
            "2026-10-21T10:00:00+24:00", "2026-10-21T10:00:00+00:60", "2026-10-21T10:00:00Zjunk",
            " 2026-10-21T10:00:00Z", "2026-10-21T10:00:00.Z",
            "1500-02-29T00:00:00Z", "0000-01-01T00:00:00+00:01", "9999-12-31T23:59:59-00:01",
            "9999-12-31T23:00:00-01:00",
            "2016-12-31T23:59:60Z", "2026-10-21T10:00:00Z\n"
        ]
    )
    fun `M reject unusable timestamps W isValidTimestamp()`(value: String?) {
        // When / Then
        assertThat(ExperimentationOptOut.isValidTimestamp(value)).isFalse()
    }
}
