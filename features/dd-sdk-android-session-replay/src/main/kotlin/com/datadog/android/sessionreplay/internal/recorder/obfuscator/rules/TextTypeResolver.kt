/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext

internal class TextTypeResolver {

    @Suppress("ReturnCount")
    fun resolveTextType(textView: TextView, mappingContext: MappingContext): TextType {
        if (textView.isPrivacySensitive()) {
            return TextType.SENSITIVE_TEXT
        }

        if (textView.isInputText()) {
            return if (textView.text.isNullOrEmpty() && !textView.hint.isNullOrEmpty()) {
                TextType.HINTS_TEXT
            } else {
                TextType.INPUT_TEXT
            }
        }

        if (mappingContext.hasOptionSelectorParent) {
            return TextType.OPTION_TEXT
        }

        return TextType.STATIC_TEXT
    }

    private fun TextView.isInputText(): Boolean {
        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        return EditText::class.java.isAssignableFrom(this::class.java)
    }

    private fun TextView.isPrivacySensitive(): Boolean {
        val classType = inputType and EditorInfo.TYPE_MASK_CLASS
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        return classType == EditorInfo.TYPE_CLASS_PHONE ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == EditorInfo.TYPE_TEXT_VARIATION_POSTAL_ADDRESS
    }
}
