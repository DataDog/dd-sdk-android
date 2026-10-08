/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import androidx.collection.LruCache
import org.json.JSONException
import org.json.JSONObject
import java.security.MessageDigest

/** The public encoding descriptor travels with its assignment map. */
@ConsistentCopyVisibility
internal data class FlagKeyObfuscation private constructor(val salt: String) {
    @Suppress("UnsafeThirdPartyFunctionCall") // The cache limit is a positive constant.
    private val lookupKeys = LruCache<String, String>(LOOKUP_CACHE_LIMIT)

    fun encode(key: String): String? {
        // Reject unpaired surrogates instead of silently replacing their UTF-8 encoding.
        if (!isWellFormedUnicode(key)) return null
        @Suppress("UnsafeThirdPartyFunctionCall") // The key is non-null.
        val cachedKey = lookupKeys.get(key)
        return cachedKey ?: run {
            @Suppress("UnsafeThirdPartyFunctionCall") // SHA-256 is mandatory on Android.
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update("datadog.feature-flags.flag-key.v1\u0000".toByteArray(Charsets.UTF_8))
            @Suppress("UnsafeThirdPartyFunctionCall") // The size is positive; the salt is validated hexadecimal.
            val saltBytes = ByteArray(SALT_BYTES) { salt.substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() }
            digest.update(saltBytes)
            digest.update(key.toByteArray(Charsets.UTF_8))
            digest.digest().joinToString("") {
                @Suppress("UnsafeThirdPartyFunctionCall") // The constant format accepts a byte.
                "%02x".format(it)
            }.also {
                @Suppress("UnsafeThirdPartyFunctionCall") // The key and encoded value are non-null.
                lookupKeys.put(key, it)
            }
        }
    }

    fun toJson(): JSONObject {
        @Suppress("UnsafeThirdPartyFunctionCall") // Both fields have validated, non-null names and string values.
        return JSONObject().put("scheme", SCHEME).put("salt", salt)
    }

    companion object {
        const val SCHEME = "flag-key-sha256-v1"
        const val CAPABILITY = "assignment-encoding-flag-key-256-v1"
        private const val SALT_BYTES = 16
        private const val HEX_RADIX = 16
        private const val DIGEST_BYTES = 32
        private const val LOOKUP_CACHE_LIMIT = 1024

        /** Throws for unsupported or inconsistent descriptors, including explicit JSON null. */
        // JSON errors propagate to the response or cache parser, where they are caught.
        @Suppress("ThrowsCount")
        fun read(json: JSONObject): FlagKeyObfuscation? {
            val obfuscated = json.opt("obfuscated")
            if ((obfuscated == null || obfuscated == false) && !json.has("obfuscation")) return null
            if (obfuscated != true) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                throw JSONException("Invalid flag-key obfuscation metadata")
            }
            @Suppress("UnsafeThirdPartyFunctionCall") // JSON errors are caught by the response or cache parser.
            val descriptor = json.getJSONObject("obfuscation")
            if (descriptor.opt("scheme") != SCHEME) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                throw JSONException("Unsupported flag-key obfuscation scheme")
            }
            val salt = descriptor.opt("salt")
            if (salt !is String || !isLowercaseHex(salt, SALT_BYTES)) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                throw JSONException("Flag-key salt must contain 32 lowercase hexadecimal characters")
            }
            return FlagKeyObfuscation(salt)
        }

        // Caught by the response or cache parser.
        fun validateKeys(keys: Iterator<String>) {
            keys.forEach {
                if (!isLowercaseHex(it, DIGEST_BYTES)) {
                    @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                    throw JSONException("Invalid encoded flag key")
                }
            }
        }

        private fun isLowercaseHex(value: String, bytes: Int): Boolean =
            value.length == bytes * 2 && value.all { it in '0'..'9' || it in 'a'..'f' }

        @Suppress("ReturnCount")
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
