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
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver

/**
 * Ported verbatim from legacy `EditTextMapper`: any [EditText] whose input type is considered
 * sensitive (password, email address, postal address, numeric password, phone) is masked with the
 * static mask under `MASK_SENSITIVE_INPUTS`; every other field is masked per the same three-way
 * switch as the generic [CapturedTextViewMapper].
 */
internal class CapturedEditTextMapper(
    viewBoundsResolver: ViewBoundsResolver = DefaultViewBoundsResolver,
    colorStringFormatter: ColorStringFormatter = DefaultColorStringFormatter,
    backgroundShapeStyleResolver: CapturedBackgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(),
    internalLogger: InternalLogger
) : CapturedTextViewMapper<EditText>(
    viewBoundsResolver,
    colorStringFormatter,
    backgroundShapeStyleResolver,
    internalLogger
) {

    override fun resolveCapturedText(view: EditText, textAndInputPrivacy: TextAndInputPrivacy): String {
        val text = view.text?.toString().orEmpty()
        val hint = view.hint?.toString().orEmpty()
        return if (text.isNotEmpty()) {
            resolveCapturedFieldText(view, text, textAndInputPrivacy)
        } else {
            resolveCapturedHint(hint, textAndInputPrivacy)
        }
    }

    private fun resolveCapturedFieldText(
        view: EditText,
        text: String,
        textAndInputPrivacy: TextAndInputPrivacy
    ): String {
        val inputTypeVariation = view.inputType and InputType.TYPE_MASK_VARIATION
        val inputTypeClass = view.inputType and InputType.TYPE_MASK_CLASS

        val isSensitiveText = (inputTypeClass == InputType.TYPE_CLASS_TEXT) &&
            (inputTypeVariation in SENSITIVE_TEXT_VARIATIONS)
        val isSensitiveNumber = (inputTypeClass == InputType.TYPE_CLASS_NUMBER) &&
            (inputTypeVariation in SENSITIVE_NUMBER_VARIATIONS)
        val isSensitive = isSensitiveText || isSensitiveNumber || (inputTypeClass == InputType.TYPE_CLASS_PHONE)

        return when (textAndInputPrivacy) {
            TextAndInputPrivacy.MASK_SENSITIVE_INPUTS -> if (isSensitive) FIXED_INPUT_MASK else text
            TextAndInputPrivacy.MASK_ALL,
            TextAndInputPrivacy.MASK_ALL_INPUTS -> FIXED_INPUT_MASK
        }
    }

    private fun resolveCapturedHint(hint: String, textAndInputPrivacy: TextAndInputPrivacy): String {
        return if (textAndInputPrivacy == TextAndInputPrivacy.MASK_ALL) {
            StringObfuscator.getStringObfuscator().obfuscate(hint)
        } else {
            hint
        }
    }

    private companion object {
        const val FIXED_INPUT_MASK = "***"

        val SENSITIVE_TEXT_VARIATIONS = intArrayOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        val SENSITIVE_NUMBER_VARIATIONS = intArrayOf(
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
    }
}
