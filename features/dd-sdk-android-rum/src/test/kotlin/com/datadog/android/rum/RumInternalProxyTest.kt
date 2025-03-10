/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import android.app.Activity
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumInternalProxyTest {

    @Test
    fun `M proxy addLongTask to RumMonitor W addLongTask()`(
        @LongForgery time: Long,
        @StringForgery target: String
    ) {
        // Given
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor)

        // When
        proxy.addLongTask(time, target)

        // Then
        verify(mockRumMonitor).addLongTask(time, target)
    }

    @Test
    fun `M proxy updatePerformanceMetric to RumMonitor W updatePerformanceMetric()`(
        forge: Forge
    ) {
        // Given
        val metric = forge.aValueFrom(RumPerformanceMetric::class.java)
        val value = forge.aDouble()
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor)

        // When
        proxy.updatePerformanceMetric(metric, value)

        // Then
        verify(mockRumMonitor).updatePerformanceMetric(metric, value)
    }

    @Test
    fun `M proxy enableJankStatsTracking to RumMonitor W enableJankStatsTracking()`() {
        // Given
        val mockRumMonitor = mock(AdvancedRumMonitor::class.java)
        val proxy = _RumInternalProxy(mockRumMonitor)
        val activity: Activity = mock()

        // When
        proxy.enableJankStatsTracking(activity)

        // Then
        verify(mockRumMonitor).enableJankStatsTracking(activity)
    }
}
