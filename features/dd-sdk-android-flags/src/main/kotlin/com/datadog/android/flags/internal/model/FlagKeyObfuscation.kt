/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/** The public encoding descriptor travels with its assignment map. */
@ConsistentCopyVisibility
internal data class FlagKeyObfuscation private constructor(val salt: String) {
    @Suppress("UnsafeThirdPartyFunctionCall") // SHA-256 is mandatory on Android; salt and Unicode are validated.
    fun encode(key: String): String? {
        // Reject unpaired surrogates instead of silently replacing their UTF-8 encoding.
        if (!isWellFormedUnicode(key)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("datadog.feature-flags.flag-key.v1\u0000".toByteArray(Charsets.UTF_8))
        digest.update(ByteArray(SALT_BYTES) { salt.substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() })
        digest.update(key.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Both fields have validated, non-null names and values.
    fun toJson(): JSONObject = JSONObject().put("scheme", SCHEME).put("salt", salt)

    companion object {
        const val SCHEME = "flag-key-sha256-v1"
        const val CAPABILITY = "assignment-encoding-flag-key-256-v1"
        private const val SALT_BYTES = 16
        private const val HEX_RADIX = 16
        private const val DIGEST_BYTES = 32

        /** Throws for unsupported or inconsistent descriptors, including explicit JSON null. */
        // JSON errors propagate to the response or cache parser, where they are caught.
        @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException", "ThrowsCount")
        fun read(json: JSONObject): FlagKeyObfuscation? {
            val obfuscated = json.opt("obfuscated")
            if ((obfuscated == null || obfuscated == false) && !json.has("obfuscation")) return null
            if (obfuscated != true) throw JSONException("Invalid flag-key obfuscation metadata")
            val descriptor = json.getJSONObject("obfuscation")
            if (descriptor.opt("scheme") != SCHEME) throw JSONException("Unsupported flag-key obfuscation scheme")
            val salt = descriptor.opt("salt")
            if (salt !is String || !isLowercaseHex(salt, SALT_BYTES)) {
                throw JSONException("Flag-key salt must contain 32 lowercase hexadecimal characters")
            }
            return FlagKeyObfuscation(salt)
        }

        // Caught by the response or cache parser.
        @Suppress("UnsafeThirdPartyFunctionCall", "ThrowingInternalException")
        fun validateKeys(keys: Iterator<String>) {
            keys.forEach {
                if (!isLowercaseHex(it, DIGEST_BYTES)) throw JSONException("Invalid encoded flag key")
            }
        }

        private fun isLowercaseHex(value: String, bytes: Int): Boolean =
            value.length == bytes * 2 && value.all { it in '0'..'9' || it in 'a'..'f' }

        @Suppress("UnsafeThirdPartyFunctionCall", "ReturnCount") // Character predicates cannot throw.
        private fun isWellFormedUnicode(value: String): Boolean {
            var index = 0
            while (index < value.length) {
                val char = value[index++]
                if (char.isHighSurrogate()) {
                    if (index >= value.length || !value[index++].isLowSurrogate()) return false
                } else if (char.isLowSurrogate()) {
                    return false
                }
            }
            return true
        }
    }
}

internal data class PrecomputedAssignments(
    val flags: Map<String, PrecomputedFlag>,
    val obfuscation: FlagKeyObfuscation? = null
)
