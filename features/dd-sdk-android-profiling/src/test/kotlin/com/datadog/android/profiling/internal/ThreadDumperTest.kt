/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.profiling.forge.Configurator
import com.datadog.android.profiling.internal.utils.ThreadDumper
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ThreadDumperTest {

    @Mock
    private lateinit var mockInternalLogger: InternalLogger

    @Test
    fun `M return ANR thread stack and other threads W dump()`(
        forge: Forge,
        @LongForgery fakeDetectedAtMs: Long
    ) {
        // Given
        val anrThread = Thread.currentThread()
        val otherThread = Thread("worker")
        val otherStack = forge.aList { getForgery<StackTraceElement>() }.toTypedArray()
        val ignoredStack = forge.aList { getForgery<StackTraceElement>() }.toTypedArray()
        val testedDumper = ThreadDumper(
            mainThreadProvider = { Thread.currentThread() },
            allStackTracesProvider = {
                mapOf(
                    anrThread to ignoredStack,
                    otherThread to otherStack
                )
            }
        ).apply { internalLogger = mockInternalLogger }

        // When
        val dump = testedDumper.dump(fakeDetectedAtMs)

        // Then
        assertThat(dump.detectedAtMs).isEqualTo(fakeDetectedAtMs)
        assertThat(dump.anrThreadStack).isNotEmpty()
        assertThat(dump.anrThreadName).isEqualTo(anrThread.name)
        assertThat(dump.anrThreadState).isEqualTo(anrThread.state)
        assertThat(dump.allThreads).hasSize(1)
        assertThat(dump.allThreads.first().name).isEqualTo("worker")
    }

    @Test
    fun `M skip threads with empty stack W dump()`(
        @LongForgery fakeDetectedAtMs: Long
    ) {
        // Given
        val mainThread = Thread.currentThread()
        val idleThread = Thread("idle")
        val testedDumper = ThreadDumper(
            mainThreadProvider = { Thread.currentThread() },
            allStackTracesProvider = {
                mapOf(
                    mainThread to mainThread.stackTrace,
                    idleThread to emptyArray()
                )
            }
        ).apply { internalLogger = mockInternalLogger }

        // When
        val dump = testedDumper.dump(fakeDetectedAtMs)

        // Then
        assertThat(dump.detectedAtMs).isEqualTo(fakeDetectedAtMs)
        assertThat(dump.anrThreadName).isEqualTo(mainThread.name)
        assertThat(dump.anrThreadState).isEqualTo(mainThread.state)
        assertThat(dump.allThreads).isEmpty()
    }

    @Test
    fun `M return empty allThreads and log W getAllStackTraces throws`(
        @LongForgery fakeDetectedAtMs: Long
    ) {
        // Given
        val fakeBoom = RuntimeException("boom")
        val testedDumper = ThreadDumper(
            mainThreadProvider = { Thread.currentThread() },
            allStackTracesProvider = { throw fakeBoom }
        ).apply { internalLogger = mockInternalLogger }

        // When
        val dump = testedDumper.dump(fakeDetectedAtMs)

        // Then
        assertThat(dump.allThreads).isEmpty()
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            eq(fakeBoom),
            eq(false),
            eq(null)
        )
    }
}
