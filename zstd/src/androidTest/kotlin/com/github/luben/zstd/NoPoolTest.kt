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
 * Instrumented tests for [NoPool] and [BufferPool] interface.
 */
@RunWith(AndroidJUnit4::class)
class NoPoolTest {

    @Test
    fun instance_returnsSingletonInstance() {
        // When
        val instance1 = NoPool.INSTANCE
        val instance2 = NoPool.INSTANCE

        // Then
        assertThat(instance1).isSameAs(instance2)
    }

    @Test
    fun instance_implementsBufferPoolInterface() {
        // When
        val instance = NoPool.INSTANCE

        // Then
        assertThat(instance).isInstanceOf(BufferPool::class.java)
    }

    @Test
    fun get_returnsBufferWithRequestedCapacity() {
        // Given
        val capacity = 1024

        // When
        val buffer = NoPool.INSTANCE.get(capacity)

        // Then
        assertThat(buffer).isNotNull()
        assertThat(buffer.capacity()).isGreaterThanOrEqualTo(capacity)
    }

    @Test
    fun get_returnsHeapBuffer() {
        // Given
        val capacity = 256

        // When
        val buffer = NoPool.INSTANCE.get(capacity)

        // Then
        assertThat(buffer.hasArray()).isTrue()
        assertThat(buffer.arrayOffset()).isEqualTo(0)
    }

    @Test
    fun get_calledMultipleTimes_returnsNewBufferEachTime() {
        // Given
        val capacity = 128

        // When
        val buffer1 = NoPool.INSTANCE.get(capacity)
        val buffer2 = NoPool.INSTANCE.get(capacity)

        // Then
        assertThat(buffer1).isNotSameAs(buffer2)
    }

    @Test
    fun release_doesNotThrow() {
        // Given
        val buffer = NoPool.INSTANCE.get(64)

        // When & Then - no exception
        NoPool.INSTANCE.release(buffer)
    }

    @Test
    fun release_calledMultipleTimes_doesNotThrow() {
        // Given
        val buffer = NoPool.INSTANCE.get(64)

        // When & Then - no exception
        NoPool.INSTANCE.release(buffer)
        NoPool.INSTANCE.release(buffer)
        NoPool.INSTANCE.release(buffer)
    }

    @Test
    fun releaseAndGet_doesNotRecycleBuffers() {
        // Given
        val capacity = 512
        val buffer1 = NoPool.INSTANCE.get(capacity)

        // When
        NoPool.INSTANCE.release(buffer1)
        val buffer2 = NoPool.INSTANCE.get(capacity)

        // Then - NoPool does not recycle, so buffers should be different
        assertThat(buffer1).isNotSameAs(buffer2)
    }

    @Test
    fun get_returnsBufferWithPositionAtZero() {
        // Given
        val capacity = 256

        // When
        val buffer = NoPool.INSTANCE.get(capacity)

        // Then
        assertThat(buffer.position()).isEqualTo(0)
    }

    @Test
    fun get_handlesVariousCapacities() {
        // Given
        val capacities = listOf(1, 10, 100, 1000, 10000, 100000)

        // When & Then
        for (capacity in capacities) {
            val buffer = NoPool.INSTANCE.get(capacity)
            assertThat(buffer.capacity()).isGreaterThanOrEqualTo(capacity)
            assertThat(buffer.hasArray()).isTrue()
        }
    }
}
