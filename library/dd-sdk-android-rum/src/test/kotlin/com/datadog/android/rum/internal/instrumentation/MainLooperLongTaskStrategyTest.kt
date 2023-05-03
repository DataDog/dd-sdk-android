/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.os.Looper
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.ObjectTest
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.setStaticValue
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness
import java.util.concurrent.TimeUnit

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class MainLooperLongTaskStrategyTest : ObjectTest<MainLooperLongTaskStrategy>() {

    lateinit var testedPrinter: MainLooperLongTaskStrategy

    @Mock
    lateinit var mockMainLooper: Looper

    @BeforeEach
    fun `set up`() {
        Looper::class.java.setStaticValue("sMainLooper", mockMainLooper)

        testedPrinter = MainLooperLongTaskStrategy(TEST_THRESHOLD_MS)
        testedPrinter.register(rumMonitor.mockSdkCore, mock())
    }

    @Test
    fun `M set printer on main looper W register()`() {
        // Given
        val mockLooper = mock<Looper>()
        Looper::class.java.setStaticValue("sMainLooper", mockLooper)

        // When
        testedPrinter.register(rumMonitor.mockSdkCore, mock())

        // Then
        verify(mockLooper).setMessageLogging(testedPrinter)
    }

    @Test
    fun `M unset printer on main looper W unregister()`() {
        // When
        testedPrinter.unregister(mock())

        // Then
        verify(mockMainLooper).setMessageLogging(testedPrinter)
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
            verify(rumMonitor.mockInstance as AdvancedRumMonitor)
                .addLongTask(capture(), eq("$target $callback: $what"))
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
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    override fun createInstance(forge: Forge): MainLooperLongTaskStrategy {
        return MainLooperLongTaskStrategy(forge.aLong(0, 65536L))
    }

    override fun createEqualInstance(
        source: MainLooperLongTaskStrategy,
        forge: Forge
    ): MainLooperLongTaskStrategy {
        return MainLooperLongTaskStrategy(source.thresholdMs)
    }

    override fun createUnequalInstance(
        source: MainLooperLongTaskStrategy,
        forge: Forge
    ): MainLooperLongTaskStrategy {
        return MainLooperLongTaskStrategy(source.thresholdMs + forge.aLong(1, 65536L))
    }

    companion object {
        const val TEST_THRESHOLD_MS = 50L

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
