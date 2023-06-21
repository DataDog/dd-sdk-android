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
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.utils.config.GlobalRumMonitorTestConfiguration
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.android.v2.api.InternalLogger
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class UiRumDebugListenerTest {

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDecorView: ViewGroup

    @Mock
    lateinit var mockContentView: FrameLayout

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedListener: UiRumDebugListener

    @BeforeEach
    fun setUp() {
        testedListener = UiRumDebugListener(
            rumMonitor.mockSdkCore,
            rumMonitor.mockInstance as AdvancedRumMonitor
        )

        whenever(rumMonitor.mockSdkCore.internalLogger) doReturn mockInternalLogger
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
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            UiRumDebugListener.CANNOT_FIND_CONTENT_VIEW_MESSAGE
        )

        verifyNoInteractions(rumMonitor.mockInstance, mockContentView)
    }

    @Test
    fun `M log a warning W onActivityResumed() { cannot find content view by id }`() {
        // GIVEN
        whenever(mockDecorView.findViewById<View>(android.R.id.content)) doReturn null

        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            UiRumDebugListener.CANNOT_FIND_CONTENT_VIEW_MESSAGE
        )

        verifyNoInteractions(rumMonitor.mockInstance, mockContentView)
    }

    @Test
    fun `M log a warning W onActivityResumed() { decor view is not ViewGroup }`() {
        // GIVEN
        whenever(mockWindow.decorView) doReturn mock()

        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockInternalLogger).log(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            UiRumDebugListener.CANNOT_FIND_CONTENT_VIEW_MESSAGE
        )

        verifyNoInteractions(rumMonitor.mockInstance, mockContentView)
    }

    @Test
    fun `M add debug container W onActivityResumed()`() {
        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(mockContentView).addView(isA<LinearLayout>(), isA<FrameLayout.LayoutParams>())

        assertThat(testedListener.rumViewsContainer).isNotNull

        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M register listener W onActivityResumed()`() {
        // WHEN
        testedListener.onActivityResumed(mockActivity)

        // THEN
        verify(rumMonitor.mockInstance as AdvancedRumMonitor).setDebugListener(testedListener)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M unregister listener W onActivityPaused()`() {
        // WHEN
        testedListener.onActivityPaused(mockActivity)

        // THEN
        verify(rumMonitor.mockInstance as AdvancedRumMonitor).setDebugListener(null)
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
        verifyNoInteractions(mockActivity, mockInternalLogger, rumMonitor.mockInstance)
    }

    // endregion

    // region RumDebugListener

    @Test
    fun `M add active RUM views to container W onReceiveRumActiveViews()`(forge: Forge) {
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
        testedListener.onReceiveRumActiveViews(rumViews)

        // THEN
        inOrder(container) {
            verify(container).removeAllViews()
            verify(container, times(rumViews.size)).addView(isA<TextView>())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `M add RUM views to container only if changed W onReceiveRumActiveViews()`(forge: Forge) {
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
        testedListener.onReceiveRumActiveViews(viewsOne)
        testedListener.onReceiveRumActiveViews(viewsOne)
        testedListener.onReceiveRumActiveViews(viewsTwo)
        testedListener.onReceiveRumActiveViews(viewsTwo)
        testedListener.onReceiveRumActiveViews(viewsThree)
        testedListener.onReceiveRumActiveViews(viewsThree)
        testedListener.onReceiveRumActiveViews(viewsFour)
        testedListener.onReceiveRumActiveViews(viewsFour)
        testedListener.onReceiveRumActiveViews(viewsFive)

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
    fun `M add missing RUM view text to container W onReceiveRumActiveViews()`() {
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
        testedListener.onReceiveRumActiveViews(emptyList())

        // THEN
        inOrder(container) {
            verify(container).removeAllViews()
            verify(container).addView(isA<TextView>())
            verifyNoMoreInteractions()
        }
    }

    // endregion

    companion object {
        val rumMonitor = GlobalRumMonitorTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(rumMonitor)
        }
    }
}
