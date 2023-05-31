/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.Context
import android.view.ViewGroup
import android.widget.DropDownListView
import androidx.appcompat.widget.AppCompatSpinner
import com.google.android.material.datepicker.MaterialCalendarGridView
import com.google.android.material.timepicker.TimePickerView
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness

@Suppress("UNCHECKED_CAST")
@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DefaultOptionSelectorDetectorTest {
    lateinit var testedOptionSelectorDetector: DefaultOptionSelectorDetector

    @Mock
    lateinit var mockViewGroup: ViewGroup

    @BeforeEach
    fun `set up`() {
        testedOptionSelectorDetector = DefaultOptionSelectorDetector()
    }

    @Test
    fun `M return false W isOptionSelector { view is not option selector }`() {
        assertThat(testedOptionSelectorDetector.isOptionSelector(mockViewGroup)).isEqualTo(false)
    }

    @Test
    fun `M return false W isOptionSelector { view is inner class }`() {
        // Given
        val innerClassViewGroup = TestInnerClassViewGroup(mock())

        // Then
        assertThat(testedOptionSelectorDetector.isOptionSelector(innerClassViewGroup))
            .isEqualTo(false)
    }

    @ParameterizedTest
    @MethodSource("optionSelectorTestInstances")
    fun `M return true W isOptionSelector { view is option selector type}`(
        optionSelector: ViewGroup
    ) {
        assertThat(testedOptionSelectorDetector.isOptionSelector(optionSelector)).isEqualTo(true)
    }

    companion object {
        @Suppress("unused")
        @JvmStatic
        fun optionSelectorTestInstances(): List<ViewGroup> {
            val mockContext: Context = mock()
            return listOf(
                // we need to provide those classes from fake packages as real implementations
                // because they are not visible for mock.
                DropDownListView(mockContext),
                androidx.appcompat.widget.DropDownListView(mockContext),
                TimePickerView(mockContext),
                MaterialCalendarGridView(mockContext),
                mock<AppCompatSpinner>()
            )
        }
    }

    inner class TestInnerClassViewGroup(context: Context) : ViewGroup(context) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            TODO("Not yet implemented")
        }
    }
}
