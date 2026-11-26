/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DependencyFlagKeyEncryptionTest {

    @Test
    fun `M decrypt canonical vector W decrypt()`() {
        // When
        val result = DependencyFlagKeyEncryption.decrypt(ENVELOPE, ROOT_FLAG_KEY, SALT)

        // Then
        assertThat(result).isEqualTo("new-payment-flow")
    }

    @Test
    fun `M reject envelope W decrypt() {different root}`() {
        // When
        val result = DependencyFlagKeyEncryption.decrypt(ENVELOPE, "different-root", SALT)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M reject envelope W decrypt() {authentication tag changed}`() {
        // Given
        val tampered = ENVELOPE.dropLast(1) + if (ENVELOPE.last() == 'A') "B" else "A"

        // When
        val result = DependencyFlagKeyEncryption.decrypt(tampered, ROOT_FLAG_KEY, SALT)

        // Then
        assertThat(result).isNull()
    }

    private companion object {
        const val SALT = "000102030405060708090a0b0c0d0e0f"
        const val ROOT_FLAG_KEY = "checkout-redesign"
        const val ENVELOPE = "tATBHdKpF-zxZ2Fr70UGwTMfPcXSdulh78uKYPpNzVBoSlNnB6qMCTtI1o8"
    }
}
