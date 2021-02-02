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
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
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

    @Mock
    lateinit var mockPredicate: ComponentPredicate<Fragment>

    lateinit var fakeAttributes: Map<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        RumFeature.gesturesTracker = mockGesturesTracker

        whenever(mockActivity.fragmentManager).thenReturn(mockFragmentManager)
        whenever(mockActivity.window).thenReturn(mockWindow)

        fakeAttributes = forge.aMap { forge.aString() to forge.aString() }
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            mockPredicate,
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

    // region Track View Loading Time

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onFragmentAttached()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentAttached(
            mockFragmentManager,
            mockFragment,
            mockActivity
        )

        // Then
        verify(mockViewLoadingTimer).onCreated(mockFragment)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onFragmentStarted()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentStarted(mockFragmentManager, mockFragment)

        // Then
        verify(mockViewLoadingTimer).onStartLoading(mockFragment)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onFragmentResumed()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockViewLoadingTimer).onFinishedLoading(mockFragment)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityPaused()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)

        // Then
        verify(mockViewLoadingTimer).onPaused(mockFragment)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityDestroyed()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentDestroyed(mockFragmentManager, mockFragment)

        // Then
        verify(mockViewLoadingTimer).onDestroyed(mockFragment)
    }

    // endregion

    // region Track View Loading Time (not tracked)

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onFragmentAttached() {fragment not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentAttached(
            mockFragmentManager,
            mockFragment,
            mockActivity
        )

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onFragmentStarted() {fragment not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentStarted(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onFragmentResumed() {fragment not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityPaused() {fragment not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 notify viewLoadingTimer 𝕎 onActivityDestroyed() {fragment not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentDestroyed(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockViewLoadingTimer)
    }

    // endregion

    // region Track RUM View

    @Test
    fun `𝕄 start a RUM View event 𝕎 onFragmentResumed()`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewName(),
            fakeAttributes
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onFragmentResumed() {custom view name}`(
        @StringForgery fakeName: String
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true
        whenever(mockPredicate.getViewName(mockFragment)) doReturn fakeName

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            fakeName,
            fakeAttributes
        )
    }

    @Test
    fun `𝕄 start a RUM View event 𝕎 onFragmentResumed() {custom blank view name}`(
        @StringForgery(StringForgeryType.WHITESPACE) fakeName: String
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true
        whenever(mockPredicate.getViewName(mockFragment)) doReturn fakeName

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewName(),
            fakeAttributes
        )
    }

    @Test
    fun `𝕄 start RUM View and update loading time 𝕎 onFragmentResumed()`(
        @BoolForgery firstTimeLoading: Boolean,
        @LongForgery(1L) loadingTime: Long
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true
        val expectedLoadingType = if (firstTimeLoading) {
            ViewEvent.LoadingType.FRAGMENT_DISPLAY
        } else {
            ViewEvent.LoadingType.FRAGMENT_REDISPLAY
        }
        whenever(mockViewLoadingTimer.getLoadingTime(mockFragment)) doReturn loadingTime
        whenever(mockViewLoadingTimer.isFirstTimeLoading(mockFragment)) doReturn firstTimeLoading

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        inOrder(mockRumMonitor, mockAdvancedRumMonitor, mockViewLoadingTimer) {
            verify(mockViewLoadingTimer).onFinishedLoading(mockFragment)
            verify(mockRumMonitor).startView(
                mockFragment,
                mockFragment.resolveViewName(),
                fakeAttributes
            )
            verify(mockAdvancedRumMonitor).updateViewLoadingTime(
                mockFragment,
                loadingTime,
                expectedLoadingType
            )
        }
    }

    @Test
    fun `𝕄 stop RUM View 𝕎 onActivityPaused()`(
        @BoolForgery firstTimeLoading: Boolean,
        @LongForgery(1L) loadingTime: Long
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).stopView(mockFragment, emptyMap())
    }

    // endregion

    // region Track RUM View (not tracked)

    @Test
    fun `𝕄 start a RUM View event 𝕎 onFragmentResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 start RUM View and update loadingTime 𝕎 onFragmentResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockViewLoadingTimer)
    }

    @Test
    fun `𝕄 stop RUM View 𝕎 onActivityPaused() {activity not tracked}`(
        @BoolForgery firstTimeLoading: Boolean,
        @LongForgery(1L) loadingTime: Long
    ) {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)

        // Then
        verifyZeroInteractions(mockRumMonitor, mockViewLoadingTimer)
    }

    // endregion

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
