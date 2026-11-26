/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import com.datadog.android.tests.elmyr.useCoreFactories
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AutoClose
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.TimeUnit

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(BaseConfigurator::class)
internal class StubSDKCoreTest {

    @AutoClose
    private lateinit var testedSdkCore: StubSDKCore

    @BeforeEach
    fun `set up`(forge: Forge) {
        forge.useCoreFactories()
        testedSdkCore = StubSDKCore(forge)
    }

    @Test
    fun `M advance elapsed realtime in sync with elapsed time W advanceTimeBy()`(
        @LongForgery(min = 1L, max = 100_000_000L) fakeDurationMs: Long
    ) {
        // Given
        val initialElapsedRealtimeNs = testedSdkCore.timeProvider.getDeviceElapsedRealtimeNanos()
        val initialElapsedRealtimeMs = testedSdkCore.timeProvider.getDeviceElapsedRealtimeMillis()

        // When
        testedSdkCore.advanceTimeBy(fakeDurationMs)

        // Then
        assertThat(testedSdkCore.timeProvider.getDeviceElapsedRealtimeNanos())
            .isEqualTo(initialElapsedRealtimeNs + TimeUnit.MILLISECONDS.toNanos(fakeDurationMs))
            .isEqualTo(testedSdkCore.timeProvider.getDeviceElapsedTimeNanos())
        assertThat(testedSdkCore.timeProvider.getDeviceElapsedRealtimeMillis())
            .isEqualTo(initialElapsedRealtimeMs + fakeDurationMs)
    }
}
