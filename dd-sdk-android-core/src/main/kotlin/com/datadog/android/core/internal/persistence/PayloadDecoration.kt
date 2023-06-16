/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence

internal data class PayloadDecoration(
    val prefix: CharSequence,
    val suffix: CharSequence,
    val separator: CharSequence
) {

    val separatorBytes = separator.toString().toByteArray(Charsets.UTF_8)
    val prefixBytes = prefix.toString().toByteArray(Charsets.UTF_8)
    val suffixBytes = suffix.toString().toByteArray(Charsets.UTF_8)

    companion object {
        val JSON_ARRAY_DECORATION = PayloadDecoration("[", "]", ",")
        val NEW_LINE_DECORATION = PayloadDecoration("", "", "\n")
    }
}
