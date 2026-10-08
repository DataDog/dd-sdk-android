/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import androidx.collection.LruCache
import com.datadog.android.flags.internal.model.generated.Obfuscation
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.security.MessageDigest

/** Encoding metadata validated by read(). */
// Keep copy() private so callers cannot bypass salt validation.
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

    companion object {
        const val SCHEME = "flag-key-sha256-v1"
        const val CAPABILITY = "assignment-encoding-flag-key-256-v1"
        private const val SALT_BYTES = 16
        private const val HEX_RADIX = 16
        private const val DIGEST_BYTES = 32
        private const val LOOKUP_CACHE_LIMIT = 1024

        /** Throws for unsupported or inconsistent descriptors, including explicit JSON null. */
        // JSON errors propagate to the response or cache parser, where they are caught.
        @Throws(JsonParseException::class)
        @Suppress("ThrowsCount")
        fun read(json: JsonObject): FlagKeyObfuscation? {
            val obfuscated = json.get("obfuscated")
            val isBoolean = obfuscated?.isJsonPrimitive == true && obfuscated.asJsonPrimitive.isBoolean
            val isPlaintext = obfuscated == null || (isBoolean && !obfuscated.asBoolean)
            if (isPlaintext && !json.has("obfuscation")) return null
            if (!isBoolean || !obfuscated.asBoolean) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                throw JsonParseException("Invalid flag-key obfuscation metadata")
            }
            val descriptor = PrecomputedFlagJson.objectValue(json.get("obfuscation"))
            val scheme = descriptor.get("scheme")
            if (scheme?.isJsonPrimitive != true || !scheme.asJsonPrimitive.isString || scheme.asString != SCHEME) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                throw JsonParseException("Unsupported flag-key obfuscation scheme")
            }
            val salt = descriptor.get("salt")
            if (salt?.isJsonPrimitive != true || !salt.asJsonPrimitive.isString ||
                !isLowercaseHex(salt.asString, SALT_BYTES)
            ) {
                @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                throw JsonParseException("Flag-key salt must contain 32 lowercase hexadecimal characters")
            }
            return FlagKeyObfuscation(Obfuscation.fromJsonObject(descriptor).salt)
        }

        // Caught by the response or cache parser.
        @Throws(JsonParseException::class)
        fun validateKeys(keys: Iterator<String>) {
            keys.forEach {
                if (!isLowercaseHex(it, DIGEST_BYTES)) {
                    @Suppress("ThrowingInternalException") // Caught by the response or cache parser.
                    throw JsonParseException("Invalid encoded flag key")
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
