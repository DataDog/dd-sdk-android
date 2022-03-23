/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.utils

import android.annotation.SuppressLint
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.datadog.android.security.Encryption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@SuppressLint("InlinedApi")
class TestEncryption : Encryption {

    private companion object {
        const val CIPHER = "AES/GCM/NoPadding"
        const val BLOCK_SIZE_BITS = 256
        const val KEY_ALIAS = "app-test-key"
    }

    private val key: SecretKey
        get() {

            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            if (keyStore.containsAlias(KEY_ALIAS)) {
                return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )

                val keySpec = KeyGenParameterSpec
                    .Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setKeySize(BLOCK_SIZE_BITS)
                    .build()
                keyGenerator.init(keySpec)

                return keyGenerator.generateKey()
            }
        }

    override fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key)

        // 1 byte for IV size, then IV itself, then encrypted data, then tag length
        // Normally IV for GCM is 12 byte by default and tag is 128 bits (this is max), but let's
        // be on the safe side.
        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)
        val tagLength = cipher.parameters.getParameterSpec(GCMParameterSpec::class.java).tLen / 8
        return ByteArray(1) { iv.size.toByte() } +
            iv + encrypted + ByteArray(1) { tagLength.toByte() }
    }

    override fun decrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(CIPHER)

        val ivSize = data[0].toInt()
        val iv = data.copyOfRange(1, ivSize + 1)

        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(data[data.size - 1].toInt() * 8, iv))

        return cipher.doFinal(data.copyOfRange(ivSize + 1, data.size - 1))
    }
}
