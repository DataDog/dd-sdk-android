/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.collections

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class EvictingQueueTest {

    @Test
    fun `M shrink items W add {more than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        repeat(5) { queue.add(it) }

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(2, 3, 4))
    }

    @Test
    fun `M shrink items W offer {more than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        repeat(5) { queue.offer(it) }

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(2, 3, 4))
    }

    @Test
    fun `M not shrink items W add {less than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        repeat(2) { queue.add(it) }

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(0, 1))
    }

    @Test
    fun `M not shrink items W offer {less than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        repeat(2) { queue.offer(it) }

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(0, 1))
    }

    @Test
    fun `M not shrink items W addAll {less than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        queue.addAll(listOf(1, 2))

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(1, 2))
    }

    @Test
    fun `M not shrink items W addAll {equal max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        queue.addAll(listOf(1, 2))

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(1, 2))
    }

    @Test
    fun `M shrink items W addAll {more than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        queue.addAll(listOf(1, 2, 3, 4))

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(2, 3, 4))
    }

    @Test
    fun `M shrink items W addAll {more than max, less than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        queue.addAll(listOf(1, 2, 3, 4))
        queue.addAll(listOf(5, 6))

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(4, 5, 6))
    }

    @Test
    fun `M shrink items W addAll {more than max, more than max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        queue.addAll(listOf(1, 2, 3, 4))
        queue.addAll(listOf(5, 6, 7, 8))

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(6, 7, 8))
    }

    @Test
    fun `M shrink items W addAll {equal max, equal max}`() {
        // Given
        val queue = EvictingQueue<Int>(3)

        // When
        queue.addAll(listOf(1, 2, 3))
        queue.addAll(listOf(4, 5, 6))

        // Then
        assertThat(queue.toList()).isEqualTo(listOf(4, 5, 6))
    }

    @Test
    fun `M create empty queue W maxSize le 0`() {
        // When
        val queue = EvictingQueue<Int>(-1)

        // Then
        assertThat(queue.size).isEqualTo(0)
    }

    @Test
    fun `M not change 0-sized queue W add`() {
        // Given
        val queue = EvictingQueue<Int>(0)

        // When
        assertDoesNotThrow { queue.add(1) }

        // Then
        assertThat(queue.size).isEqualTo(0)
    }

    @Test
    fun `M not change 0-sized queue W offer`() {
        // Given
        val queue = EvictingQueue<Int>(0)

        // When
        assertDoesNotThrow { queue.offer(1) }

        // Then
        assertThat(queue.size).isEqualTo(0)
    }
}
