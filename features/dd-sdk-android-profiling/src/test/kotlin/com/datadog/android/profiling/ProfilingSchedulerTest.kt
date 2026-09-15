/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class ProfilingSchedulerTest {

    @Test
    fun `M create an on demand named scheduler W createProfilingScheduler()`() {
        // When
        val testedExecutor = createProfilingScheduler()

        // Then
        check(testedExecutor is ThreadPoolExecutor)
        assertThat(testedExecutor.allowsCoreThreadTimeOut()).isTrue()
        assertThat(testedExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS))
            .isEqualTo(PROFILING_SCHEDULER_KEEP_ALIVE_MS)
        assertThat(testedExecutor.poolSize).isZero()
        testedExecutor.shutdownNow()
    }

    @Test
    fun `M name the thread W createProfilingScheduler() {task submitted}`() {
        // Given
        val testedExecutor = createProfilingScheduler()

        // When
        val threadName = testedExecutor.submit<String> { Thread.currentThread().name }
            .get(5, TimeUnit.SECONDS)

        // Then
        assertThat(threadName).isEqualTo(PROFILING_SCHEDULER_THREAD_NAME)
        testedExecutor.shutdownNow()
    }
}
