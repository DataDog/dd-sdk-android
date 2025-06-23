/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.concurrent

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
class CompletableFutureTest {

    @Test
    fun `M return value W value { future is completed }`(
        @StringForgery fakeValue: String
    ) {
        // Given
        val future = CompletableFuture<String>()
        future.complete(fakeValue)

        // When
        val value = future.value

        // Then
        assertThat(value).isEqualTo(fakeValue)
    }

    @RepeatedTest(10)
    fun `M return value W value { future is completed, different threads }`(
        @StringForgery fakeValue: String
    ) {
        // Given
        val future = CompletableFuture<String>()
        Thread {
            future.complete(fakeValue)
        }.apply {
            start()
            join()
        }

        // When
        val value = future.value

        // Then
        assertThat(value).isEqualTo(fakeValue)
    }

    @Test
    fun `M set value only once W value { multiple complete calls }`(
        @StringForgery fakeValue: String,
        @StringForgery anotherFakeValue: String
    ) {
        // Given
        val future = CompletableFuture<String>()
        future.complete(fakeValue)
        future.complete(anotherFakeValue)

        // When
        val value = future.value

        // Then
        assertThat(value).isEqualTo(fakeValue)
    }

    @Test
    fun `M throw error W value { future is not completed }`() {
        // Given
        val future = CompletableFuture<String>()

        // When + Then
        assertThrows<UninitializedPropertyAccessException> {
            future.value
        }
    }

    @Test
    fun `M return true W isComplete() { future is completed }`(
        @StringForgery fakeValue: String
    ) {
        // Given
        val future = CompletableFuture<String>()
        future.complete(fakeValue)

        // When
        val result = future.isComplete()

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W isComplete() { future is not completed }`() {
        // Given
        val future = CompletableFuture<String>()

        // When
        val result = future.isComplete()

        // Then
        assertThat(result).isFalse()
    }
}
