/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator.Companion.CHARACTER_MASK

internal class LegacyStringObfuscator : StringObfuscator {

    override fun obfuscate(stringValue: String): String {
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
}
