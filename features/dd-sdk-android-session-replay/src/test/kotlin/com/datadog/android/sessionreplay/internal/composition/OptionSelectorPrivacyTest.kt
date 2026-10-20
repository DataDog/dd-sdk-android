/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("TooManyFunctions") // Compose decomposition and native-view traversal are split by concern.

package com.datadog.android.sessionreplay.internal.composition

import android.view.ViewGroup
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class OptionSelectorPrivacyTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M mask inputs W isOptionSelectorSafely() { detector throws }`(fakeLinkageError: Boolean) {
        // Given
        val mockDetector: OptionSelectorDetector = mock()
        val stubView: ViewGroup = mock()
        whenever(mockDetector.isOptionSelector(stubView)).thenAnswer {
            if (fakeLinkageError) throw NoClassDefFoundError("Extension")
            error("Extension")
        }

        // When
        val result = mockDetector.isOptionSelectorSafely(stubView, mock())

        // Then
        assertThat(result).isTrue()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M preserve detection W isOptionSelectorSafely()`(fakeSelector: Boolean) {
        // Given
        val mockDetector: OptionSelectorDetector = mock()
        val stubView: ViewGroup = mock()
        whenever(mockDetector.isOptionSelector(stubView)).thenReturn(fakeSelector)

        // When
        val result = mockDetector.isOptionSelectorSafely(stubView, mock())

        // Then
        assertThat(result).isEqualTo(fakeSelector)
    }
}
