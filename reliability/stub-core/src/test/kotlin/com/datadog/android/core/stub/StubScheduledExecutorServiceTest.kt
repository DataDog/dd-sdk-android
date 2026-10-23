/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.tests.elmyr.useCoreFactories
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class StubScheduledExecutorServiceTest {

    private lateinit var testedExecutor: StubScheduledExecutorService

    private var fakeTimestampMillis = 0L

    @StringForgery
    private lateinit var fakeExecutorContext: String

    @BoolForgery
    private var fakeUseCallable = false

    @BeforeEach
    fun `set up`(forge: Forge) {
        forge.useCoreFactories()
        fakeTimestampMillis = forge.aLong(min = 1L, max = 1_000_000L)
        testedExecutor = StubScheduledExecutorService(fakeExecutorContext) { fakeTimestampMillis }
    }

    @Test
    fun `M leave task pending before its deadline W runDueTasks()`() {
        // Given
        var fakeTaskExecutions = 0
        val future = scheduleTask(200L) { fakeTaskExecutions++ }

        // When
        testedExecutor.runDueTasks()
        fakeTimestampMillis += 199L
        testedExecutor.runDueTasks()

        // Then
        assertThat(fakeTaskExecutions).isZero()
        assertThat(future.isDone).isFalse()
    }

    @Test
    fun `M run task once at its deadline W runDueTasks()`() {
        // Given
        var fakeTaskExecutions = 0
        val future = scheduleTask(200L) { fakeTaskExecutions++ }

        // When
        fakeTimestampMillis += 200L
        testedExecutor.runDueTasks()

        // Then
        assertThat(fakeTaskExecutions).isEqualTo(1)
        assertThat(future.isDone).isTrue()

        // When
        fakeTimestampMillis += 200L
        testedExecutor.runDueTasks()

        // Then
        assertThat(fakeTaskExecutions).isEqualTo(1)
    }

    @Test
    fun `M run tasks in deadline and submission order W runDueTasks()`() {
        // Given
        val executionOrder = mutableListOf<String>()
        scheduleTask(200L) { executionOrder.add("last") }
        scheduleTask(100L) { executionOrder.add("first") }
        scheduleTask(100L) { executionOrder.add("second") }

        // When
        fakeTimestampMillis += 200L
        testedExecutor.runDueTasks()

        // Then
        assertThat(executionOrder).containsExactly("first", "second", "last")
    }

    @Test
    fun `M skip cancelled task W runDueTasks()`() {
        // Given
        var fakeTaskExecutions = 0
        val future = scheduleTask(200L) { fakeTaskExecutions++ }
        future.cancel(false)

        // When
        fakeTimestampMillis += 200L
        testedExecutor.runDueTasks()

        // Then
        assertThat(fakeTaskExecutions).isZero()
        assertThat(future.isCancelled).isTrue()
    }

    @Test
    fun `M return callable result with converted delay W runDueTasks()`(@StringForgery fakeResult: String) {
        // Given
        val future = testedExecutor.schedule(Callable { fakeResult }, 1L, TimeUnit.SECONDS)

        // When
        fakeTimestampMillis += 1_000L
        testedExecutor.runDueTasks()

        // Then
        assertThat(future.isDone).isTrue()
        assertThat(future.get(1L, TimeUnit.SECONDS)).isEqualTo(fakeResult)
    }

    @Test
    fun `M run newly scheduled due tasks W runDueTasks()`() {
        // Given
        val executionOrder = mutableListOf<String>()
        testedExecutor.schedule(
            Runnable {
                executionOrder.add("first")
                testedExecutor.schedule(Runnable { executionOrder.add("second") }, 0L, TimeUnit.MILLISECONDS)
            },
            200L,
            TimeUnit.MILLISECONDS
        )

        // When
        fakeTimestampMillis += 200L
        testedExecutor.runDueTasks()

        // Then
        assertThat(executionOrder).containsExactly("first", "second")
    }

    @Test
    fun `M isolate scheduled tasks between SDK cores W advanceTimeBy()`(forge: Forge) {
        // Given
        val testedFirstCore = StubSDKCore(forge)
        val testedSecondCore = StubSDKCore(forge)
        var fakeFirstTaskExecutions = 0
        var fakeSecondTaskExecutions = 0
        testedFirstCore.createScheduledExecutorService(fakeExecutorContext)
            .schedule(Runnable { fakeFirstTaskExecutions++ }, 200L, TimeUnit.MILLISECONDS)
        testedSecondCore.createScheduledExecutorService(fakeExecutorContext)
            .schedule(Runnable { fakeSecondTaskExecutions++ }, 200L, TimeUnit.MILLISECONDS)

        // When
        testedFirstCore.advanceTimeBy(200L)

        // Then
        assertThat(fakeFirstTaskExecutions).isEqualTo(1)
        assertThat(fakeSecondTaskExecutions).isZero()

        // When
        testedSecondCore.advanceTimeBy(200L)

        // Then
        assertThat(fakeSecondTaskExecutions).isEqualTo(1)
    }

    @Test
    fun `M return independent executors W createScheduledExecutorService() {same context}`(forge: Forge) {
        // Given
        val testedCore = StubSDKCore(forge)

        // When
        val firstExecutor = testedCore.createScheduledExecutorService(fakeExecutorContext)
        val secondExecutor = testedCore.createScheduledExecutorService(fakeExecutorContext)

        // Then
        assertThat(firstExecutor).isNotSameAs(secondExecutor)

        // When
        firstExecutor.shutdown()

        // Then
        assertThat(firstExecutor.isShutdown).isTrue()
        assertThat(secondExecutor.isShutdown).isFalse()
    }

    @Test
    fun `M advance every scheduled executor W advanceTimeBy() {same context}`(forge: Forge) {
        // Given
        val testedCore = StubSDKCore(forge)
        val firstExecutor = testedCore.createScheduledExecutorService(fakeExecutorContext)
        val secondExecutor = testedCore.createScheduledExecutorService(fakeExecutorContext)
        var fakeFirstTaskExecutions = 0
        var fakeSecondTaskExecutions = 0
        firstExecutor.schedule(Runnable { fakeFirstTaskExecutions++ }, 200L, TimeUnit.MILLISECONDS)
        secondExecutor.schedule(Runnable { fakeSecondTaskExecutions++ }, 300L, TimeUnit.MILLISECONDS)

        // When
        testedCore.advanceTimeBy(200L)

        // Then
        assertThat(fakeFirstTaskExecutions).isEqualTo(1)
        assertThat(fakeSecondTaskExecutions).isZero()

        // When
        testedCore.advanceTimeBy(100L)

        // Then
        assertThat(fakeFirstTaskExecutions).isEqualTo(1)
        assertThat(fakeSecondTaskExecutions).isEqualTo(1)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M run due work scheduled across executors W advanceTimeBy()`(
        createExecutorDuringExecution: Boolean,
        forge: Forge
    ) {
        // Given
        val testedCore = StubSDKCore(forge)
        val firstExecutor = testedCore.createScheduledExecutorService(fakeExecutorContext)
        val secondExecutor = testedCore.createScheduledExecutorService(fakeExecutorContext)
        var fakeTaskExecutions = 0
        secondExecutor.schedule(
            Runnable {
                val targetExecutor = if (createExecutorDuringExecution) {
                    testedCore.createScheduledExecutorService(fakeExecutorContext)
                } else {
                    firstExecutor
                }
                targetExecutor.schedule(Runnable { fakeTaskExecutions++ }, 0L, TimeUnit.MILLISECONDS)
            },
            200L,
            TimeUnit.MILLISECONDS
        )

        // When
        testedCore.advanceTimeBy(200L)

        // Then
        assertThat(fakeTaskExecutions).isEqualTo(1)
    }

    @Test
    fun `M advance scheduled tasks from different contexts W advanceTimeBy()`(
        forge: Forge,
        @StringForgery fakeOtherContext: String,
        @StringForgery fakeResult: String
    ) {
        // Given
        val testedCore = StubSDKCore(forge)
        val firstFuture = testedCore.createScheduledExecutorService(fakeExecutorContext)
            .schedule(Callable { fakeResult }, 200L, TimeUnit.MILLISECONDS)
        val secondFuture = testedCore.createScheduledExecutorService(fakeOtherContext)
            .schedule(Callable { fakeResult }, 200L, TimeUnit.MILLISECONDS)
        assertThat(firstFuture.isDone).isFalse()
        assertThat(secondFuture.isDone).isFalse()

        // When
        testedCore.advanceTimeBy(200L)

        // Then
        assertThat(firstFuture.isDone).isTrue()
        assertThat(secondFuture.isDone).isTrue()
        assertThat(firstFuture.get(1L, TimeUnit.SECONDS)).isEqualTo(fakeResult)
        assertThat(secondFuture.get(1L, TimeUnit.SECONDS)).isEqualTo(fakeResult)
    }

    private fun scheduleTask(delayMs: Long, action: () -> Unit): ScheduledFuture<*> {
        return if (fakeUseCallable) {
            testedExecutor.schedule(Callable { action() }, delayMs, TimeUnit.MILLISECONDS)
        } else {
            testedExecutor.schedule(Runnable { action() }, delayMs, TimeUnit.MILLISECONDS)
        }
    }
}
