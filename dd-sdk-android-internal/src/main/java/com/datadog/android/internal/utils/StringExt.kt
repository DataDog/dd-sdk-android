/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.utils

import android.util.Base64

/**
 * Encodes this string to Base64 using UTF-8 encoding.
 *
 * @return the Base64-encoded representation of this string, without line wrapping.
 */
fun String.toBase64(): String {
    val bytes = this.toByteArray(Charsets.UTF_8)
    @Suppress("UnsafeThirdPartyFunctionCall") // cannot throw UnsupportedEncodingException
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

/**
 * Decodes this Base64-encoded string back to its original UTF-8 representation.
 *
 * @return the decoded string, or `null` if the input is not valid Base64.
 */
fun String.fromBase64(): String? {
    return try {
        val decodedBytes = Base64.decode(this, Base64.NO_WRAP)
        decodedBytes?.toString(Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
