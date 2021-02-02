/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.util.Printer
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MainLooperLongTaskStrategyTest {

    lateinit var testedPrinter: Printer

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @BeforeEach
    fun `set up`() {
        testedPrinter = MainLooperLongTaskStrategy(TEST_THRESHOLD_MS)
        GlobalRum.registerIfAbsent(mockRumMonitor)
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
    }

    @Test
    fun `𝕄 report long task 𝕎 print()`(
        @LongForgery(min = TEST_THRESHOLD_MS, max = 500) duration: Long,
        @StringForgery target: String,
        @StringForgery callback: String,
        @IntForgery what: Int
    ) {
        // Given

        // When
        testedPrinter.println(">>>>> Dispatching to $target $callback: $what")
        Thread.sleep(duration + 1)
        testedPrinter.println("<<<<< Finished to $target $callback")

        // Then
        argumentCaptor<Long> {
            verify(mockRumMonitor).addLongTask(capture(), eq("$target $callback: $what"))
            val capturedMs = TimeUnit.NANOSECONDS.toMillis(firstValue)
            assertThat(capturedMs)
                .isCloseTo(duration, offset(10L))
        }
    }

    @Test
    fun `𝕄 do not report short task 𝕎 print()`(
        @LongForgery(min = 0, max = TEST_THRESHOLD_MS) duration: Long,
        @StringForgery target: String,
        @StringForgery callback: String,
        @IntForgery what: Int
    ) {
        // Given

        // When
        testedPrinter.println(">>>>> Dispatching to $target $callback: $what")
        Thread.sleep(duration / 4)
        testedPrinter.println("<<<<< Finished to $target $callback")

        // Then
        verifyZeroInteractions(mockRumMonitor)
    }

    companion object {
        const val TEST_THRESHOLD_MS = 50L
    }
}
