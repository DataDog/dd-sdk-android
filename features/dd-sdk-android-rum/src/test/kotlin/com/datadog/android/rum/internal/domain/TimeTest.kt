/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
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

    @Test
    fun `M convert timestamp to Time W asTime()`(
        @LongForgery(1000000000000, 2000000000000) timestamp: Long
    ) {
        val startMs = System.currentTimeMillis()
        val startNs = System.nanoTime()
        val time = timestamp.asTime()
        val endNs = System.nanoTime()
        val endMs = System.currentTimeMillis()

        assertThat(time.timestamp).isEqualTo(timestamp)
        val nanoOffset = time.nanoTime - ((startNs + endNs) / 2)
        val milliOffset = time.timestamp - ((startMs + endMs) / 2)
        assertThat(TimeUnit.NANOSECONDS.toMillis(nanoOffset)).isCloseTo(
            milliOffset,
            offset(2L)
        )
    }
}
