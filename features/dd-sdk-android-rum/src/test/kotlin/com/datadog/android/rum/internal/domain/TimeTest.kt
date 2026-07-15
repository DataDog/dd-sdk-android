/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain

import com.datadog.android.internal.tests.stub.StubTimeProvider
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(Configurator::class)
internal class TimeTest {

    private lateinit var stubTimeProvider: StubTimeProvider

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubTimeProvider = StubTimeProvider(
            deviceTimestampMs = forge.aLong(min = 1000000000000, max = 2000000000000),
            elapsedTimeNs = forge.aLong(min = 1000000000000, max = 2000000000000)
        )
    }

    @Test
    fun `M read current time from provider W now()`() {
        // When
        val time = Time.now(stubTimeProvider)

        // Then
        assertThat(time.timestamp).isEqualTo(stubTimeProvider.deviceTimestampMs)
        assertThat(time.nanoTime).isEqualTo(stubTimeProvider.elapsedTimeNs)
    }

    @Test
    fun `M convert timestamp to Time W fromTimestampMillis()`(
        @LongForgery(1000000000000, 2000000000000) fakeTimestampMs: Long
    ) {
        // Given
        val expectedNanoTime = stubTimeProvider.elapsedTimeNs +
            TimeUnit.MILLISECONDS.toNanos(fakeTimestampMs - stubTimeProvider.deviceTimestampMs)

        // When
        val time = Time.fromTimestampMillis(fakeTimestampMs, stubTimeProvider)

        // Then
        assertThat(time.timestamp).isEqualTo(fakeTimestampMs)
        assertThat(time.nanoTime).isEqualTo(expectedNanoTime)
    }

    @Test
    fun `M convert nanoTime to Time W fromNanoTime()`(
        @LongForgery(1000000000000, 2000000000000) fakeNanoTime: Long
    ) {
        // Given
        val expectedTimestamp = stubTimeProvider.deviceTimestampMs +
            TimeUnit.NANOSECONDS.toMillis(fakeNanoTime - stubTimeProvider.elapsedTimeNs)

        // When
        val time = Time.fromNanoTime(fakeNanoTime, stubTimeProvider)

        // Then
        assertThat(time.nanoTime).isEqualTo(fakeNanoTime)
        assertThat(time.timestamp).isEqualTo(expectedTimestamp)
    }
}
