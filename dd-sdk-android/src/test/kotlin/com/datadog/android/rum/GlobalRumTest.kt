/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.tools.unit.annotations.ProhibitLeavingStaticMocksIn
import com.datadog.tools.unit.extensions.ProhibitLeavingStaticMocksExtension
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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
        verifyZeroInteractions(mockNoOpRumMonitor)
    }

    @Test
    fun `M update RUM context W updateRumContext()`() {
        // Given
        val newContext = mock<RumContext>()

        // When
        GlobalRum.updateRumContext(newContext)

        // Then
        assertThat(GlobalRum.getRumContext()).isEqualTo(newContext)
    }

    @Test
    fun `M not update RUM context W updateRumContext() { applyOnlyIf predicate returns false }`() {
        // Given
        val newContext = mock<RumContext>()

        // When
        GlobalRum.updateRumContext(newContext, applyOnlyIf = { false })

        // Then
        assertThat(GlobalRum.getRumContext()).isNotEqualTo(newContext)
    }
}
