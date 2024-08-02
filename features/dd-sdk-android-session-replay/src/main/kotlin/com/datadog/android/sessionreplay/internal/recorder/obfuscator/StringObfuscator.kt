/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator

import android.os.Build

internal interface StringObfuscator {
    fun obfuscate(stringValue: String): String

    companion object {
        internal const val CHARACTER_MASK = 'x'

        fun getStringObfuscator(): StringObfuscator {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                AndroidNStringObfuscator()
            } else {
                LegacyStringObfuscator()
            }
        }
    }
}
