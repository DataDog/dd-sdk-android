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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toolbar
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.EditTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ImageButtonMapper
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
import com.datadog.android.sessionreplay.internal.recorder.mapper.UnsupportedViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewScreenshotWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.tools.unit.setStaticValue
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runners.Parameterized
import java.util.stream.Stream
import androidx.appcompat.widget.Toolbar as AppCompatToolbar

internal class SessionReplayPrivacyTest {

    /*
        At some point in future this may cause issues for us due to final static optimization
        After x times changing a static final value, the changes will cease having an effect
        see: https://stackoverflow.com/questions/3301635/change-private-static-final-field-using-java-reflection/3301720#3301720
     */
    private val origApiLevel = Build.VERSION.SDK_INT

    @AfterEach
    fun teardown() {
        setApiLevel(origApiLevel)
    }

    @ParameterizedTest(name = "M return mappers W rule({1}, API Level {0}")
    @MethodSource("provideTestArguments")
    fun `M return mappers W rule({1}, API Level {0}`(
        apiLevel: Int,
        maskLevel: String,
        expectedMappers: List<MapperTypeWrapper>
    ) {
        // Given
        setApiLevel(apiLevel)

        // When
        val actualMappers = when (maskLevel) {
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

    // region Internal

    private fun setApiLevel(newApiLevel: Int) {
        Build.VERSION::class.java.setStaticValue("SDK_INT", newApiLevel)
    }

    // endregion

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun WireframeMapper<*, *>.toGenericMapper(): WireframeMapper<View, *> {
            return this as WireframeMapper<View, *>
        }

        // BASE
        private val mockButtonMapper: ButtonMapper = mock()
        private val mockEditTextViewMapper: EditTextViewMapper = mock()
        private val mockImageMapper: ViewScreenshotWireframeMapper = mock()
        private val mockUnsupportedViewMapper: UnsupportedViewMapper = mock()
        private val mockImageButtonViewMapper: ImageButtonMapper = mock()

        private val baseMappers = listOf(
            MapperTypeWrapper(Button::class.java, mockButtonMapper.toGenericMapper()),
            MapperTypeWrapper(EditText::class.java, mockEditTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(ImageView::class.java, mockImageMapper.toGenericMapper()),
            MapperTypeWrapper(ImageButton::class.java, mockImageButtonViewMapper.toGenericMapper()),
            MapperTypeWrapper(AppCompatToolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
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
            MapperTypeWrapper(SwitchCompat::class.java, mockSwitchCompatMapper.toGenericMapper())
        )

        private val allowAllFromApi21 = allowAll + listOf(
            MapperTypeWrapper(Toolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
        )

        private val allowAllFromApi26 = allowAllFromApi21 + listOf(
            MapperTypeWrapper(SeekBar::class.java, mockSeekBarMapper.toGenericMapper())
        )

        private val allowAllFromApi29 = allowAllFromApi26 + listOf(
            MapperTypeWrapper(NumberPicker::class.java, mockNumberPickerMapper.toGenericMapper())
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

        private val maskAllFromApi21 = maskAll + listOf(
            MapperTypeWrapper(Toolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
        )

        private val maskAllFromApi26 = maskAllFromApi21 + listOf(
            MapperTypeWrapper(SeekBar::class.java, mockMaskAllSeekBarWireframeMapper.toGenericMapper())
        )

        private val maskAllFromApi29 = maskAllFromApi26 + listOf(
            MapperTypeWrapper(NumberPicker::class.java, mockMaskAllNumberPickerMapper.toGenericMapper())
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

        private val maskUserInputFromApi21 = maskUserInput + listOf(
            MapperTypeWrapper(Toolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
        )

        private val maskUserInputFromApi26 = maskUserInputFromApi21 + listOf(
            MapperTypeWrapper(SeekBar::class.java, mockMaskAllSeekBarWireframeMapper.toGenericMapper())
        )

        private val maskUserInputFromApi29 = maskUserInputFromApi26 + listOf(
            MapperTypeWrapper(NumberPicker::class.java, mockMaskAllNumberPickerMapper.toGenericMapper())
        )

        @JvmStatic
        @Parameterized.Parameters
        fun provideTestArguments(): Stream<Arguments> {
            val allowAllLevel = SessionReplayPrivacy.ALLOW_ALL.toString()
            val maskAllLevel = SessionReplayPrivacy.MASK_ALL.toString()
            val maskUserInputLevel = SessionReplayPrivacy.MASK_USER_INPUT.toString()

            return Stream.of(
                Arguments.of(Build.VERSION_CODES.KITKAT, allowAllLevel, allowAll),
                Arguments.of(Build.VERSION_CODES.LOLLIPOP, allowAllLevel, allowAllFromApi21),
                Arguments.of(Build.VERSION_CODES.O, allowAllLevel, allowAllFromApi26),
                Arguments.of(Build.VERSION_CODES.Q, allowAllLevel, allowAllFromApi29),
                Arguments.of(Build.VERSION_CODES.KITKAT, maskAllLevel, maskAll),
                Arguments.of(Build.VERSION_CODES.LOLLIPOP, maskAllLevel, maskAllFromApi21),
                Arguments.of(Build.VERSION_CODES.O, maskAllLevel, maskAllFromApi26),
                Arguments.of(Build.VERSION_CODES.Q, maskAllLevel, maskAllFromApi29),
                Arguments.of(Build.VERSION_CODES.KITKAT, maskUserInputLevel, maskUserInput),
                Arguments.of(Build.VERSION_CODES.LOLLIPOP, maskUserInputLevel, maskUserInputFromApi21),
                Arguments.of(Build.VERSION_CODES.O, maskUserInputLevel, maskUserInputFromApi26),
                Arguments.of(Build.VERSION_CODES.Q, maskUserInputLevel, maskUserInputFromApi29)
            )
        }
    }
}
