/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ProhibitLeavingStaticMocksExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ProhibitLeavingStaticMocksIn(GlobalRum::class)
internal class GlobalRumTest {

    @Mock
    lateinit var mockNoOpRumMonitor: NoOpRumMonitor

    @Mock
    lateinit var mockAdvancedRumMonitor: AdvancedRumMonitor

    @AfterEach
    fun tearDown() {
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `M delegate to monitor W notifyIngestedWebViewEvent(){ AdvancedRumMonitor }`() {
        // Given
        GlobalRum.monitor = mockAdvancedRumMonitor

        // When
        GlobalRum.notifyIngestedWebViewEvent()

        // Then
        verify(mockAdvancedRumMonitor).sendWebViewEvent()
    }

    @Test
    fun `M do nothing W notifyIngestedWebViewEvent(){ NoOpMonitor }`() {
        // Given
        GlobalRum.monitor = mockNoOpRumMonitor

        // When
        GlobalRum.notifyIngestedWebViewEvent()

        // Then
        verifyNoInteractions(mockNoOpRumMonitor)
    }

    @Test
    fun `M delegate to monitor W notifyInterceptorInstantiated(){ AdvancedRumMonitor }`() {
        // Given
        GlobalRum.monitor = mockAdvancedRumMonitor

        // When
        GlobalRum.notifyInterceptorInstantiated()

        // Then
        verify(mockAdvancedRumMonitor).notifyInterceptorInstantiated()
    }

    @Test
    fun `M do nothing W notifyInterceptorInstantiated(){ NoOpMonitor }`() {
        // Given
        GlobalRum.monitor = mockNoOpRumMonitor

        // When
        GlobalRum.notifyInterceptorInstantiated()

        // Then
        verifyNoInteractions(mockNoOpRumMonitor)
    }
}
