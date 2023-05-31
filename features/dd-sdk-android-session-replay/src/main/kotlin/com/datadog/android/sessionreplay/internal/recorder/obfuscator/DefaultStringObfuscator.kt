/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import android.os.Build
import androidx.annotation.RequiresApi

internal class DefaultStringObfuscator : StringObfuscator {

    override fun obfuscate(stringValue: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            obfuscateUsingCodeStream(stringValue)
        } else {
            obfuscateUsingCharacterCode(stringValue)
        }
    }

    private fun obfuscateUsingCharacterCode(stringValue: String): String {
        return String(
            CharArray(stringValue.length) {
                val character = stringValue[it]
                // Given that we replace each printable character with `x` for 2 chars expressions
                // as emojis we will have 2 `x` instead of 1 but this will not be a problem as the
                // obfuscation will still be applied.
                if (Character.isWhitespace(character.code)) {
                    character
                } else {
                    CHARACTER_MASK
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun obfuscateUsingCodeStream(stringValue: String): String {
        // Because we are using the CharSequence.codePoints() stream we are going to correctly
        // handle the cases where the text contains 2 chars expression. In this case one single
        // codePoint will be returned for those 2 chars and the obfuscation char will be `x`.
        val stringBuilder = StringBuilder()
        stringValue.codePoints().forEach {
            if (Character.isWhitespace(it)) {
                // I don't think we should log this case here. I could not even reproduce it
                // in my tests. As long as there is a valid string there there should not be
                // any problem.
                @Suppress("SwallowedException")
                try {
                    stringBuilder.append(Character.toChars(it))
                } catch (e: IllegalArgumentException) {
                    stringBuilder.append(CHARACTER_MASK)
                }
            } else {
                stringBuilder.append(CHARACTER_MASK)
            }
        }
        return stringBuilder.toString()
    }

    companion object {
        private const val CHARACTER_MASK = 'x'
    }
}
