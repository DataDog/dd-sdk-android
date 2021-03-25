/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class ViewTreeChangeTrackingStrategyTest {

    lateinit var testedStrategy: ViewTreeChangeTrackingStrategy

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockViewTreeObserver: ViewTreeObserver

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDecorView: View

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @BeforeEach
    fun `set up`() {

        GlobalRum.registerIfAbsent(mockRumMonitor)

        whenever(mockActivity.window) doReturn mockWindow
        whenever(mockWindow.decorView) doReturn mockDecorView
        whenever(mockDecorView.viewTreeObserver) doReturn mockViewTreeObserver

        testedStrategy = ViewTreeChangeTrackingStrategy()
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `𝕄 add listener 𝕎 onActivityStarted()`() {
        // Given

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verify(mockViewTreeObserver).addOnGlobalLayoutListener(testedStrategy)
    }

    @Test
    fun `𝕄 doNothing 𝕎 onActivityStarted() without window`() {
        // Given
        whenever(mockActivity.window) doReturn null

        // When
        testedStrategy.onActivityStarted(mockActivity)

        // Then
        verifyZeroInteractions(mockViewTreeObserver)
    }

    @Test
    fun `𝕄 remove listener 𝕎 onActivityStopped()`() {
        // Given

        // When
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        verify(mockViewTreeObserver).removeOnGlobalLayoutListener(testedStrategy)
    }

    @Test
    fun `𝕄 doNothing 𝕎 onActivityStopped() without window`() {
        // Given
        whenever(mockActivity.window) doReturn null

        // When
        testedStrategy.onActivityStopped(mockActivity)

        // Then
        verifyZeroInteractions(mockViewTreeObserver)
    }

    @Test
    fun `𝕄 send viewTreeChanged event 𝕎 onGlobalLayout()`() {
        // Given

        // When
        val before = Time()
        testedStrategy.onGlobalLayout()
        val after = Time()

        // Then
        argumentCaptor<Time> {
            verify(mockRumMonitor).viewTreeChanged(capture())

            assertThat(firstValue.timestamp).isBetween(before.timestamp, after.timestamp)
            assertThat(firstValue.nanoTime).isBetween(before.nanoTime, after.nanoTime)
        }
    }
}
