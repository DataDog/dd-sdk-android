/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.thread

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.configuration.BackPressureMitigation
import com.datadog.android.internal.time.TimeProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

@Tag("FormalMethods")
internal class FormalMethodsBackPressureDiagnosticsTest {

    @Test
    fun `known counterexample - ignored newest task is reported as accepted`() {
        val dropped = mutableListOf<Runnable>()
        val queue = BackPressuredBlockingQueue<Runnable>(
            logger = mock<InternalLogger>(),
            executorContext = "formal-methods",
            notifyThreshold = 1,
            capacity = 1,
            onThresholdReached = {},
            onItemDropped = { dropped += it as Runnable },
            backpressureMitigation = BackPressureMitigation.IGNORE_NEWEST,
            timeProvider = mock<TimeProvider>()
        )
        val first = Runnable {}
        val ignored = Runnable {}

        assertThat(queue.offer(first)).isTrue()
        val accepted = queue.offer(ignored)

        assertThat(accepted).isTrue()
        assertThat(queue).containsExactly(first)
        assertThat(dropped).containsExactly(ignored)
    }
}
