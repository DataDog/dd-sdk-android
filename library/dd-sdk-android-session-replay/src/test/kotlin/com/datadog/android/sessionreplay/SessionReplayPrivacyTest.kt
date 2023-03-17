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
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.mapper.ButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.CheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.EditTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllCheckBoxMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllCheckedTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllRadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllSwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.RadioButtonMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.SwitchCompatMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewScreenshotWireframeMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
        assertThat(mappers[6].mapper).isInstanceOf(TextWireframeMapper::class.java)
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
        assertThat(mappers[5].mapper).isInstanceOf(EditTextViewMapper::class.java)
        assertThat(mappers[6].type).isEqualTo(TextView::class.java)
        assertThat(mappers[6].mapper).isInstanceOf(MaskAllTextViewMapper::class.java)
        assertThat(mappers[7].type).isEqualTo(ImageView::class.java)
        assertThat(mappers[7].mapper).isInstanceOf(ViewScreenshotWireframeMapper::class.java)
    }
}
