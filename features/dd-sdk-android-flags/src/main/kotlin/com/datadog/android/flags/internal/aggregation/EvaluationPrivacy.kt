/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import java.nio.charset.CharacterCodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

internal object EvaluationPrivacy {
    private const val BYTE_MASK = 0xff
    private const val HEX_DIGIT_BITS = 4
    private const val HEX_DIGIT_MASK = 0xf
    private const val HEX_DIGITS = "0123456789abcdef"

    fun targetingKey(value: String?, observeFullEvaluationData: Boolean): String? {
        if (value == null || observeFullEvaluationData || value.isEmpty()) return value
        return try {
            val bytes = value.encodeToByteArray(throwOnInvalidSequence = true)
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(bytes)
            "sha256_" + digest.digest().joinToString("") { byte ->
                val unsigned = byte.toInt() and BYTE_MASK
                HEX_DIGITS[unsigned ushr HEX_DIGIT_BITS].toString() + HEX_DIGITS[unsigned and HEX_DIGIT_MASK]
            }
        } catch (_: CharacterCodingException) {
            // Invalid UTF-16 must be omitted, never hashed with replacement characters.
            null
        } catch (_: IndexOutOfBoundsException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: NoSuchAlgorithmException) {
            // SHA-256 is required by Android; omission is the safe fallback if unavailable.
            null
        }
    }

    fun errorCode(code: String?, message: String?): String? = when {
        code == null && message == null -> null
        code in ERROR_CODES -> code
        else -> "GENERAL"
    }

    private val ERROR_CODES = setOf(
        "PROVIDER_NOT_READY",
        "PROVIDER_FATAL",
        "FLAG_NOT_FOUND",
        "PARSE_ERROR",
        "TYPE_MISMATCH",
        "TARGETING_KEY_MISSING",
        "INVALID_CONTEXT",
        "GENERAL"
    )
}
