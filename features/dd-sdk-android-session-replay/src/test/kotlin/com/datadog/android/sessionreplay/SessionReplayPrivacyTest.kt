/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.os.Build
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
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
import com.datadog.android.sessionreplay.internal.recorder.mapper.ImageViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MapperTypeWrapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskCheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskCheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskInputTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskNumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskRadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskSeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskSwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.UnsupportedViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.tools.unit.setStaticValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
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
            SessionReplayPrivacy.ALLOW.toString() -> SessionReplayPrivacy.ALLOW.mappers()
            SessionReplayPrivacy.MASK.toString() -> SessionReplayPrivacy.MASK.mappers()
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
        private val mockUnsupportedViewMapper: UnsupportedViewMapper = mock()
        private val mockImageViewMapper: ImageViewMapper = mock()
        private val mockWebViewWireframeMapper: WebViewWireframeMapper = mock()

        private val baseMappers = listOf(
            MapperTypeWrapper(Button::class.java, mockButtonMapper.toGenericMapper()),
            MapperTypeWrapper(ImageView::class.java, mockImageViewMapper.toGenericMapper()),
            MapperTypeWrapper(AppCompatToolbar::class.java, mockUnsupportedViewMapper.toGenericMapper()),
            MapperTypeWrapper(WebView::class.java, mockWebViewWireframeMapper.toGenericMapper())
        )

        // ALLOW
        private val mockTextViewMapper: TextViewMapper = mock()
        private val mockSwitchCompatMapper: SwitchCompatMapper = mock()
        private val mockCheckedTextViewMapper: CheckedTextViewMapper = mock()
        private val mockCheckBoxMapper: CheckBoxMapper = mock()
        private val mockRadioButtonMapper: RadioButtonMapper = mock()
        private val mockSeekBarMapper: SeekBarWireframeMapper = mock()
        private val mockNumberPickerMapper: NumberPickerMapper = mock()

        private val allow = baseMappers + listOf(
            MapperTypeWrapper(TextView::class.java, mockTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(CheckedTextView::class.java, mockCheckedTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(CheckBox::class.java, mockCheckBoxMapper.toGenericMapper()),
            MapperTypeWrapper(RadioButton::class.java, mockRadioButtonMapper.toGenericMapper()),
            MapperTypeWrapper(SwitchCompat::class.java, mockSwitchCompatMapper.toGenericMapper())
        )

        private val allowFromApi21 = allow + listOf(
            MapperTypeWrapper(Toolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
        )

        private val allowFromApi26 = allowFromApi21 + listOf(
            MapperTypeWrapper(SeekBar::class.java, mockSeekBarMapper.toGenericMapper())
        )

        private val allowFromApi29 = allowFromApi26 + listOf(
            MapperTypeWrapper(NumberPicker::class.java, mockNumberPickerMapper.toGenericMapper())
        )

        // MASK
        private val mockMaskTextViewMapper: MaskTextViewMapper = mock()
        private val mockMaskCheckedTextViewMapper: MaskCheckedTextViewMapper = mock()
        private val mockMaskCheckBoxMapper: MaskCheckBoxMapper = mock()
        private val mockMaskRadioButtonMapper: MaskRadioButtonMapper = mock()
        private val mockMaskSwitchCompatMapper: MaskSwitchCompatMapper = mock()
        private val mockMaskSeekBarWireframeMapper: MaskSeekBarWireframeMapper = mock()
        private val mockMaskNumberPickerMapper: MaskNumberPickerMapper = mock()

        private val mask = baseMappers + listOf(
            MapperTypeWrapper(TextView::class.java, mockMaskTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(CheckedTextView::class.java, mockMaskCheckedTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(CheckBox::class.java, mockMaskCheckBoxMapper.toGenericMapper()),
            MapperTypeWrapper(RadioButton::class.java, mockMaskRadioButtonMapper.toGenericMapper()),
            MapperTypeWrapper(SwitchCompat::class.java, mockMaskSwitchCompatMapper.toGenericMapper())
        )

        private val maskFromApi21 = mask + listOf(
            MapperTypeWrapper(Toolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
        )

        private val maskFromApi26 = maskFromApi21 + listOf(
            MapperTypeWrapper(SeekBar::class.java, mockMaskSeekBarWireframeMapper.toGenericMapper())
        )

        private val maskFromApi29 = maskFromApi26 + listOf(
            MapperTypeWrapper(NumberPicker::class.java, mockMaskNumberPickerMapper.toGenericMapper())
        )

        // MASK_USER_INPUT
        private val mockMaskInputTextViewMapper: MaskInputTextViewMapper = mock()

        private val maskUserInput = baseMappers + listOf(
            MapperTypeWrapper(TextView::class.java, mockMaskInputTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(CheckedTextView::class.java, mockMaskCheckedTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(CheckBox::class.java, mockMaskCheckBoxMapper.toGenericMapper()),
            MapperTypeWrapper(RadioButton::class.java, mockMaskRadioButtonMapper.toGenericMapper()),
            MapperTypeWrapper(SwitchCompat::class.java, mockMaskSwitchCompatMapper.toGenericMapper())
        )

        private val maskUserInputFromApi21 = maskUserInput + listOf(
            MapperTypeWrapper(Toolbar::class.java, mockUnsupportedViewMapper.toGenericMapper())
        )

        private val maskUserInputFromApi26 = maskUserInputFromApi21 + listOf(
            MapperTypeWrapper(SeekBar::class.java, mockMaskSeekBarWireframeMapper.toGenericMapper())
        )

        private val maskUserInputFromApi29 = maskUserInputFromApi26 + listOf(
            MapperTypeWrapper(NumberPicker::class.java, mockMaskNumberPickerMapper.toGenericMapper())
        )

        @JvmStatic
        @Parameterized.Parameters
        fun provideTestArguments(): Stream<Arguments> {
            val allowLevel = SessionReplayPrivacy.ALLOW.toString()
            val maskLevel = SessionReplayPrivacy.MASK.toString()
            val maskUserInputLevel = SessionReplayPrivacy.MASK_USER_INPUT.toString()

            return Stream.of(
                Arguments.of(Build.VERSION_CODES.LOLLIPOP, allowLevel, allowFromApi21),
                Arguments.of(Build.VERSION_CODES.O, allowLevel, allowFromApi26),
                Arguments.of(Build.VERSION_CODES.Q, allowLevel, allowFromApi29),
                Arguments.of(Build.VERSION_CODES.LOLLIPOP, maskLevel, maskFromApi21),
                Arguments.of(Build.VERSION_CODES.O, maskLevel, maskFromApi26),
                Arguments.of(Build.VERSION_CODES.Q, maskLevel, maskFromApi29),
                Arguments.of(Build.VERSION_CODES.LOLLIPOP, maskUserInputLevel, maskUserInputFromApi21),
                Arguments.of(Build.VERSION_CODES.O, maskUserInputLevel, maskUserInputFromApi26),
                Arguments.of(Build.VERSION_CODES.Q, maskUserInputLevel, maskUserInputFromApi29)
            )
        }
    }
}
