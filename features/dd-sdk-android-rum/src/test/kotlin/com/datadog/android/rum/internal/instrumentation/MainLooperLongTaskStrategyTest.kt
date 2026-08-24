/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.instrumentation

import android.content.Context
import android.os.Looper
import com.datadog.android.internal.tests.stub.StubTimeProvider
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.ObjectTest
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.datadog.tools.unit.getStaticValue
import com.datadog.tools.unit.setStaticValue
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
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
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
        MainLooperLongTaskStrategy.CompositePrinter.registeredPrinters.toList()
            .forEach { MainLooperLongTaskStrategy.CompositePrinter.removePrinter(it) }
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
    fun `M stop message logging W unregister() {last registered printer}`() {
        // When
        testedPrinter.unregister(mock())

        // Then
        verify(mockMainLooper).setMessageLogging(null)
    }

    @Test
    fun `M keep message logging W unregister() {another printer still registered}`() {
        // Given
        val anotherPrinter = MainLooperLongTaskStrategy(TEST_THRESHOLD_MS).also {
            it.register(rumMonitor.mockSdkCore, mock())
        }

        // When
        testedPrinter.unregister(mock())

        // Then
        verify(mockMainLooper, never()).setMessageLogging(null)
        assertThat(MainLooperLongTaskStrategy.CompositePrinter.registeredPrinters)
            .hasSize(1)
            .allMatch { it === anotherPrinter }
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
            .addLongTask(fakeDurationNs, "$target $callback: $what")
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

    @Test
    fun `M do not report long task W println() {duration exactly at threshold}`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // When
        testedPrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")
        stubTimeProvider.elapsedTimeNs += TEST_THRESHOLD_NS
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M do not report long task W println() {finished without dispatch}`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String
    ) {
        // Given
        stubTimeProvider.elapsedTimeNs = DEVICE_UPTIME_NS

        // When
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M do not report long task W println() {finished without dispatch, negative clock origin}`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String
    ) {
        // Given: System.nanoTime() counts from an arbitrary origin and is documented as possibly
        // negative. Subtracting the NOT_STARTED sentinel from a negative clock overflows into a
        // huge *positive* duration, so the sentinel must be checked before the duration is judged.
        // This test fails if that check is dropped or moved after the threshold comparison.
        stubTimeProvider.elapsedTimeNs = NEGATIVE_CLOCK_ORIGIN_NS

        // When
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M report long task W println() {dispatch starts at zero}`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // Given
        stubTimeProvider.elapsedTimeNs = 0L

        // When
        testedPrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")
        stubTimeProvider.elapsedTimeNs = LONG_TASK_DURATION_NS
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .addLongTask(LONG_TASK_DURATION_NS, "$fakeTarget $fakeCallback: $fakeWhat")
    }

    @Test
    fun `M do not report long task W unregister()+register()+println() {strategy replaced during dispatch}`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // Given
        stubTimeProvider.elapsedTimeNs = DEVICE_UPTIME_NS
        MainLooperLongTaskStrategy.CompositePrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")
        val testedReplacementPrinter = MainLooperLongTaskStrategy(TEST_THRESHOLD_MS)

        // When
        testedPrinter.unregister(mock())
        testedReplacementPrinter.register(rumMonitor.mockSdkCore, mock())
        stubTimeProvider.elapsedTimeNs += LONG_TASK_DURATION_NS
        MainLooperLongTaskStrategy.CompositePrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M clear pending dispatch W unregister()+register()+println()`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // Given
        stubTimeProvider.elapsedTimeNs = DEVICE_UPTIME_NS
        testedPrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")

        // When
        testedPrinter.unregister(mock())
        testedPrinter.register(rumMonitor.mockSdkCore, mock())
        stubTimeProvider.elapsedTimeNs += LONG_TASK_DURATION_NS
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M report long task once W println() {finished twice}`(
        @LongForgery(min = TEST_THRESHOLD_NS + 1) fakeDurationNs: Long,
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // When
        testedPrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")
        stubTimeProvider.elapsedTimeNs += fakeDurationNs
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")
        stubTimeProvider.elapsedTimeNs += SHORT_TASK_DURATION_NS
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .addLongTask(fakeDurationNs, "$fakeTarget $fakeCallback: $fakeWhat")
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M clear state W println() {short task finished, then orphan finished}`(
        @LongForgery(min = 0, max = TEST_THRESHOLD_NS) fakeShortDurationNs: Long,
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // Given: a short task completes (duration <= threshold) — must clear pending state
        testedPrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")
        stubTimeProvider.elapsedTimeNs += fakeShortDurationNs
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // When: an orphan Finished (no new Dispatching) arrives after enough time has
        // passed that reusing the stale startUptimeNs would look like a long task
        stubTimeProvider.elapsedTimeNs += LONG_TASK_DURATION_NS
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verifyNoInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M clear state W println() {long task reported, then orphan finished}`(
        @LongForgery(min = TEST_THRESHOLD_NS + 1) fakeDurationNs: Long,
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // Given: a long task is reported — must clear pending state afterwards
        testedPrinter.println(">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat")
        stubTimeProvider.elapsedTimeNs += fakeDurationNs
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // When: an orphan Finished (no new Dispatching) arrives after more time passes
        stubTimeProvider.elapsedTimeNs += LONG_TASK_DURATION_NS
        testedPrinter.println("<<<<< Finished to $fakeTarget $fakeCallback")

        // Then
        verify(rumMonitor.mockInstance as AdvancedRumMonitor)
            .addLongTask(fakeDurationNs, "$fakeTarget $fakeCallback: $fakeWhat")
        verifyNoMoreInteractions(rumMonitor.mockInstance)
    }

    @Test
    fun `M not crash nor report bogus duration W concurrent println() and unregister()+register()`(
        @StringForgery fakeTarget: String,
        @StringForgery fakeCallback: String,
        @IntForgery fakeWhat: Int
    ) {
        // Given: a device that has been up for a while, so a start timestamp that leaked across a
        // register() would surface as a duration orders of magnitude above the simulated one
        val dispatchMessage = ">>>>> Dispatching to $fakeTarget $fakeCallback: $fakeWhat"
        val finishMessage = "<<<<< Finished to $fakeTarget $fakeCallback"
        val fakeContext = mock<Context>()
        stubTimeProvider.elapsedTimeNs = DEVICE_UPTIME_NS

        val executor = Executors.newFixedThreadPool(2)

        // When
        try {
            val dispatcherFuture = CompletableFuture.runAsync(
                {
                    repeat(RACE_ITERATIONS) {
                        testedPrinter.println(dispatchMessage)
                        stubTimeProvider.elapsedTimeNs += LONG_TASK_DURATION_NS
                        testedPrinter.println(finishMessage)
                    }
                },
                executor
            )
            val lifecycleFuture = CompletableFuture.runAsync(
                {
                    repeat(RACE_ITERATIONS) {
                        testedPrinter.unregister(fakeContext)
                        testedPrinter.register(rumMonitor.mockSdkCore, fakeContext)
                    }
                },
                executor
            )
            CompletableFuture
                .allOf(dispatcherFuture, lifecycleFuture)
                .get(RACE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } finally {
            executor.shutdownNow()
        }

        // Then: both threads are done, so one clean dispatch reports deterministically and keeps
        // the assertion below from passing on an empty capture
        testedPrinter.println(dispatchMessage)
        stubTimeProvider.elapsedTimeNs += LONG_TASK_DURATION_NS
        testedPrinter.println(finishMessage)

        // a register() landing mid-dispatch may legitimately drop a long task, but every task
        // that is reported must carry the duration we actually simulated
        val durationCaptor = argumentCaptor<Long>()
        verify(rumMonitor.mockInstance as AdvancedRumMonitor, atLeastOnce())
            .addLongTask(durationCaptor.capture(), eq("$fakeTarget $fakeCallback: $fakeWhat"))
        assertThat(durationCaptor.allValues).allMatch { it == LONG_TASK_DURATION_NS }
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
        const val LONG_TASK_DURATION_NS = TEST_THRESHOLD_NS + 1L
        val DEVICE_UPTIME_NS = TimeUnit.HOURS.toNanos(2)
        val NEGATIVE_CLOCK_ORIGIN_NS = Long.MIN_VALUE / 2
        val SHORT_TASK_DURATION_NS = TimeUnit.MILLISECONDS.toNanos(1)
        const val RACE_ITERATIONS = 2000

        // generous on purpose: this guards against a hang, it is not a performance assertion
        const val RACE_TIMEOUT_SECONDS = 30L

        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
