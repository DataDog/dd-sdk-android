/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.debug

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.mockDevLogHandler
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isA
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
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
    ExtendWith(ForgeExtension::class),
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UiRumDebugListenerTest {

    @Mock
    lateinit var mockRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDecorView: ViewGroup

    @Mock
    lateinit var mockContentView: FrameLayout

    private lateinit var mockDevLogHandler: LogHandler

    private lateinit var testedListener: UiRumDebugListener

    @BeforeEach
    fun setUp() {
        testedListener = UiRumDebugListener(
            rumMonitorProvider = { mockRumMonitor }
        )

        mockDevLogHandler = mockDevLogHandler()

        whenever(mockDecorView.findViewById<View>(android.R.id.content)) doReturn mockContentView
        whenever(mockWindow.decorView) doReturn mockDecorView
        whenever(mockActivity.window) doReturn mockWindow
    }

    // region Application.ActivityLifecycleCallbacks

    @Test
    fun `M log a warning W onActivityResumed() { content view is not a FrameLayout }`() {
        // GIVEN
        whenever(mockDecorView.findViewById<View>(android.R.id.content)) doReturn mock()

        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            UiRumDebugListener.CANNOT_FIND_CONTENT_VIEW_MESSAGE
        )

        verifyZeroInteractions(mockRumMonitor, mockContentView)
    }

    @Test
    fun `M log a warning W onActivityResumed() { cannot find content view by id }`() {
        // GIVEN
        whenever(mockDecorView.findViewById<View>(android.R.id.content)) doReturn null

        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            UiRumDebugListener.CANNOT_FIND_CONTENT_VIEW_MESSAGE
        )

        verifyZeroInteractions(mockRumMonitor, mockContentView)
    }

    @Test
    fun `M log a warning W onActivityResumed() { decor view is not ViewGroup }`() {
        // GIVEN
        whenever(mockWindow.decorView) doReturn mock()

        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            UiRumDebugListener.CANNOT_FIND_CONTENT_VIEW_MESSAGE
        )

        verifyZeroInteractions(mockRumMonitor, mockContentView)
    }

    @Test
    fun `M log a warning W onActivityResumed() { RUM monitor is not AdvancedRumMonitor }`() {
        // GIVEN
        testedListener = UiRumDebugListener(
            rumMonitorProvider = { null }
        )

        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockDevLogHandler).handleLog(
            Log.WARN,
            UiRumDebugListener.MISSING_RUM_MONITOR_TYPE.format(
                Locale.US,
                AdvancedRumMonitor::class.qualifiedName
            )
        )

        verifyZeroInteractions(mockContentView)
    }

    @Test
    fun `M add debug container W onActivityResumed()`() {
        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockContentView).addView(isA<LinearLayout>(), isA<FrameLayout.LayoutParams>())

        assertThat(testedListener.rumViewsContainer).isNotNull

        verifyZeroInteractions(mockDevLogHandler)
    }

    @Test
    fun `M register listener W onActivityResumed()`() {
        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockRumMonitor).setDebugListener(testedListener)
        verifyZeroInteractions(mockDevLogHandler)
    }

    @Test
    fun `M unregister listener W onActivityPaused()`() {
        // GIVEN
        testedListener.rumMonitor = mockRumMonitor

        // WHEN
        testedListener.onActivityPaused(mockActivity)

        // THEN
        verify(mockRumMonitor).setDebugListener(null)
        assertThat(testedListener.rumMonitor).isNull()
    }

    @Test
    fun `M remove debug container W onActivityPaused()`() {
        // GIVEN
        val container = mock<LinearLayout>()
        testedListener.rumViewsContainer = container

        // WHEN
        testedListener.onActivityPaused(mockActivity)

        // THEN
        verify(mockContentView).removeView(container)
        assertThat(testedListener.rumViewsContainer).isNull()
    }

    @Test
    fun `M not do any interaction W method is not in { resume - pause }`() {
        // WHEN
        testedListener.onActivityCreated(mockActivity, mock())
        testedListener.onActivityDestroyed(mockActivity)
        testedListener.onActivityStarted(mockActivity)
        testedListener.onActivityStopped(mockActivity)

        // THEN
        verifyZeroInteractions(mockActivity, mockDevLogHandler, mockRumMonitor)
    }

    // endregion

    // region RumDebugListener

    @Test
    fun `M add active RUM views to container W onReceiveActiveRumViews()`(forge: Forge) {
        // GIVEN
        val rumViews = forge.aList {
            forge.anAlphaNumericalString()
        }

        val container = mock<LinearLayout>().apply {
            val mockDisplayMetrics = mock<DisplayMetrics>()
            val mockContext = mock<Context>()
            val mockResources = mock<Resources>()

            whenever(mockResources.displayMetrics) doReturn mockDisplayMetrics
            whenever(mockContext.resources) doReturn mockResources
            whenever(context) doReturn mockContext
        }
        testedListener.rumViewsContainer = container

        whenever(container.post(any())) doAnswer {
            it.getArgument(0, Runnable::class.java).run()
            true
        }

        // WHEN
        testedListener.onReceiveActiveRumViews(rumViews)

        // THEN
        inOrder(container) {
            verify(container).removeAllViews()
            verify(container, times(rumViews.size)).addView(isA<TextView>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M add RUM views to container only if changed W onReceiveActiveRumViews()`(forge: Forge) {
        // GIVEN
        val viewsOne = listOf(forge.anAlphaNumericalString())
        val viewsTwo = listOf(forge.anAlphaNumericalString())
        val viewsThree = listOf(viewsOne[0], viewsTwo[0])
        val viewsFour = listOf(viewsTwo[0], viewsOne[0])
        val viewsFive = listOf(forge.anAlphaNumericalString())

        val container = mock<LinearLayout>().apply {
            val mockDisplayMetrics = mock<DisplayMetrics>()
            val mockContext = mock<Context>()
            val mockResources = mock<Resources>()

            whenever(mockResources.displayMetrics) doReturn mockDisplayMetrics
            whenever(mockContext.resources) doReturn mockResources
            whenever(context) doReturn mockContext
        }
        testedListener.rumViewsContainer = container

        whenever(container.post(any())) doAnswer {
            it.getArgument(0, Runnable::class.java).run()
            true
        }

        // WHEN
        testedListener.onReceiveActiveRumViews(viewsOne)
        testedListener.onReceiveActiveRumViews(viewsOne)
        testedListener.onReceiveActiveRumViews(viewsTwo)
        testedListener.onReceiveActiveRumViews(viewsTwo)
        testedListener.onReceiveActiveRumViews(viewsThree)
        testedListener.onReceiveActiveRumViews(viewsThree)
        testedListener.onReceiveActiveRumViews(viewsFour)
        testedListener.onReceiveActiveRumViews(viewsFour)
        testedListener.onReceiveActiveRumViews(viewsFive)

        // THEN
        verify(container, times(5)).removeAllViews()
        verify(
            container,
            times(
                viewsOne.size +
                    viewsTwo.size +
                    viewsThree.size +
                    viewsFour.size +
                    viewsFive.size
            )
        ).addView(isA<TextView>())
    }

    @Test
    fun `M add missing RUM view text to container W onReceiveActiveRumViews()`() {
        // GIVEN
        val container = mock<LinearLayout>().apply {
            val mockDisplayMetrics = mock<DisplayMetrics>()
            val mockContext = mock<Context>()
            val mockResources = mock<Resources>()

            whenever(mockResources.displayMetrics) doReturn mockDisplayMetrics
            whenever(mockContext.resources) doReturn mockResources
            whenever(context) doReturn mockContext
        }
        testedListener.rumViewsContainer = container

        whenever(container.post(any())) doAnswer {
            it.getArgument(0, Runnable::class.java).run()
            true
        }

        // WHEN
        testedListener.onReceiveActiveRumViews(emptyList())

        // THEN
        inOrder(container) {
            verify(container).removeAllViews()
            verify(container).addView(isA<TextView>())
            verifyNoMoreInteractions()
        }
    }

    // endregion
}
