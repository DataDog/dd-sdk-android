/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RandomizedAssignmentRequestRetrySchedulerTest {

    private val sleeps = mutableListOf<Long>()
    private val randomBounds = mutableListOf<Long>()

    @Test
    fun `M apply randomized exponential backoff W awaitRetry()`() {
        // Given
        val scheduler = createScheduler(randomValue = 42L)

        // When
        val firstRetry = scheduler.awaitRetry(attempt = 0, retryAfter = null)
        val fourthRetry = scheduler.awaitRetry(attempt = 3, retryAfter = null)
        val cappedRetry = scheduler.awaitRetry(attempt = 9, retryAfter = null)

        // Then
        assertThat(firstRetry).isTrue()
        assertThat(fourthRetry).isTrue()
        assertThat(cappedRetry).isTrue()
        assertThat(randomBounds).containsExactly(100L, 800L, 30_000L)
        assertThat(sleeps).containsExactly(42L, 42L, 42L)
    }

    @Test
    fun `M add Retry-After delta seconds W awaitRetry()`() {
        // Given
        val scheduler = createScheduler(randomValue = 25L)

        // When
        val shouldRetry = scheduler.awaitRetry(attempt = 0, retryAfter = " 5 ")

        // Then
        assertThat(shouldRetry).isTrue()
        assertThat(sleeps).containsExactly(5_025L)
    }

    @Test
    fun `M add Retry-After HTTP date W awaitRetry()`() {
        // Given
        val scheduler = createScheduler(
            randomValue = 25L,
            currentTimeMillis = REFERENCE_TIME_MS
        )

        // When
        val shouldRetry = scheduler.awaitRetry(
            attempt = 0,
            retryAfter = "Tue, 14 Nov 2023 22:13:40 GMT"
        )

        // Then
        assertThat(shouldRetry).isTrue()
        assertThat(sleeps).containsExactly(20_025L)
    }

    @Test
    fun `M treat past Retry-After date as zero W awaitRetry()`() {
        // Given
        val scheduler = createScheduler(
            randomValue = 25L,
            currentTimeMillis = REFERENCE_TIME_MS
        )

        // When
        val shouldRetry = scheduler.awaitRetry(
            attempt = 0,
            retryAfter = "Tue, 14 Nov 2023 22:13:00 GMT"
        )

        // Then
        assertThat(shouldRetry).isTrue()
        assertThat(sleeps).containsExactly(25L)
    }

    @Test
    fun `M not retry W awaitRetry() { Retry-After delta exceeds limit }`() {
        // Given
        val scheduler = createScheduler(randomValue = 25L)

        // When
        val shouldRetry = scheduler.awaitRetry(attempt = 0, retryAfter = "31")

        // Then
        assertThat(shouldRetry).isFalse()
        assertThat(randomBounds).isEmpty()
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `M not retry W awaitRetry() { Retry-After date exceeds limit }`() {
        // Given
        val scheduler = createScheduler(
            randomValue = 25L,
            currentTimeMillis = REFERENCE_TIME_MS
        )

        // When
        val shouldRetry = scheduler.awaitRetry(
            attempt = 0,
            retryAfter = "Tue, 14 Nov 2023 22:14:00 GMT"
        )

        // Then
        assertThat(shouldRetry).isFalse()
        assertThat(randomBounds).isEmpty()
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `M use backoff W awaitRetry() { Retry-After is invalid }`() {
        // Given
        val scheduler = createScheduler(randomValue = 25L)

        // When
        val results = listOf("", "-1", "1.5", "invalid-date").map {
            scheduler.awaitRetry(attempt = 0, retryAfter = it)
        }

        // Then
        assertThat(results).containsOnly(true)
        assertThat(sleeps).containsExactly(25L, 25L, 25L, 25L)
    }

    @Test
    fun `M not sleep W awaitRetry() { computed delay is zero }`() {
        // Given
        val scheduler = createScheduler(randomValue = 0L)

        // When
        val shouldRetry = scheduler.awaitRetry(attempt = 0, retryAfter = "0")

        // Then
        assertThat(shouldRetry).isTrue()
        assertThat(sleeps).isEmpty()
    }

    private fun createScheduler(
        randomValue: Long,
        currentTimeMillis: Long = REFERENCE_TIME_MS
    ) = RandomizedAssignmentRequestRetryScheduler(
        randomLong = { upperBound ->
            randomBounds += upperBound
            randomValue
        },
        currentTimeMillis = { currentTimeMillis },
        sleeper = { sleeps += it }
    )

    private companion object {
        const val REFERENCE_TIME_MS = 1_700_000_000_000L
    }
}
