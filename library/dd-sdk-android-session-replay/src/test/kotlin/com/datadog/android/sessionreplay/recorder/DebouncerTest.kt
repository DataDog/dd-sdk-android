/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.os.Handler
import com.datadog.android.sessionreplay.utils.ForgeConfigurator
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DebouncerTest {

    @Mock
    lateinit var mockHandler: Handler

    lateinit var testedDebouncer: Debouncer

    @BeforeEach
    fun `set up`() {
        testedDebouncer = Debouncer(mockHandler, TEST_MAX_DELAY_THRESHOLD_IN_NS)
    }

    @Test
    fun `M delegate to the delayed handler W debounce { first request }`() {
        // Given
        val fakeRunnable = TestRunnable()
        whenever(mockHandler.postDelayed(any(), any())).then {
            (it.arguments[0] as Runnable).run()
            true
        }

        // When
        testedDebouncer.debounce(fakeRunnable)

        // Then
        verify(mockHandler).removeCallbacksAndMessages(null)
        verify(mockHandler).postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_NS))
    }

    @Test
    fun `M skip the handler and execute the runnable in place W debounce { threshold reached }`() {
        // Given
        val fakeRunnable = TestRunnable()
        val fakeSecondRunnable = TestRunnable()
        testedDebouncer.debounce(fakeRunnable)
        Thread.sleep(TimeUnit.NANOSECONDS.toMillis(TEST_MAX_DELAY_THRESHOLD_IN_NS))

        // When
        testedDebouncer.debounce(fakeSecondRunnable)

        // Then
        verify(mockHandler, times(1)).postDelayed(
            any(),
            eq(Debouncer.DEBOUNCE_TIME_IN_NS)
        )
        verify(mockHandler, times(2)).removeCallbacksAndMessages(null)
        assertThat(fakeRunnable.wasExecuted).isFalse
        assertThat(fakeSecondRunnable.wasExecuted).isTrue
    }

    @Test
    fun `M execute the runnable once W debounce { high frequency, delay threshold reached  }`(
        forge: Forge
    ) {
        // Given
        val fakeDelayedRunnables = forge.aList(size = forge.anInt(min = 1, max = 10)) { TestRunnable() }
        val delayInterval = ceil(
            TEST_MAX_DELAY_THRESHOLD_IN_NS.toDouble() /
                fakeDelayedRunnables.size.toDouble()
        ).toLong()
        val fakeExecutedRunnable = TestRunnable()
        fakeDelayedRunnables.forEach {
            testedDebouncer.debounce(it)
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(delayInterval))
        }

        // When
        testedDebouncer.debounce(fakeExecutedRunnable)

        // Then
        verify(mockHandler, times(fakeDelayedRunnables.size))
            .postDelayed(any(), eq(Debouncer.DEBOUNCE_TIME_IN_NS))
        verify(mockHandler, times(fakeDelayedRunnables.size + 1))
            .removeCallbacksAndMessages(null)
        assertThat(fakeDelayedRunnables.filter { it.wasExecuted }.size).isEqualTo(0)
        assertThat(fakeExecutedRunnable.wasExecuted).isTrue
    }

    @Test
    fun `M switch to the handler W debounce { delay threshold reached, more runnables after  }`(
        forge: Forge
    ) {
        // Given
        val fakeDelayedRunnablesPack1 = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            TestRunnable()
        }
        val fakeDelayedRunnablesPack2 = forge.aList(size = forge.anInt(min = 1, max = 10)) {
            TestRunnable()
        }
        val delayInterval = ceil(
            TEST_MAX_DELAY_THRESHOLD_IN_NS.toDouble() /
                fakeDelayedRunnablesPack1.size.toDouble()
        ).toLong()
        val fakeExecutedRunnable = TestRunnable()
        fakeDelayedRunnablesPack1.forEach {
            testedDebouncer.debounce(it)
            Thread.sleep(TimeUnit.NANOSECONDS.toMillis(delayInterval))
        }
        testedDebouncer.debounce(fakeExecutedRunnable)

        // When
        fakeDelayedRunnablesPack2.forEach {
            testedDebouncer.debounce(it)
        }

        // Then
        val numOfDelayedInvocations = fakeDelayedRunnablesPack1.size +
            fakeDelayedRunnablesPack2.size
        verify(mockHandler, times(numOfDelayedInvocations)).postDelayed(
            any(),
            eq(Debouncer.DEBOUNCE_TIME_IN_NS)
        )
        val numOfCancelInvocations = fakeDelayedRunnablesPack1.size +
            fakeDelayedRunnablesPack2.size + 1
        verify(mockHandler, times(numOfCancelInvocations)).removeCallbacksAndMessages(null)
        assertThat(fakeDelayedRunnablesPack1.filter { it.wasExecuted }.size).isEqualTo(0)
        assertThat(fakeDelayedRunnablesPack2.filter { it.wasExecuted }.size).isEqualTo(0)
        assertThat(fakeExecutedRunnable.wasExecuted).isTrue
    }

    private class TestRunnable : Runnable {
        var wasExecuted: Boolean = false

        override fun run() {
            wasExecuted = true
        }
    }

    companion object {
        private val TEST_MAX_DELAY_THRESHOLD_IN_NS = TimeUnit.SECONDS.toNanos(2)
    }
}
