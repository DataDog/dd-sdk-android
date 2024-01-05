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
import android.widget.ImageView
import android.widget.NumberPicker
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toolbar
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar as AppCompatToolbar
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64LRUCache
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.BitmapPool
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.mapper.BasePickerMapper
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
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator

/**
 * Defines the Session Replay privacy policy when recording the sessions.
 * @see SessionReplayPrivacy.ALLOW
 * @see SessionReplayPrivacy.MASK
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
    ALLOW,

    /**
     *  Masks all the elements. All the characters in texts will be replaced by X, images will be
     *  replaced with just a placeholder and switch buttons, check boxes and radio buttons will also
     *  be masked. This is the default privacy rule.
     **/
    MASK,

    /**
     * Masks most form fields such as inputs, checkboxes, radio buttons, switchers, sliders, etc.
     * while recording all other text as is. Inputs are replaced with three asterisks (***).
     */
    MASK_USER_INPUT;

    @Suppress("LongMethod")
    internal fun mappers(): List<MapperTypeWrapper> {
        val base64Serializer = buildBase64Serializer()
        val imageWireframeHelper = ImageWireframeHelper(base64Serializer = base64Serializer)
        val uniqueIdentifierGenerator = UniqueIdentifierGenerator

        val unsupportedViewMapper = UnsupportedViewMapper()
        val imageViewMapper = ImageViewMapper(
            imageWireframeHelper = imageWireframeHelper,
            uniqueIdentifierGenerator = uniqueIdentifierGenerator
        )
        val textMapper: TextViewMapper
        val buttonMapper: ButtonMapper
        val checkedTextViewMapper: CheckedTextViewMapper
        val checkBoxMapper: CheckBoxMapper
        val radioButtonMapper: RadioButtonMapper
        val switchCompatMapper: SwitchCompatMapper
        val seekBarMapper: SeekBarWireframeMapper?
        val numberPickerMapper: BasePickerMapper?
        when (this) {
            ALLOW -> {
                textMapper = TextViewMapper(
                    imageWireframeHelper = imageWireframeHelper,
                    uniqueIdentifierGenerator = uniqueIdentifierGenerator
                )
                buttonMapper = ButtonMapper(textMapper)
                checkedTextViewMapper = CheckedTextViewMapper(textMapper)
                checkBoxMapper = CheckBoxMapper(textMapper)
                radioButtonMapper = RadioButtonMapper(textMapper)
                switchCompatMapper = SwitchCompatMapper(textMapper)
                seekBarMapper = getSeekBarMapper()
                numberPickerMapper = getNumberPickerMapper()
            }
            MASK -> {
                textMapper = MaskTextViewMapper(
                    imageWireframeHelper = imageWireframeHelper,
                    uniqueIdentifierGenerator = uniqueIdentifierGenerator
                )
                buttonMapper = ButtonMapper(textMapper)
                checkedTextViewMapper = MaskCheckedTextViewMapper(textMapper)
                checkBoxMapper = MaskCheckBoxMapper(textMapper)
                radioButtonMapper = MaskRadioButtonMapper(textMapper)
                switchCompatMapper = MaskSwitchCompatMapper(textMapper)
                seekBarMapper = getMaskSeekBarMapper()
                numberPickerMapper = getMaskNumberPickerMapper()
            }
            MASK_USER_INPUT -> {
                textMapper = MaskInputTextViewMapper(
                    imageWireframeHelper = imageWireframeHelper,
                    uniqueIdentifierGenerator = uniqueIdentifierGenerator
                )
                buttonMapper = ButtonMapper(textMapper)
                checkedTextViewMapper = MaskCheckedTextViewMapper(textMapper)
                checkBoxMapper = MaskCheckBoxMapper(textMapper)
                radioButtonMapper = MaskRadioButtonMapper(textMapper)
                switchCompatMapper = MaskSwitchCompatMapper(textMapper)
                seekBarMapper = getMaskSeekBarMapper()
                numberPickerMapper = getMaskNumberPickerMapper()
            }
        }
        val mappersList = mutableListOf(
            MapperTypeWrapper(SwitchCompat::class.java, switchCompatMapper.toGenericMapper()),
            MapperTypeWrapper(RadioButton::class.java, radioButtonMapper.toGenericMapper()),
            MapperTypeWrapper(CheckBox::class.java, checkBoxMapper.toGenericMapper()),
            MapperTypeWrapper(CheckedTextView::class.java, checkedTextViewMapper.toGenericMapper()),
            MapperTypeWrapper(Button::class.java, buttonMapper.toGenericMapper()),
            MapperTypeWrapper(TextView::class.java, textMapper.toGenericMapper()),
            MapperTypeWrapper(ImageView::class.java, imageViewMapper.toGenericMapper()),
            MapperTypeWrapper(AppCompatToolbar::class.java, unsupportedViewMapper.toGenericMapper())
        )

        mappersList.add(
            0,
            MapperTypeWrapper(Toolbar::class.java, unsupportedViewMapper.toGenericMapper())
        )

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

    private fun buildBase64Serializer(): Base64Serializer {
        val bitmapPool = BitmapPool()
        val base64LRUCache = Base64LRUCache()

        val builder = Base64Serializer.Builder(
            bitmapPool = bitmapPool,
            base64LRUCache = base64LRUCache
        )
        return builder.build()
    }

    private fun getMaskSeekBarMapper(): MaskSeekBarWireframeMapper? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            MaskSeekBarWireframeMapper()
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

    private fun getMaskNumberPickerMapper(): BasePickerMapper? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MaskNumberPickerMapper()
        } else {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun WireframeMapper<*, *>.toGenericMapper(): WireframeMapper<View, *> {
        return this as WireframeMapper<View, *>
    }
}
