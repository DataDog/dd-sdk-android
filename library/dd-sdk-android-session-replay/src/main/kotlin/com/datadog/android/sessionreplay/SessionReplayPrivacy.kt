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
import android.widget.Toolbar
import androidx.appcompat.widget.SwitchCompat
import com.datadog.android.sessionreplay.internal.recorder.mapper.BasePickerMapper
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
import com.datadog.android.sessionreplay.internal.recorder.mapper.UnsupportedViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewScreenshotWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.ViewWireframeMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import androidx.appcompat.widget.Toolbar as AppCompatToolbar

/**
 * Defines the Session Replay privacy policy when recording the sessions.
 * @see SessionReplayPrivacy.ALLOW_ALL
 * @see SessionReplayPrivacy.MASK_ALL
 * @see SessionReplayPrivacy.MASK_USER_INPUT
 *
 */
enum class SessionReplayPrivacy {
    /** Does not apply any privacy rule on the recorded data with an exception for strong privacy
     * sensitive EditTextViews.
     * The EditTextViews which have email, password, postal address or phone number
     * inputType will be masked no matter what the privacy option with space-preserving "x" mask
     * (each char individually)
     **/
    ALLOW_ALL,

    /**
     *  Masks all the elements. All the characters in texts will be replaced by X, images will be
     *  replaced with just a placeholder and switch buttons, check boxes and radio buttons will also
     *  be masked. This is the default privacy rule.
     **/
    MASK_ALL,

    /**
     * Masks most form fields such as inputs, checkboxes, radio buttons, switchers, sliders, etc.
     * while recording all other text as is. Inputs are replaced with three asterisks (***).
     */
    MASK_USER_INPUT;

    @Suppress("LongMethod")
    internal fun mappers(): List<MapperTypeWrapper> {
        val viewWireframeMapper = ViewWireframeMapper()
        val unsupportedViewMapper = UnsupportedViewMapper()
        val imageMapper: ViewScreenshotWireframeMapper
        val textMapper: TextViewMapper
        val buttonMapper: ButtonMapper
        val editTextViewMapper: EditTextViewMapper
        val checkedTextViewMapper: CheckedTextViewMapper
        val checkBoxMapper: CheckBoxMapper
        val radioButtonMapper: RadioButtonMapper
        val switchCompatMapper: SwitchCompatMapper
        val seekBarMapper: SeekBarWireframeMapper?
        val numberPickerMapper: BasePickerMapper?
        when (this) {
            ALLOW_ALL -> {
                imageMapper = ViewScreenshotWireframeMapper(viewWireframeMapper)
                textMapper = TextViewMapper()
                buttonMapper = ButtonMapper(textMapper)
                editTextViewMapper = EditTextViewMapper(textMapper)
                checkedTextViewMapper = CheckedTextViewMapper(textMapper)
                checkBoxMapper = CheckBoxMapper(textMapper)
                radioButtonMapper = RadioButtonMapper(textMapper)
                switchCompatMapper = SwitchCompatMapper(textMapper)
                seekBarMapper = getSeekBarMapper()
                numberPickerMapper = getNumberPickerMapper()
            }
            MASK_ALL -> {
                imageMapper = ViewScreenshotWireframeMapper(viewWireframeMapper)
                textMapper = MaskAllTextViewMapper()
                buttonMapper = ButtonMapper(textMapper)
                editTextViewMapper = EditTextViewMapper(textMapper)
                checkedTextViewMapper = MaskAllCheckedTextViewMapper(textMapper)
                checkBoxMapper = MaskAllCheckBoxMapper(textMapper)
                radioButtonMapper = MaskAllRadioButtonMapper(textMapper)
                switchCompatMapper = MaskAllSwitchCompatMapper(textMapper)
                seekBarMapper = getMaskAllSeekBarMapper()
                numberPickerMapper = getMaskAllNumberPickerMapper()
            }
            MASK_USER_INPUT -> {
                imageMapper = ViewScreenshotWireframeMapper(viewWireframeMapper)
                textMapper = MaskInputTextViewMapper()
                buttonMapper = ButtonMapper(textMapper)
                editTextViewMapper = EditTextViewMapper(textMapper)
                checkedTextViewMapper = MaskAllCheckedTextViewMapper(textMapper)
                checkBoxMapper = MaskAllCheckBoxMapper(textMapper)
                radioButtonMapper = MaskAllRadioButtonMapper(textMapper)
                switchCompatMapper = MaskAllSwitchCompatMapper(textMapper)
                seekBarMapper = getMaskAllSeekBarMapper()
                numberPickerMapper = getMaskAllNumberPickerMapper()
            }
        }
        val mappersList = mutableListOf(
            MapperTypeWrapper(SwitchCompat::class.java, switchCompatMapper.toGenericMapper()),
            MapperTypeWrapper(RadioButton::class.java, radioButtonMapper.toGenericMapper()),
            MapperTypeWrapper(CheckBox::class.java, checkBoxMapper.toGenericMapper()),
            MapperTypeWrapper(CheckedTextView::class.java, checkedTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(Button::class.java, buttonMapper.toGenericMapper()),
            MapperTypeWrapper(EditText::class.java, editTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(TextView::class.java, textMapper.toGenericMapper()),
            MapperTypeWrapper(ImageView::class.java, imageMapper.toGenericMapper()),
            MapperTypeWrapper(AppCompatToolbar::class.java, unsupportedViewMapper.toGenericMapper())
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mappersList.add(0, MapperTypeWrapper(Toolbar::class.java, unsupportedViewMapper.toGenericMapper()))
        }

        seekBarMapper?.let {
            mappersList.add(0, MapperTypeWrapper(SeekBar::class.java, it.toGenericMapper()))
        }
        numberPickerMapper?.let {
            mappersList.add(
                0,
                MapperTypeWrapper(
                    NumberPicker::class.java,
                    it.toGenericMapper()
                )
            )
        }
        return mappersList
    }

    private fun getMaskAllSeekBarMapper(): MaskAllSeekBarWireframeMapper? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MaskAllSeekBarWireframeMapper()
        } else {
            null
        }
    }

    private fun getSeekBarMapper(): SeekBarWireframeMapper? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            SeekBarWireframeMapper()
        } else {
            null
        }
    }

    private fun getNumberPickerMapper(): BasePickerMapper? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NumberPickerMapper()
        } else {
            null
        }
    }

    private fun getMaskAllNumberPickerMapper(): BasePickerMapper? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MaskAllNumberPickerMapper()
        } else {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun WireframeMapper<*, *>.toGenericMapper(): WireframeMapper<View, *> {
        return this as WireframeMapper<View, *>
    }
}
