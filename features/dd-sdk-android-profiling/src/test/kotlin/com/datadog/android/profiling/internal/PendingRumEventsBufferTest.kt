/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal

import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.profiling.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatNoException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class PendingRumEventsBufferTest {

    private lateinit var testedBuffer: PendingRumEventsBuffer

    @BeforeEach
    fun `set up`() {
        testedBuffer = PendingRumEventsBuffer()
    }

    // region initial state

    @Test
    fun `M return empty lists W freshly created`() {
        // Then
        assertThat(testedBuffer.pendingLongTasks).isEmpty()
        assertThat(testedBuffer.pendingAnrEvents).isEmpty()
    }

    // endregion

    // region add

    @Test
    fun `M accumulate long task events W add(RumLongTaskEvent) {called multiple times}`(
        @Forgery fakeEvent1: ProfilerEvent.RumLongTaskEvent,
        @Forgery fakeEvent2: ProfilerEvent.RumLongTaskEvent
    ) {
        // When
        testedBuffer.add(fakeEvent1)
        testedBuffer.add(fakeEvent2)

        // Then
        assertThat(testedBuffer.pendingLongTasks).containsExactly(fakeEvent1, fakeEvent2)
    }

    @Test
    fun `M accumulate ANR events W add(RumAnrEvent) {called multiple times}`(
        @Forgery fakeEvent1: ProfilerEvent.RumAnrEvent,
        @Forgery fakeEvent2: ProfilerEvent.RumAnrEvent
    ) {
        // When
        testedBuffer.add(fakeEvent1)
        testedBuffer.add(fakeEvent2)

        // Then
        assertThat(testedBuffer.pendingAnrEvents).containsExactly(fakeEvent1, fakeEvent2)
    }

    @Test
    fun `M not affect ANR events W add(RumLongTaskEvent)`(
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent
    ) {
        // When
        testedBuffer.add(fakeLongTask)

        // Then
        assertThat(testedBuffer.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M not affect long task events W add(RumAnrEvent)`(
        @Forgery fakeAnr: ProfilerEvent.RumAnrEvent
    ) {
        // When
        testedBuffer.add(fakeAnr)

        // Then
        assertThat(testedBuffer.pendingLongTasks).isEmpty()
    }

    // endregion

    // region drain

    @Test
    fun `M snapshot all buffered events W drain()`(
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent,
        @Forgery fakeAnr: ProfilerEvent.RumAnrEvent
    ) {
        // Given
        testedBuffer.add(fakeLongTask)
        testedBuffer.add(fakeAnr)

        // When
        val snapshot = testedBuffer.drain()

        // Then
        assertThat(snapshot.longTasks).containsExactly(fakeLongTask)
        assertThat(snapshot.anrEvents).containsExactly(fakeAnr)
    }

    @Test
    fun `M empty the buffer W drain()`(
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent,
        @Forgery fakeAnr: ProfilerEvent.RumAnrEvent
    ) {
        // Given
        testedBuffer.add(fakeLongTask)
        testedBuffer.add(fakeAnr)

        // When
        testedBuffer.drain()

        // Then
        assertThat(testedBuffer.pendingLongTasks).isEmpty()
        assertThat(testedBuffer.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M return empty snapshot W drain() {buffer was empty}`() {
        // When
        val snapshot = testedBuffer.drain()

        // Then
        assertThat(snapshot.longTasks).isEmpty()
        assertThat(snapshot.anrEvents).isEmpty()
    }

    @Test
    fun `M not include post-drain events in snapshot W drain() {event added after}`(
        @Forgery fakeEarly: ProfilerEvent.RumLongTaskEvent,
        @Forgery fakeLate: ProfilerEvent.RumLongTaskEvent
    ) {
        // Given
        testedBuffer.add(fakeEarly)

        // When
        val snapshot = testedBuffer.drain()
        testedBuffer.add(fakeLate)

        // Then
        assertThat(snapshot.longTasks).containsExactly(fakeEarly)
        assertThat(testedBuffer.pendingLongTasks).containsExactly(fakeLate)
    }

    // endregion

    // region clear

    @Test
    fun `M empty both lists W clear()`(
        @Forgery fakeLongTask: ProfilerEvent.RumLongTaskEvent,
        @Forgery fakeAnr: ProfilerEvent.RumAnrEvent
    ) {
        // Given
        testedBuffer.add(fakeLongTask)
        testedBuffer.add(fakeAnr)

        // When
        testedBuffer.clear()

        // Then
        assertThat(testedBuffer.pendingLongTasks).isEmpty()
        assertThat(testedBuffer.pendingAnrEvents).isEmpty()
    }

    @Test
    fun `M not throw W clear() {buffer already empty}`() {
        // Then
        assertThatNoException().isThrownBy { testedBuffer.clear() }
    }

    // endregion

    // region thread safety

    @Test
    fun `M capture all events W concurrent adds from multiple threads`(forge: Forge) {
        // Given
        val fakeLongTasks = forge.aList(size = 100) { getForgery<ProfilerEvent.RumLongTaskEvent>() }
        val fakeAnrEvents = forge.aList(size = 100) { getForgery<ProfilerEvent.RumAnrEvent>() }
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)

        // When
        thread {
            startLatch.await()
            fakeLongTasks.forEach { testedBuffer.add(it) }
            doneLatch.countDown()
        }
        thread {
            startLatch.await()
            fakeAnrEvents.forEach { testedBuffer.add(it) }
            doneLatch.countDown()
        }
        startLatch.countDown()
        doneLatch.await()

        // Then
        val snapshot = testedBuffer.drain()
        assertThat(snapshot.longTasks).isEqualTo(fakeLongTasks)
        assertThat(snapshot.anrEvents).isEqualTo(fakeAnrEvents)
    }

    @Test
    fun `M not lose events W concurrent add and drain`(forge: Forge) {
        // Given
        val fakeLongTasks = forge.aList(size = 100) { getForgery<ProfilerEvent.RumLongTaskEvent>() }
        val startLatch = CountDownLatch(1)
        val doneLatch = CountDownLatch(2)
        val drainedEvents = mutableListOf<ProfilerEvent.RumLongTaskEvent>()

        // When
        thread {
            startLatch.await()
            fakeLongTasks.forEach { testedBuffer.add(it) }
            doneLatch.countDown()
        }
        thread {
            startLatch.await()
            repeat(10) { drainedEvents += testedBuffer.drain().longTasks }
            doneLatch.countDown()
        }
        startLatch.countDown()
        doneLatch.await()
        drainedEvents += testedBuffer.drain().longTasks

        // Then
        assertThat(drainedEvents).isEqualTo(fakeLongTasks)
    }

    // endregion
}
