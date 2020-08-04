/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TimeTest {

    @Test
    fun `creates Time with current millis and nanos`() {

        val startMs = System.currentTimeMillis()
        val startNs = System.nanoTime()

        val time = Time()

        val endNs = System.nanoTime()
        val endMs = System.currentTimeMillis()

        assertThat(time.timestamp).isBetween(startMs, endMs)
        assertThat(time.nanoTime).isBetween(startNs, endNs)
    }
}
