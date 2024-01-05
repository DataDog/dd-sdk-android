/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ConcurrencyExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M execute task W executeSafe()`(
        @StringForgery name: String
    ) {
        // Given
        val service: ExecutorService = mock()
        val runnable: Runnable = mock()
        doNothing().whenever(service).execute(runnable)

        // When
        service.executeSafe(name, mockInternalLogger, runnable)

        // Then
        verify(service).execute(runnable)
    }

    @Test
    fun `M not throw W executeSafe() {rejected exception}`(
        @StringForgery name: String,
        @StringForgery message: String
    ) {
        // Given
        val service: ExecutorService = mock()
        val runnable: Runnable = mock()
        val exception = RejectedExecutionException(message)
        doThrow(exception).whenever(service).execute(runnable)

        // When
        service.executeSafe(name, mockInternalLogger, runnable)

        // Then
        verify(service).execute(runnable)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule $name task on the executor",
            exception
        )
    }

    @Test
    fun `M schedule task W scheduleSafe()`(
        @StringForgery name: String,
        @LongForgery delay: Long,
        @Forgery unit: TimeUnit
    ) {
        // Given
        val service: ScheduledExecutorService = mock()
        val runnable: Runnable = mock()
        val future: ScheduledFuture<*> = mock()
        whenever(service.schedule(runnable, delay, unit)) doReturn future

        // When
        val result: Any? = service.scheduleSafe(name, delay, unit, mockInternalLogger, runnable)

        // Then
        assertThat(result).isSameAs(future)
        verify(service).schedule(runnable, delay, unit)
    }

    @Test
    fun `M not throw W scheduleSafe() {rejected exception}`(
        @StringForgery name: String,
        @LongForgery delay: Long,
        @Forgery unit: TimeUnit,
        @StringForgery message: String
    ) {
        // Given
        val service: ScheduledExecutorService = mock()
        val runnable: Runnable = mock()
        val exception = RejectedExecutionException(message)
        doThrow(exception).whenever(service).schedule(runnable, delay, unit)

        // When
        val result: Any? = service.scheduleSafe(name, delay, unit, mockInternalLogger, runnable)

        // Then
        assertThat(result).isNull()
        verify(service).schedule(runnable, delay, unit)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule $name task on the executor",
            exception
        )
    }

    @Test
    fun `M submit task W submitSafe()`(
        @StringForgery name: String
    ) {
        // Given
        val service: ExecutorService = mock()
        val runnable: Runnable = mock()
        val future: ScheduledFuture<*> = mock()
        whenever(service.submit(runnable)) doReturn future

        // When
        val result: Any? = service.submitSafe(name, mockInternalLogger, runnable)

        // Then
        assertThat(result).isSameAs(future)
        verify(service).submit(runnable)
    }

    @Test
    fun `M not throw W submitSafe() {rejected exception}`(
        @StringForgery name: String,
        @StringForgery message: String
    ) {
        // Given
        val service: ExecutorService = mock()
        val runnable: Runnable = mock()
        val exception = RejectedExecutionException(message)
        doThrow(exception).whenever(service).submit(runnable)

        // When
        val result: Any? = service.submitSafe(name, mockInternalLogger, runnable)

        // Then
        assertThat(result).isNull()
        verify(service).submit(runnable)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY),
            "Unable to schedule $name task on the executor",
            exception
        )
    }
}
