/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.security

import com.datadog.tools.annotation.NoOpImplementation

/**
 * Interface that allows storing data in encrypted format. Encryption/decryption round should
 * return exactly the same data as it given for the encryption originally (even if decryption
 * happens in another process/app launch).
 */
@NoOpImplementation
interface Encryption {
    /**
     * Encrypts given [ByteArray] with user-chosen encryption.
     * @param data Bytes to encrypt.
     */
    fun encrypt(data: ByteArray): ByteArray

    /**
     * Decrypts given [ByteArray] with user-chosen encryption.
     * @param data Bytes to decrypt. Beware that data to decrypt could be encrypted in a previous
     * app launch, so implementation should be aware of the case when decryption could
     * fail (for example, key used for encryption is different from key used for decryption, if
     * they are unique for every app launch).
     */
    fun decrypt(data: ByteArray): ByteArray
}
