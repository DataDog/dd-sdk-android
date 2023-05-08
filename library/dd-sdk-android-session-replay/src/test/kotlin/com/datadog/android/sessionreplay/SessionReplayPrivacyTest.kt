/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.os.Build
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.EditTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllCheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllCheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllNumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllRadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllSeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllSwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskInputTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewScreenshotWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.mock
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runners.Parameterized
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.provider.Arguments
import java.util.stream.Stream


internal class SessionReplayPrivacyTest {

    @ParameterizedTest(name = "M return mappers W rule({1}, API Level {0}")
    @MethodSource("provideTestArguments")
    fun `M return mappers W rule({1}, API Level {0}`(
            apiLevel: Int,
            maskLevel: String,
            expectedMappers: List<MapperTypeWrapper>
    ) {
        // Given
        Build.VERSION::class.java.setStaticValue("SDK_INT", apiLevel)

        // When
        val actualMappers = when(maskLevel) {
            SessionReplayPrivacy.ALLOW_ALL.toString() -> SessionReplayPrivacy.ALLOW_ALL.mappers()
            SessionReplayPrivacy.MASK_ALL.toString() -> SessionReplayPrivacy.MASK_ALL.mappers()
            SessionReplayPrivacy.MASK_USER_INPUT.toString() -> SessionReplayPrivacy.MASK_USER_INPUT.mappers()
            else -> throw IllegalArgumentException("Unknown masking level")
        }

        // Then
        assertThat(actualMappers.size).isEqualTo(expectedMappers.size)

        for ((type, mapper) in actualMappers) {
            val expectedMapper = expectedMappers.find { it.type == type }?.mapper
            assertThat(mapper.javaClass.name).isEqualTo(expectedMapper?.javaClass?.name)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun WireframeMapper<*, *>.toGenericMapper(): WireframeMapper<View, *> {
            return this as WireframeMapper<View, *>
        }

        // BASE
        private val mockButtonMapper: ButtonMapper = mock()
        private val mockEditTextViewMapper: EditTextViewMapper = mock()
        private val mockImageMapper: ViewScreenshotWireframeMapper = mock()

        private val baseMappers = listOf(
                MapperTypeWrapper(Button::class.java, mockButtonMapper.toGenericMapper()),
                MapperTypeWrapper(EditText::class.java, mockEditTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(ImageView::class.java, mockImageMapper.toGenericMapper()),
        )


        // ALLOW_ALL
        private val mockTextViewMapper: TextViewMapper = mock()
        private val mockSwitchCompatMapper: SwitchCompatMapper = mock()
        private val mockCheckedTextViewMapper: CheckedTextViewMapper = mock()
        private val mockCheckBoxMapper: CheckBoxMapper = mock()
        private val mockRadioButtonMapper: RadioButtonMapper = mock()
        private val mockSeekBarMapper: SeekBarWireframeMapper = mock()
        private val mockNumberPickerMapper: NumberPickerMapper = mock()

        private val allowAll = baseMappers + listOf(
                MapperTypeWrapper(TextView::class.java, mockTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(CheckedTextView::class.java, mockCheckedTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(CheckBox::class.java, mockCheckBoxMapper.toGenericMapper()),
                MapperTypeWrapper(RadioButton::class.java, mockRadioButtonMapper.toGenericMapper()),
                MapperTypeWrapper(SwitchCompat::class.java, mockSwitchCompatMapper.toGenericMapper()),
        )

        private val allowAllAbove26 = allowAll + listOf(
                MapperTypeWrapper(SeekBar::class.java, mockSeekBarMapper.toGenericMapper()),
        )

        private val allowAllAbove29 = allowAllAbove26 + listOf(
                MapperTypeWrapper(NumberPicker::class.java, mockNumberPickerMapper.toGenericMapper()),
        )

        // MASK_ALL
        private val mockMaskAllTextViewMapper: MaskAllTextViewMapper = mock()
        private val mockMaskAllCheckedTextViewMapper: MaskAllCheckedTextViewMapper = mock()
        private val mockMaskAllCheckBoxMapper: MaskAllCheckBoxMapper = mock()
        private val mockMaskAllRadioButtonMapper: MaskAllRadioButtonMapper = mock()
        private val mockMaskAllSwitchCompatMapper: MaskAllSwitchCompatMapper = mock()
        private val mockMaskAllSeekBarWireframeMapper: MaskAllSeekBarWireframeMapper = mock()
        private val mockMaskAllNumberPickerMapper: MaskAllNumberPickerMapper = mock()

        private val maskAll = baseMappers + listOf(
                MapperTypeWrapper(TextView::class.java, mockMaskAllTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(CheckedTextView::class.java, mockMaskAllCheckedTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(CheckBox::class.java, mockMaskAllCheckBoxMapper.toGenericMapper()),
                MapperTypeWrapper(RadioButton::class.java, mockMaskAllRadioButtonMapper.toGenericMapper()),
                MapperTypeWrapper(SwitchCompat::class.java, mockMaskAllSwitchCompatMapper.toGenericMapper())
        )

        private val maskAllAbove26 = maskAll + listOf(
                MapperTypeWrapper(SeekBar::class.java, mockMaskAllSeekBarWireframeMapper.toGenericMapper()),
        )

        private val maskAllAbove29 = maskAllAbove26 + listOf(
                MapperTypeWrapper(NumberPicker::class.java, mockMaskAllNumberPickerMapper.toGenericMapper()),
        )


        // MASK_USER_INPUT
        private val mockMaskInputTextViewMapper: MaskInputTextViewMapper = mock()

        private val maskUserInput = baseMappers + listOf(
                MapperTypeWrapper(TextView::class.java, mockMaskInputTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(CheckedTextView::class.java, mockMaskAllCheckedTextViewMapper.toGenericMapper()),
                MapperTypeWrapper(CheckBox::class.java, mockMaskAllCheckBoxMapper.toGenericMapper()),
                MapperTypeWrapper(RadioButton::class.java, mockMaskAllRadioButtonMapper.toGenericMapper()),
                MapperTypeWrapper(SwitchCompat::class.java, mockMaskAllSwitchCompatMapper.toGenericMapper())
        )

        private val maskUserInputAbove26 = maskUserInput + listOf(
                MapperTypeWrapper(SeekBar::class.java, mockMaskAllSeekBarWireframeMapper.toGenericMapper()),
        )

        private val maskUserInputAbove29 = maskUserInputAbove26 + listOf(
                MapperTypeWrapper(NumberPicker::class.java, mockMaskAllNumberPickerMapper.toGenericMapper()),
        )

        @JvmStatic
        @Parameterized.Parameters
        fun provideTestArguments(): Stream<Arguments> {
            val allowAllLevel = SessionReplayPrivacy.ALLOW_ALL.toString()
            val maskAllLevel = SessionReplayPrivacy.MASK_ALL.toString()
            val maskUserInputLevel = SessionReplayPrivacy.MASK_USER_INPUT.toString()

            return Stream.of(
                    Arguments.of(Build.VERSION_CODES.M, allowAllLevel, allowAll),
                    Arguments.of(Build.VERSION_CODES.O, allowAllLevel, allowAllAbove26),
                    Arguments.of(Build.VERSION_CODES.Q, allowAllLevel, allowAllAbove29),
                    Arguments.of(Build.VERSION_CODES.M, maskAllLevel, maskAll),
                    Arguments.of(Build.VERSION_CODES.O, maskAllLevel, maskAllAbove26),
                    Arguments.of(Build.VERSION_CODES.Q, maskAllLevel, maskAllAbove29),
                    Arguments.of(Build.VERSION_CODES.M, maskUserInputLevel, maskUserInput),
                    Arguments.of(Build.VERSION_CODES.O, maskUserInputLevel, maskUserInputAbove26),
                    Arguments.of(Build.VERSION_CODES.Q, maskUserInputLevel, maskUserInputAbove29),
            )
        }
    }
}
