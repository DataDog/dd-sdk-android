/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.text.InputType
import android.widget.EditText
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.recorder.mapper.EditTextMapper.Companion.SENSITIVE_NUMBER_VARIATIONS
import com.datadog.android.sessionreplay.recorder.mapper.EditTextMapper.Companion.SENSITIVE_TEXT_VARIATIONS
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper.Companion.FIXED_INPUT_MASK
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Ported from legacy `EditTextMapper`. Entered text is replaced with the fixed input mask, except
 * under `MASK_SENSITIVE_INPUTS`, where only sensitive input types (passwords, email and postal
 * addresses, phone numbers) are masked. A hint shown in an empty field is obfuscated under
 * `MASK_ALL` only.
 */
internal class CapturedEditTextMapper(
    viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver,
    internalLogger: InternalLogger,
    canMaskText: Boolean = false
) : CapturedTextViewMapper<EditText>(
    viewBoundsResolver = viewBoundsResolver,
    colorStringFormatter = colorStringFormatter,
    backgroundShapeStyleResolver = backgroundShapeStyleResolver,
    internalLogger = internalLogger,
    canMaskText = canMaskText
) {

    override fun resolveCapturedText(view: EditText, textAndInputPrivacy: TextAndInputPrivacy): CapturedText {
        val text = view.text?.toString().orEmpty()
        val hint = view.hint?.toString().orEmpty()
        return if (text.isNotEmpty()) {
            // The rendered text keeps any TransformationMethod, such as password dots.
            resolveCapturedFieldText(view, view.layout?.text?.toString() ?: text, textAndInputPrivacy)
        } else {
            resolveCapturedHint(hint, textAndInputPrivacy)
        }
    }

    private fun resolveCapturedFieldText(
        view: EditText,
        text: String,
        textAndInputPrivacy: TextAndInputPrivacy
    ): CapturedText {
        val isMasked = when (textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> isSensitiveInputType(view.inputType)
            TextAndInputPrivacy.MASK_ALL,
            TextAndInputPrivacy.MASK_ALL_INPUTS -> true
        }
        return CapturedText(if (isMasked) FIXED_INPUT_MASK else text, isMasked)
    }

    private fun isSensitiveInputType(inputType: Int): Boolean {
        val inputTypeVariation = inputType and InputType.TYPE_MASK_VARIATION
        val inputTypeClass = inputType and InputType.TYPE_MASK_CLASS
        val isSensitiveText = (inputTypeClass == InputType.TYPE_CLASS_TEXT) &&
            (inputTypeVariation in SENSITIVE_TEXT_VARIATIONS)
        val isSensitiveNumber = (inputTypeClass == InputType.TYPE_CLASS_NUMBER) &&
            (inputTypeVariation in SENSITIVE_NUMBER_VARIATIONS)
        return isSensitiveText || isSensitiveNumber || (inputTypeClass == InputType.TYPE_CLASS_PHONE)
    }

    private fun resolveCapturedHint(hint: String, textAndInputPrivacy: TextAndInputPrivacy): CapturedText {
        return if (textAndInputPrivacy == TextAndInputPrivacy.MASK_ALL) {
            CapturedText(StringObfuscator.getStringObfuscator().obfuscate(hint), isMasked = true)
        } else {
            CapturedText(hint, isMasked = false)
        }
    }
}
