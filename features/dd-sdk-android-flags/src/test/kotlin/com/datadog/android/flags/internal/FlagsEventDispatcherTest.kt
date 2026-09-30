/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FlagsClientEventHandler
import com.datadog.android.flags.model.FlagsClientEventDetails
import com.datadog.android.flags.model.FlagsClientEventType.CONFIGURATION_CHANGED
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal class FlagsEventDispatcherTest {
    private val testedDispatcher = FlagsEventDispatcher(mock<InternalLogger>())

    @Test
    fun `M drain accepted installs even when completion throws W deferred dispatch`() {
        var calls = 0
        testedDispatcher.addHandler(CONFIGURATION_CHANGED) { calls++ }
        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            testedDispatcher.deferDispatch {
                testedDispatcher.enqueueConfigurationChanged()
                testedDispatcher.drain()
                assertThat(calls).isZero()
                error("completion")
            }
        }
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `M isolate failures and preserve queued order W handler reenters`() {
        val calls = mutableListOf<String>()
        var first = true
        testedDispatcher.addHandler(CONFIGURATION_CHANGED) { error("test") }
        testedDispatcher.addHandler(CONFIGURATION_CHANGED) {
            calls.add("a")
            if (first) {
                first = false
                testedDispatcher.enqueueConfigurationChanged()
                testedDispatcher.drain()
            }
        }
        testedDispatcher.addHandler(CONFIGURATION_CHANGED) { calls.add("b") }

        testedDispatcher.enqueueConfigurationChanged()
        testedDispatcher.drain()

        assertThat(calls).containsExactly("a", "b", "a", "b")
    }

    @Test
    fun `M skip removed registrations without replay W handlers change during dispatch`() {
        val calls = mutableListOf<String>()
        val removed = FlagsClientEventHandler { calls.add("removed") }
        val added = FlagsClientEventHandler { calls.add("added") }
        lateinit var first: FlagsClientEventHandler
        first = FlagsClientEventHandler {
            calls.add("first")
            testedDispatcher.removeHandler(CONFIGURATION_CHANGED, first)
            testedDispatcher.removeHandler(CONFIGURATION_CHANGED, removed)
            testedDispatcher.addHandler(CONFIGURATION_CHANGED, added)
        }
        testedDispatcher.addHandler(CONFIGURATION_CHANGED, first)
        testedDispatcher.addHandler(CONFIGURATION_CHANGED, first)
        testedDispatcher.addHandler(CONFIGURATION_CHANGED, removed)
        testedDispatcher.enqueueConfigurationChanged()
        testedDispatcher.drain()
        assertThat(calls).containsExactly("first")

        testedDispatcher.enqueueConfigurationChanged()
        testedDispatcher.drain()
        assertThat(calls).containsExactly("first", "added")
    }

    @Test
    fun `M retain only commit time subscribers W registration occurs after enqueue`() {
        val received = mutableListOf<FlagsClientEventDetails>()
        testedDispatcher.enqueueConfigurationChanged()
        testedDispatcher.addHandler(CONFIGURATION_CHANGED) { received.add(it) }
        testedDispatcher.drain()
        assertThat(received).isEmpty()
        testedDispatcher.enqueueConfigurationChanged()
        testedDispatcher.drain()
        assertThat(received).hasSize(1)
        with(received.single()) {
            assertThat(providerName).isEqualTo("Datadog Feature Flags Provider")
            assertThat(flagsChanged).isNull()
            assertThat(message).isNull()
            assertThat(errorCode).isNull()
            assertThat(eventMetadata).isEmpty()
        }
    }

    @Test
    fun `M allow concurrent removal and serialize callbacks W another thread dispatches`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = mutableListOf<String>()
        val handler = FlagsClientEventHandler {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            calls.add("called")
        }
        testedDispatcher.addHandler(CONFIGURATION_CHANGED, handler)
        testedDispatcher.enqueueConfigurationChanged()
        val worker = thread { testedDispatcher.drain() }
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            testedDispatcher.enqueueConfigurationChanged()
            testedDispatcher.removeHandler(CONFIGURATION_CHANGED, handler)
            testedDispatcher.drain()
        } finally {
            release.countDown()
            worker.join(5000)
        }
        assertThat(worker.isAlive).isFalse()
        assertThat(calls).containsExactly("called")
    }

    @Test
    fun `M keep subscriptions per client W different dispatcher emits`() {
        val calls = mutableListOf<FlagsClientEventDetails>()
        testedDispatcher.addHandler(CONFIGURATION_CHANGED) { calls.add(it) }
        val other = FlagsEventDispatcher(mock())
        other.enqueueConfigurationChanged()
        other.drain()
        assertThat(calls).isEmpty()
    }
}
