/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class DataUploadSchedulerTest {

    lateinit var testedScheduler: DataUploadScheduler

    @Mock
    lateinit var mockExecutor: ScheduledThreadPoolExecutor

    @BeforeEach
    fun `set up`() {
        testedScheduler = DataUploadScheduler(
            mock(),
            mock(),
            mock(),
            mock(),
            mockExecutor
        )
    }

    @Test
    fun `when start it will schedule a runnable`() {
        // when
        testedScheduler.startScheduling()

        // then
        verify(mockExecutor).schedule(
            any(),
            eq(DataUploadRunnable.DEFAULT_DELAY),
            eq(TimeUnit.MILLISECONDS)
        )
    }

    @Test
    fun `when stop it will try to remove the scheduled runnable`() {
        // given
        testedScheduler.startScheduling()

        // when
        testedScheduler.stopScheduling()

        // then
        val argumentCaptor = argumentCaptor<Runnable>()
        verify(mockExecutor).schedule(
            argumentCaptor.capture(),
            eq(DataUploadRunnable.DEFAULT_DELAY),
            eq(TimeUnit.MILLISECONDS)
        )
        verify(mockExecutor).remove(argumentCaptor.firstValue)
    }
}
