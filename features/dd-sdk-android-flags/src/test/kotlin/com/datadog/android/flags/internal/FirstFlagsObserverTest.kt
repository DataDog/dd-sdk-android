/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.FirstFlagsCallback
import com.datadog.android.flags.FlagsClient
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientEvent
import com.datadog.android.flags.model.FlagsClientEventType
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

internal class FirstFlagsObserverTest {
    private val logger = mock<InternalLogger>()
    private val client = mock<FlagsClient>()

    @Test
    fun `M retain first detached payload W release and client attachment in either order`() {
        listOf(true, false).forEach { attachFirst ->
            val events = mutableListOf<FlagsClientEvent>()
            val tested = FirstFlagsObserver(
                FirstFlagsCallback { actual, event ->
                    assertThat(actual).isSameAs(client)
                    events.add(event)
                },
                logger
            )
            if (attachFirst) tested.attachClient(client)
            val keys = mutableSetOf("first")
            val release = checkNotNull(tested.claim(keys))
            keys.add("later")
            assertThat(tested.claim(setOf("second")) == null).isTrue()
            assertThat(events).isEmpty()
            release()
            if (!attachFirst) {
                assertThat(events).isEmpty()
                tested.attachClient(client)
            }
            release()
            tested.attachClient(client)
            assertThat(events).hasSize(1)
            assertThat(events.single().flagsChanged).containsExactly("first")
            assertThat(events.single().type).isEqualTo(FlagsClientEventType.CONFIGURATION_CHANGED)
            assertThat(events.single().providerName).isEqualTo("Datadog Feature Flags Provider")
            assertThat(events.single().metadata).isEmpty()
            assertThat(events.single().message).isNull()
            assertThat(events.single().errorCode).isNull()
        }
    }

    @Test
    fun `M capture coherent detached bridge snapshot W inputs mutate before delivery`() {
        val attributes = mutableMapOf("key" to "first")
        val context = EvaluationContext("first-user", attributes)
        val extra = JSONObject().put("key", "first")
        val flag = PrecomputedFlag("boolean", "true", false, "a", "v", extra, "STATIC")
        val flags = mutableMapOf("first" to flag)
        var calls = 0
        val tested = FirstFlagsObserver(null, logger) { actualContext, actualFlags ->
            calls++
            assertThat(actualContext.targetingKey).isEqualTo("first-user")
            assertThat(actualContext.attributes).containsEntry("key", "first")
            assertThat(actualFlags.keys).containsExactly("first")
            assertThat(actualFlags.getValue("first").extraLogging.getString("key")).isEqualTo("first")
        }
        val release = checkNotNull(tested.claim(flags.keys) { FirstFlagsObserver.snapshot(context, flags) })
        attributes["key"] = "later"
        extra.put("key", "later")
        flags.clear()
        tested.attachClient(client)
        release()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `M cancel unstarted delivery W disposal before claim release or attachment`() {
        (0..2).forEach { stage ->
            var calls = 0
            val tested = FirstFlagsObserver(FirstFlagsCallback { _, _ -> calls++ }, logger)
            if (stage == 0) tested.dispose()
            val release = tested.claim(emptySet())
            if (stage == 1) tested.dispose()
            release?.invoke()
            if (stage == 2) tested.dispose()
            tested.attachClient(client)
            assertThat(calls).isZero()
        }
    }

    @Test
    fun `M isolate failure and prevent reentrant delivery W callback throws after disposal`() {
        var calls = 0
        lateinit var tested: FirstFlagsObserver
        tested = FirstFlagsObserver(
            FirstFlagsCallback { _, event ->
                calls++
                assertThat(event.flagsChanged).isEmpty()
                tested.dispose()
                assertThat(tested.claim(setOf("reentrant")) == null).isTrue()
                error("customer callback")
            },
            logger
        )
        tested.attachClient(client)
        val release = checkNotNull(tested.claim(emptySet()))
        assertDoesNotThrow { release() }
        release()
        assertThat(calls).isEqualTo(1)
    }

    @Test
    fun `M allow disposal during callback W delivery already started`() {
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val calls = AtomicInteger()
        val tested = FirstFlagsObserver(
            FirstFlagsCallback { _, _ ->
                calls.incrementAndGet()
                entered.countDown()
                check(finish.await(5, TimeUnit.SECONDS))
            },
            logger
        )
        tested.attachClient(client)
        val release = checkNotNull(tested.claim(emptySet()))
        val worker = thread { release() }
        try {
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue()
            tested.dispose()
            assertThat(tested.claim(setOf("late")) == null).isTrue()
        } finally {
            finish.countDown()
            worker.join(5000)
        }
        assertThat(worker.isAlive).isFalse()
        assertThat(calls.get()).isEqualTo(1)
    }
}
