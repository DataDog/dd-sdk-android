/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.github.luben.zstd

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented tests for [ZstdIOException].
 */
@RunWith(AndroidJUnit4::class)
class ZstdIOExceptionTest {

    @Test
    fun constructWithCodeAndMessage_storesErrorCode() {
        // Given
        val errorCode = 42L
        val errorMessage = "Test error message"

        // When
        val exception = ZstdIOException(errorCode, errorMessage)

        // Then
        assertThat(exception.errorCode).isEqualTo(errorCode)
        assertThat(exception.message).isEqualTo(errorMessage)
    }

    @Test
    fun zstdIOException_isInstanceOfIOException() {
        // Given
        val exception = ZstdIOException(1L, "Test")

        // Then
        assertThat(exception).isInstanceOf(java.io.IOException::class.java)
    }

    @Test
    fun errMemoryAllocation_returnsNonZeroErrorCode() {
        // When
        val errorCode = Zstd.errMemoryAllocation()

        // Then
        // The memory allocation error code is an enum constant (positive value like 64)
        // It's used for constructing ZstdIOException, not for isError() checks
        assertThat(errorCode).isNotEqualTo(0L)
        assertThat(errorCode).isGreaterThan(0L)
    }

    @Test
    fun isError_withSuccessCode_returnsFalse() {
        // Given - 0 and positive values are success codes
        val successCode = 0L

        // When
        val isError = Zstd.isError(successCode)

        // Then
        assertThat(isError).isFalse()
    }

    @Test
    fun isError_withPositiveValue_returnsFalse() {
        // Given - positive values represent success (e.g., number of bytes written)
        val positiveValue = 100L

        // When
        val isError = Zstd.isError(positiveValue)

        // Then
        assertThat(isError).isFalse()
    }

    @Test
    fun getErrorName_withErrorEnumCode_returnsNonEmptyString() {
        // Given - errMemoryAllocation returns an error enum code
        val errorCode = Zstd.errMemoryAllocation()

        // When - getErrorName works with the enum code directly
        val errorName = Zstd.getErrorName(errorCode)

        // Then
        assertThat(errorName).isNotNull()
        assertThat(errorName).isNotEmpty()
    }
}
