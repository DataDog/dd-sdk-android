/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.timeseries.collector

import android.os.Handler
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class DebouncerTest {

    private lateinit var testedDebouncer: Debouncer

    @Mock
    lateinit var mockHandler: Handler

    private val mockCall: () -> Unit = mock()

    @BeforeEach
    fun `set up`() {
        whenever(mockHandler.postDelayed(any(), any())) doReturn true
        testedDebouncer = Debouncer(
            handler = mockHandler
        )
    }

    private fun captureScheduledRunnable(): Runnable {
        val captor = argumentCaptor<Runnable>()
        verify(mockHandler).postDelayed(captor.capture(), eq(100L))
        return captor.lastValue
    }

    @Test
    fun `M post via handler W runDelayed()`() {
        // When
        testedDebouncer.runDelayed(100L) { }

        // Then
        verify(mockHandler).postDelayed(any<Runnable>(), eq(100L))
    }

    @Test
    fun `M invoke call W scheduled runnable fires`() {
        // Given
        testedDebouncer.runDelayed(100L, call = mockCall)
        val runnable = captureScheduledRunnable()

        // When
        runnable.run()

        // Then
        verify(mockCall).invoke()
    }

    @Test
    fun `M schedule only once W runDelayed() { called twice before it fires }`() {
        // Given
        val mockFirstCall: () -> Unit = mock()
        val mockSecondCall: () -> Unit = mock()
        testedDebouncer.runDelayed(100L, call = mockFirstCall)

        // When
        testedDebouncer.runDelayed(100L, call = mockSecondCall)

        // Then
        verify(mockHandler, times(1)).postDelayed(any<Runnable>(), eq(100L))
        captureScheduledRunnable().run()
        verify(mockFirstCall).invoke()
        verifyNoMoreInteractions(mockSecondCall)
    }

    @Test
    fun `M not invoke call W scheduled runnable fires { cancelled before it fires }`() {
        // Given
        testedDebouncer.runDelayed(100L, call = mockCall)
        val runnable = captureScheduledRunnable()
        testedDebouncer.cancel()

        // When
        runnable.run()

        // Then
        verifyNoMoreInteractions(mockCall)
        verify(mockHandler).removeCallbacks(runnable)
    }

    @Test
    fun `M allow scheduling again W runDelayed() { called after cancel() }`() {
        // Given
        testedDebouncer.runDelayed(100L) { }
        testedDebouncer.cancel()

        // When
        testedDebouncer.runDelayed(100L) { }

        // Then
        verify(mockHandler, times(2)).postDelayed(any<Runnable>(), eq(100L))
    }

    @Test
    fun `M allow scheduling again W runDelayed() { called after it already fired }`() {
        // Given
        testedDebouncer.runDelayed(100L, call = mockCall)
        captureScheduledRunnable().run()

        // When
        testedDebouncer.runDelayed(100L, call = mockCall)

        // Then
        verify(mockHandler, times(2)).postDelayed(any<Runnable>(), eq(100L))
    }

    @Test
    fun `M do nothing W cancel() { nothing scheduled }`() {
        // When / Then
        assertDoesNotThrow { testedDebouncer.cancel() }
    }
}
