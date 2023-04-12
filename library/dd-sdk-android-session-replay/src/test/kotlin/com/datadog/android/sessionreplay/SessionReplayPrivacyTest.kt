/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

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
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllCheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllCheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllEditTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllNumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllRadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllSeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllSwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.NumberPickerMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SeekBarWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewScreenshotWireframeMapper
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ApiLevelExtension::class)
)
internal class SessionReplayPrivacyTest {

    @Test
    fun `M return the AllowAll mappers W rule(ALLOW_ALL)`() {
        // When
        val mappers = SessionReplayPrivacy.ALLOW_ALL.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(8)
        assertThat(mappers[0].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(SwitchCompatMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(RadioButtonMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(CheckBoxMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(CheckedTextViewMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(Button::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(EditText::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(EditTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(TextView::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(TextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @Test
    fun `M return the MASK_ALL mappers W rule(MASK_ALL)`() {
        // When
        val mappers = SessionReplayPrivacy.MASK_ALL.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(8)
        assertThat(mappers[0].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(MaskAllSwitchCompatMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(MaskAllRadioButtonMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(MaskAllCheckBoxMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(MaskAllCheckedTextViewMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(Button::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(EditText::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(MaskAllEditTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(TextView::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(MaskAllTextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @Test
    fun `M return the MaskUserInput mappers W rule(MASK_USER_INPUT)`() {
        // When
        val mappers = SessionReplayPrivacy.MASK_USER_INPUT.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(8)
        assertThat(mappers[0].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(SwitchCompatMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(RadioButtonMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(CheckBoxMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(CheckedTextViewMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(Button::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(EditText::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(MaskAllEditTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(TextView::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(TextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @TestTargetApi(26)
    @Test
    fun `M return the AllowAll mappers W rule(ALLOW_ALL, above API 26)`() {
        // When
        val mappers = SessionReplayPrivacy.ALLOW_ALL.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(9)
        assertThat(mappers[0].type).isEqualTo(SeekBar::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(SeekBarWireframeMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(SwitchCompatMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(RadioButtonMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(CheckBoxMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(CheckedTextViewMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(Button::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(EditText::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(EditTextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(TextView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(TextViewMapper::class.java)
        assertThat(mappers[8].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[8].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @TestTargetApi(26)
    @Test
    fun `M return the AllowAll mappers W rule(MASK_USER_INPUT, above API 26)`() {
        // When
        val mappers = SessionReplayPrivacy.MASK_USER_INPUT.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(9)
        assertThat(mappers[0].type).isEqualTo(SeekBar::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(SeekBarWireframeMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(SwitchCompatMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(RadioButtonMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(CheckBoxMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(CheckedTextViewMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(Button::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(EditText::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(MaskAllEditTextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(TextView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(TextViewMapper::class.java)
        assertThat(mappers[8].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[8].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @TestTargetApi(26)
    @Test
    fun `M return the MASK_ALL mappers W rule(MASK_ALL, above API 26)`() {
        // When
        val mappers = SessionReplayPrivacy.MASK_ALL.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(9)
        assertThat(mappers[0].type).isEqualTo(SeekBar::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(MaskAllSeekBarWireframeMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(MaskAllSwitchCompatMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(MaskAllRadioButtonMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(MaskAllCheckBoxMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(MaskAllCheckedTextViewMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(Button::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(EditText::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(MaskAllEditTextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(TextView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(MaskAllTextViewMapper::class.java)
        assertThat(mappers[8].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[8].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @TestTargetApi(29)
    @Test
    fun `M return the AllowAll mappers W rule(ALLOW_ALL, above API 29)`() {
        // When
        val mappers = SessionReplayPrivacy.ALLOW_ALL.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(10)
        assertThat(mappers[0].type).isEqualTo(NumberPicker::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(NumberPickerMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(SeekBar::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(SeekBarWireframeMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(SwitchCompatMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(RadioButtonMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(CheckBoxMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(CheckedTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(Button::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(EditText::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(EditTextViewMapper::class.java)
        assertThat(mappers[8].type).isEqualTo(TextView::class.java)
        assertThat(mappers[8].mapper).isInstanceOf(TextViewMapper::class.java)
        assertThat(mappers[9].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[9].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @TestTargetApi(29)
    @Test
    fun `M return the AllowAll mappers W rule(MASK_USER_INPUT, above API 29)`() {
        // When
        val mappers = SessionReplayPrivacy.MASK_USER_INPUT.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(10)
        assertThat(mappers[0].type).isEqualTo(NumberPicker::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(NumberPickerMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(SeekBar::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(SeekBarWireframeMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(SwitchCompatMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(RadioButtonMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(CheckBoxMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(CheckedTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(Button::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(EditText::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(MaskAllEditTextViewMapper::class.java)
        assertThat(mappers[8].type).isEqualTo(TextView::class.java)
        assertThat(mappers[8].mapper).isInstanceOf(TextViewMapper::class.java)
        assertThat(mappers[9].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[9].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }

    @TestTargetApi(29)
    @Test
    fun `M return the MASK_ALL mappers W rule(MASK_ALL, above API 29)`() {
        // When
        val mappers = SessionReplayPrivacy.MASK_ALL.mappers()

        // Then
        assertThat(mappers.size).isEqualTo(10)
        assertThat(mappers[0].type).isEqualTo(NumberPicker::class.java)
        assertThat(mappers[0].mapper).isInstanceOf(MaskAllNumberPickerMapper::class.java)
        assertThat(mappers[1].type).isEqualTo(SeekBar::class.java)
        assertThat(mappers[1].mapper).isInstanceOf(MaskAllSeekBarWireframeMapper::class.java)
        assertThat(mappers[2].type).isEqualTo(SwitchCompat::class.java)
        assertThat(mappers[2].mapper).isInstanceOf(MaskAllSwitchCompatMapper::class.java)
        assertThat(mappers[3].type).isEqualTo(RadioButton::class.java)
        assertThat(mappers[3].mapper).isInstanceOf(MaskAllRadioButtonMapper::class.java)
        assertThat(mappers[4].type).isEqualTo(CheckBox::class.java)
        assertThat(mappers[4].mapper).isInstanceOf(MaskAllCheckBoxMapper::class.java)
        assertThat(mappers[5].type).isEqualTo(CheckedTextView::class.java)
        assertThat(mappers[5].mapper).isInstanceOf(MaskAllCheckedTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(Button::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(ButtonMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(EditText::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(EditTextViewMapper::class.java)
        assertThat(mappers[8].type).isEqualTo(TextView::class.java)
        assertThat(mappers[8].mapper).isInstanceOf(MaskAllTextViewMapper::class.java)
        assertThat(mappers[9].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[9].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }
}
