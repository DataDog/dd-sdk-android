/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.selection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class FlagSelectionIgnoredReasonTest {

    @ParameterizedTest
    @ValueSource(
        strings = ["malformed_selection", "too_many_values", "invalid_key_pattern", "invalid_tag", "tag_load_failed"]
    )
    fun `M keep the reason W sanitize() { known reason }`(reason: String) {
        // When / Then
        assertThat(FlagSelectionIgnoredReason.sanitize(reason)).isEqualTo(reason)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "Some Future-Reason",
            "unknown_reason",
            "Too_Many_Values",
            "TAG_LOAD_FAILED",
            " too_many_values",
            "too_many_values\u0001",
            "invalid_tag\n",
            "too_many_values\u00E9",
            "\uD83D\uDE00"
        ]
    )
    fun `M return the fallback W sanitize() { unknown reason }`(reason: String) {
        // When / Then
        assertThat(FlagSelectionIgnoredReason.sanitize(reason)).isEqualTo("unknown")
    }

    @ParameterizedTest
    @ValueSource(ints = [33, 200, 10_000])
    fun `M return the fallback W sanitize() { value longer than 32 characters }`(length: Int) {
        // Given
        val reason = "too_many_values".padEnd(length, '_')

        // When / Then
        assertThat(FlagSelectionIgnoredReason.sanitize(reason)).isEqualTo("unknown")
    }
}
