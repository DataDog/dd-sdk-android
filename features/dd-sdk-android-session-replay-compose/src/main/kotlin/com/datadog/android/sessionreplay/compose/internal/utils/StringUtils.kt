/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.text.AnnotatedString

internal fun resolveAnnotatedString(value: Any?): String {
    return if (value is AnnotatedString) {
        if (value.paragraphStyles.isEmpty() &&
            value.spanStyles.isEmpty() &&
            value.getStringAnnotations(0, value.text.length).isEmpty()
        ) {
            value.text
        } else {
            // Save space if we there is text only in the object
            value.toString()
        }
    } else if (value is Collection<*>) {
        val sb = StringBuilder()
        value.forEach {
            sb.append(resolveAnnotatedString(it))
        }
        sb.toString()
    } else {
        value.toString()
    }
}
