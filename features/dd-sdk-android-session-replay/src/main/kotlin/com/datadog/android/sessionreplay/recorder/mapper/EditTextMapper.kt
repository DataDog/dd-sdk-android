/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

/**
 * A [WireframeMapper] implementation to map an [EditText] component.
 * In this case any [EditText] for which the input type is considered sensible (password, email
 * address, postal address, numeric password) will be masked with the static mask: [***].
 * All the other text fields will not be masked.
 */
class EditTextMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper
) : TextViewMapper<EditText>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {

    override fun resolveCapturedText(textView: EditText, privacy: SessionReplayPrivacy, isOption: Boolean): String {
        val text = textView.text?.toString().orEmpty()
        val hint = textView.hint?.toString().orEmpty()

        return if (text.isNotEmpty()) {
            resolveCapturedText(textView, text, privacy)
        } else {
            resolveCapturedHint(hint, privacy)
        }
    }

    private fun resolveCapturedText(
        textView: TextView,
        text: String,
        privacy: SessionReplayPrivacy
    ): String {
        val inputTypeVariation = textView.inputType and InputType.TYPE_MASK_VARIATION
        val inputTypeClass = textView.inputType and InputType.TYPE_MASK_CLASS

        val isSensitiveText = (inputTypeClass == InputType.TYPE_CLASS_TEXT) &&
            (inputTypeVariation in SENSITIVE_TEXT_VARIATIONS)

        val isSensitiveNumber = (inputTypeClass == InputType.TYPE_CLASS_NUMBER) &&
            (inputTypeVariation in SENSITIVE_NUMBER_VARIATIONS)

        val isSensitive = isSensitiveText || isSensitiveNumber || (inputTypeClass == InputType.TYPE_CLASS_PHONE)

        return when (privacy) {
            SessionReplayPrivacy.ALLOW -> if (isSensitive) FIXED_INPUT_MASK else text

            SessionReplayPrivacy.MASK,
            SessionReplayPrivacy.MASK_USER_INPUT -> FIXED_INPUT_MASK
        }
    }

    private fun resolveCapturedHint(hint: String, privacy: SessionReplayPrivacy): String {
        return if (privacy == SessionReplayPrivacy.MASK) {
            StringObfuscator.getStringObfuscator().obfuscate(hint)
        } else {
            hint
        }
    }

    companion object {

        internal val SENSITIVE_TEXT_VARIATIONS = arrayOf(
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )

        internal val SENSITIVE_NUMBER_VARIATIONS = arrayOf(
            InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
    }
}
