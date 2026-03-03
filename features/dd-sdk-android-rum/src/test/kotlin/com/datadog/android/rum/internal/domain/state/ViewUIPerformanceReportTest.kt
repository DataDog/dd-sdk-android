/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.state

import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class ViewUIPerformanceReportTest {

    @Test
    fun `M return thread-safe copy W snapshot()`(
        @Forgery viewUIPerformanceReport: ViewUIPerformanceReport,
        forge: Forge
    ) {
        // Given
        val latch = CountDownLatch(1)
        val keepProducing = AtomicBoolean(true)

        // When
        val supplierThread = Thread {
            latch.countDown()
            while (keepProducing.get()) {
                viewUIPerformanceReport.slowFramesRecords += SlowFrameRecord(
                    startTimestampNs = forge.aPositiveLong(),
                    durationNs = forge.aPositiveLong()
                )
            }
        }.also { it.start() }

        // Then
        assertDoesNotThrow {
            latch.await(1000, TimeUnit.SECONDS)
            try {
                val snapshot = viewUIPerformanceReport.snapshot()
                // fake operation to have iterator and to avoid compiler optimizing it
                val count = snapshot.slowFramesRecords.map { it.startTimestampNs }.count()
                assertThat(count).isNotZero
            } finally {
                keepProducing.set(false)
            }
        }

        supplierThread.join(5000L)
    }
}
