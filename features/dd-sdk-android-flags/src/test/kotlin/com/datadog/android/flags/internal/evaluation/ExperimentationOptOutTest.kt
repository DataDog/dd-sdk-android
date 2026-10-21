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
        "2026-10-21T10:00:00Z, junk, false"
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
            "2026-10-21T10:00:00.1+00:00"
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
            " 2026-10-21T10:00:00Z", "2026-10-21T10:00:00.Z"
        ]
    )
    fun `M reject unusable timestamps W isValidTimestamp()`(value: String?) {
        // When / Then
        assertThat(ExperimentationOptOut.isValidTimestamp(value)).isFalse()
    }
}
