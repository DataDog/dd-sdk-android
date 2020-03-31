/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

internal data class PayloadDecoration(
    val prefix: CharSequence,
    val suffix: CharSequence,
    val separator: CharSequence
) {

    companion object {
        val JSON_ARRAY_DECORATION = PayloadDecoration("[", "]", ",")
        val NEW_LINE_DECORATION = PayloadDecoration("", "", "\n")
    }
}
