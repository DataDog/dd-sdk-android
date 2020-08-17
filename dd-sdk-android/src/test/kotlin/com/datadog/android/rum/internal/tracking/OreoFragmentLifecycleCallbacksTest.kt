/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
import android.app.FragmentManager
import android.os.Build
import android.view.Window
import com.datadog.android.core.internal.utils.resolveViewName
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.NoOpRumMonitor
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.tracking.AcceptAllDefaultFragment
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Suppress("DEPRECATION")
@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class OreoFragmentLifecycleCallbacksTest {

    lateinit var testedLifecycleCallbacks: OreoFragmentLifecycleCallbacks

    @Mock
    lateinit var mockFragment: Fragment

    @Mock
    lateinit var mockActivity: Activity

    @Mock
    lateinit var mockWindow: Window

    @Mock
    lateinit var mockDialog: Dialog

    @Mock
    lateinit var mockFragmentManager: FragmentManager

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @Mock
    lateinit var mockViewLoadingTimer: ViewLoadingTimer

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockAdvancedRumMonitor: AdvancedRumMonitor

    lateinit var fakeAttributes: Map<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        RumFeature.gesturesTracker = mockGesturesTracker

        whenever(mockActivity.fragmentManager).thenReturn(mockFragmentManager)
        whenever(mockActivity.window).thenReturn(mockWindow)

        fakeAttributes = forge.aMap { forge.aString() to forge.aString() }
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            AcceptAllDefaultFragment(),
            viewLoadingTimer = mockViewLoadingTimer,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor
        )
    }

    @AfterEach
    fun `tear down`() {
        GlobalRum.isRegistered.set(false)
        GlobalRum.monitor = NoOpRumMonitor()
    }

    @Test
    fun `when fragment attached, it will notify the timer`(
        forge: Forge
    ) {
        testedLifecycleCallbacks.onFragmentAttached(mock(), mockFragment, mockActivity)

        verify(mockViewLoadingTimer).onCreated(mockFragment)
    }

    @Test
    fun `when fragment attached, and not whitelisted will not interact with timer`(
        forge: Forge
    ) {
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }
            },
            viewLoadingTimer = mockViewLoadingTimer,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor
        )
        testedLifecycleCallbacks.onFragmentAttached(mock(), mockFragment, mockActivity)

        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when fragment started, it will notify the timer`(
        forge: Forge
    ) {
        testedLifecycleCallbacks.onFragmentStarted(mock(), mockFragment)

        verify(mockViewLoadingTimer).onStartLoading(mockFragment)
    }

    @Test
    fun `when fragment started, and not whitelisted will not interact with timer`(
        forge: Forge
    ) {
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }
            },
            viewLoadingTimer = mockViewLoadingTimer,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor
        )
        testedLifecycleCallbacks.onFragmentStarted(mock(), mockFragment)

        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when fragment activity created on DialogFragment, it will register a Window Callback`(
        forge: Forge
    ) {
        val mockDialogFragment: DialogFragment = mock()
        whenever(mockDialogFragment.context) doReturn mockActivity
        whenever(mockDialogFragment.dialog) doReturn mockDialog
        whenever(mockDialog.window) doReturn mockWindow

        testedLifecycleCallbacks.onFragmentActivityCreated(mock(), mockDialogFragment, null)

        verify(mockGesturesTracker).startTracking(mockWindow, mockActivity)
    }

    @Test
    fun `when fragment activity created on Fragment, registers nothing`(forge: Forge) {
        whenever(mockFragment.context) doReturn mockActivity

        testedLifecycleCallbacks.onFragmentActivityCreated(mock(), mockFragment, null)

        verifyZeroInteractions(mockGesturesTracker)
    }

    @Test
    fun `when fragment resumed it will start a view event`(forge: Forge) {
        // When
        testedLifecycleCallbacks.onFragmentResumed(mock(), mockFragment)
        // Then
        verify(mockRumMonitor).startView(
            eq(mockFragment),
            eq(mockFragment.resolveViewName()),
            eq(fakeAttributes)
        )
    }

    @Test
    fun `when fragment resumed, it will notify the timer and update the Rum event time`(
        forge: Forge
    ) {
        val expectedLoadingTime = forge.aLong()
        val firsTimeLoading = forge.aBool()
        val expectedLoadingType =
            if (firsTimeLoading) {
                ViewEvent.LoadingType.FRAGMENT_DISPLAY
            } else {
                ViewEvent.LoadingType.FRAGMENT_REDISPLAY
            }
        whenever(mockViewLoadingTimer.getLoadingTime(mockFragment))
            .thenReturn(expectedLoadingTime)
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockFragment))
            .thenReturn(firsTimeLoading)
        testedLifecycleCallbacks.onFragmentResumed(mock(), mockFragment)

        verify(mockViewLoadingTimer).onFinishedLoading(mockFragment)
        verify(mockAdvancedRumMonitor).updateViewLoadingTime(
            mockFragment,
            expectedLoadingTime,
            expectedLoadingType
        )
    }

    @Test
    fun `when fragment resumed will do nothing if the fragment is not whitelisted`() {
        // Given
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }
            },
            viewLoadingTimer = mockViewLoadingTimer,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor
        )

        // When
        testedLifecycleCallbacks.onFragmentResumed(mock(), mockFragment)

        // Then
        verifyZeroInteractions(mockRumMonitor)
        verifyZeroInteractions(mockAdvancedRumMonitor)
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when fragment paused it will mark the view as hidden in the timer`(forge: Forge) {
        // When
        testedLifecycleCallbacks.onFragmentPaused(mock(), mockFragment)
        // Then
        verify(mockRumMonitor).stopView(
            eq(mockFragment),
            eq(emptyMap())
        )

        verify(mockViewLoadingTimer).onPaused(mockFragment)
    }

    @Test
    fun `when fragment paused it will stop a view event`(forge: Forge) {
        // When
        testedLifecycleCallbacks.onFragmentPaused(mock(), mockFragment)
        // Then
        verify(mockRumMonitor).stopView(
            eq(mockFragment),
            eq(emptyMap())
        )
    }

    @Test
    fun `when fragment paused will do nothing if the fragment is not whitelisted`() {
        // Given
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }
            },
            viewLoadingTimer = mockViewLoadingTimer,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor
        )

        // When
        testedLifecycleCallbacks.onFragmentPaused(mock(), mockFragment)

        // Then
        verifyZeroInteractions(mockRumMonitor)
        verifyZeroInteractions(mockAdvancedRumMonitor)
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `when fragment destroyed will remove view entry from timer`() {
        // When
        testedLifecycleCallbacks.onFragmentDestroyed(mock(), mockFragment)

        // Then
        verify(mockViewLoadingTimer).onDestroyed(mockFragment)
    }

    @Test
    fun `when fragment destroyed and not whitelisted will do nothing`() {
        // Given
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            object : ComponentPredicate<Fragment> {
                override fun accept(component: Fragment): Boolean {
                    return false
                }
            },
            viewLoadingTimer = mockViewLoadingTimer,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor
        )

        // When
        testedLifecycleCallbacks.onFragmentDestroyed(mock(), mockFragment)

        // Then
        verifyZeroInteractions(mockRumMonitor)
        verifyZeroInteractions(mockAdvancedRumMonitor)
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will register the callback to fragment manager on O`() {
        // When
        testedLifecycleCallbacks.register(mockActivity)

        // Then
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            testedLifecycleCallbacks,
            true
        )
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.O)
    fun `it will unregister the callback from fragment manager on O`() {
        // When
        testedLifecycleCallbacks.unregister(mockActivity)

        // Then
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(testedLifecycleCallbacks)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `it will do nothing when calling register on M`() {
        // When
        testedLifecycleCallbacks.register(mockActivity)

        // Then
        verifyZeroInteractions(mockFragmentManager)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.M)
    fun `it will do nothing when calling unregister on M`() {
        // When
        testedLifecycleCallbacks.unregister(mockActivity)

        // Then
        verifyZeroInteractions(mockFragmentManager)
    }
}
