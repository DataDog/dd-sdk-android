/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticates and decrypts root-relative prerequisite keys from obfuscated responses. */
internal object DependencyFlagKeyEncryption {
    const val SCHEME = "dependency-key-hkdf-sha256-aes-256-gcm-v1"

    @Suppress("ReturnCount", "UnsafeThirdPartyFunctionCall") // All crypto and decoding failures become null.
    fun decrypt(envelope: String, rootFlagKey: String, salt: String): String? {
        return try {
            val bytes = decodeBase64Url(envelope) ?: return null
            if (bytes.size < NONCE_BYTES + TAG_BYTES) return null
            val nonce = bytes.copyOfRange(0, NONCE_BYTES)
            val ciphertext = bytes.copyOfRange(NONCE_BYTES, bytes.size)
            val pseudoRandomKey = hmac(hexBytes(salt), rootFlagKey.toByteArray(Charsets.UTF_8))
            val encryptionKey = hmac(pseudoRandomKey, ENCRYPTION_INFO + byteArrayOf(1))

            @Suppress("UnsafeThirdPartyFunctionCall") // AES/GCM/NoPadding is mandatory on supported Android API levels.
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encryptionKey, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(AAD)
            val plaintext = cipher.doFinal(ciphertext)
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(plaintext))
                .toString()
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: ProviderException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: CharacterCodingException) {
            null
        }
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // HmacSHA256 is mandatory; callers catch provider failures.
    private fun hmac(key: ByteArray, message: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(message)
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // Salt format is validated by the response parser.
    private fun hexBytes(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(HEX_RADIX).toByte() }

    @Suppress("UnsafeThirdPartyFunctionCall") // Bounds and alphabet are validated before indexed decoding.
    private fun decodeBase64Url(value: String): ByteArray? {
        if (value.isEmpty() || value.length % BASE64_GROUP_SIZE == 1 ||
            value.any { it !in BASE64_URL_ALPHABET }
        ) {
            return null
        }
        val output = ByteArray(value.length * BASE64_BITS / BYTE_BITS)
        var buffer = 0
        var bits = 0
        var outputIndex = 0
        value.forEach { character ->
            buffer = (buffer shl BASE64_BITS) or BASE64_URL_ALPHABET.indexOf(character)
            bits += BASE64_BITS
            if (bits >= BYTE_BITS) {
                bits -= BYTE_BITS
                output[outputIndex++] = (buffer ushr bits).toByte()
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        return output.takeIf { buffer == 0 }
    }

    private val AAD = "datadog.feature-flags.dependency-key.v1\u0000".toByteArray(Charsets.UTF_8)
    private val ENCRYPTION_INFO = "datadog.feature-flags.dependency-key.v1\u0000enc".toByteArray(Charsets.UTF_8)
    private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val NONCE_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = 128
    private const val HEX_RADIX = 16
    private const val BASE64_BITS = 6
    private const val BYTE_BITS = 8
    private const val BASE64_GROUP_SIZE = 4
}
