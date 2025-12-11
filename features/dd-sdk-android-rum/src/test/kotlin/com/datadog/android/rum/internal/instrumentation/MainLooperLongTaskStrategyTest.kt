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
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
import com.datadog.tools.unit.stub.StubTimeProvider
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

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

    lateinit var stubTimeProvider: StubTimeProvider

    @LongForgery(min = 0L)
    var fakeElapsedTimeNs = 0L

    @BeforeEach
    fun `set up`() {
        Looper::class.java.setStaticValue("sMainLooper", mockMainLooper)

        stubTimeProvider = StubTimeProvider(elapsedTimeNs = fakeElapsedTimeNs)
        whenever(rumMonitor.mockSdkCore.timeProvider) doReturn stubTimeProvider

        testedPrinter = MainLooperLongTaskStrategy(TEST_THRESHOLD_MS)
        testedPrinter.register(rumMonitor.mockSdkCore, mock())
    }

    @AfterEach
    fun `tear down`() {
        MainLooperLongTaskStrategy.CompositePrinter.registeredPrinters.clear()
        MainLooperLongTaskStrategy.CompositePrinter.isRegistered.set(false)
        Looper::class.java.setStaticValue("sMainLooper", null)
        Looper::class.java.getStaticValue<Looper, ThreadLocal<Looper>>("sThreadLocal").set(null)
    }

    @Test
    fun `M set composite printer once W register()`() {
        // When
        testedPrinter.register(rumMonitor.mockSdkCore, mock())
        testedPrinter.register(rumMonitor.mockSdkCore, mock())

        // Then
        verify(mockMainLooper).setMessageLogging(isA<MainLooperLongTaskStrategy.CompositePrinter>())
    }

    @Test
    fun `M add printer to composite printer looper W register()`() {
        // When
        testedPrinter.register(rumMonitor.mockSdkCore, mock())

        // Then
        assertThat(MainLooperLongTaskStrategy.CompositePrinter.registeredPrinters).containsOnly(testedPrinter)
    }

    @Test
    fun `M remove printer from composite printer W unregister()`() {
        // Given
        testedPrinter.register(rumMonitor.mockSdkCore, mock())

        // When
        testedPrinter.unregister(mock())

        // Then
        assertThat(MainLooperLongTaskStrategy.CompositePrinter.registeredPrinters).isEmpty()
    }

    @Test
    fun `M report long task W print()`(
        @LongForgery(min = TEST_THRESHOLD_NS + 1) fakeDurationNs: Long,
        @StringForgery target: String,
        @StringForgery callback: String,
        @IntForgery what: Int
    ) {
        // When
        testedPrinter.println(">>>>> Dispatching to $target $callback: $what")
        stubTimeProvider.elapsedTimeNs += fakeDurationNs
        testedPrinter.println("<<<<< Finished to $target $callback")

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .addLongTask(eq(fakeDurationNs), eq("$target $callback: $what"))
    }

    @Test
    fun `M do not report short task W print()`(
        @LongForgery(min = 0, max = TEST_THRESHOLD_NS) fakeDurationNs: Long,
        @StringForgery target: String,
        @StringForgery callback: String,
        @IntForgery what: Int
    ) {
        // When
        testedPrinter.println(">>>>> Dispatching to $target $callback: $what")
        stubTimeProvider.elapsedTimeNs += fakeDurationNs
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
        const val TEST_THRESHOLD_NS = TEST_THRESHOLD_MS * 1_000_000L

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
