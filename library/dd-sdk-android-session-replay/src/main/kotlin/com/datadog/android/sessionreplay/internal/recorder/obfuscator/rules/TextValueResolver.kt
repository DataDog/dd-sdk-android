/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.obfuscator.rules

import android.widget.TextView

internal class TextValueResolver {

    fun resolveTextValue(textView: TextView): String {
        return if (textView.text.isNullOrEmpty()) {
            textView.hint?.toString() ?: ""
        } else {
            textView.text?.toString() ?: ""
        }
    }
}
