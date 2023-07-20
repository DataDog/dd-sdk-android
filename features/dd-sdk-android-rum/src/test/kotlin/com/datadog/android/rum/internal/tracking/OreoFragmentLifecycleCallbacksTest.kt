/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.rum.internal.tracking

import android.app.Activity
import android.app.Dialog
import android.app.DialogFragment
import android.app.Fragment
import android.app.FragmentManager
import android.os.Build
import android.view.Window
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.core.internal.system.BuildSdkVersionProvider
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.rum.internal.instrumentation.gestures.GesturesTracker
import com.datadog.android.rum.internal.monitor.AdvancedRumMonitor
import com.datadog.android.rum.tracking.ComponentPredicate
import com.datadog.android.rum.utils.resolveViewUrl
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Suppress("DEPRECATION")
@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
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
    lateinit var mockUserActionTrackingStrategy: UserActionTrackingStrategy

    @Mock
    lateinit var mockGesturesTracker: GesturesTracker

    @Mock
    lateinit var mockRumMonitor: RumMonitor

    @Mock
    lateinit var mockSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockAdvancedRumMonitor: AdvancedRumMonitor

    @Mock
    lateinit var mockPredicate: ComponentPredicate<Fragment>

    @Mock
    lateinit var mockBuildSdkVersionProvider: BuildSdkVersionProvider

    lateinit var fakeAttributes: Map<String, Any?>

    @BeforeEach
    fun `set up`(forge: Forge) {
        val mockRumFeature = mock<RumFeature>()
        whenever(mockRumFeature.actionTrackingStrategy) doReturn mockUserActionTrackingStrategy
        whenever(mockUserActionTrackingStrategy.getGesturesTracker()) doReturn mockGesturesTracker

        whenever(mockActivity.fragmentManager).thenReturn(mockFragmentManager)
        whenever(mockActivity.window).thenReturn(mockWindow)
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.BASE

        fakeAttributes = forge.aMap { forge.aString() to forge.aString() }
        testedLifecycleCallbacks = OreoFragmentLifecycleCallbacks(
            { fakeAttributes },
            mockPredicate,
            rumMonitor = mockRumMonitor,
            advancedRumMonitor = mockAdvancedRumMonitor,
            buildSdkVersionProvider = mockBuildSdkVersionProvider,
            rumFeature = mockRumFeature
        )
    }

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
            mockFragment.resolveViewUrl(),
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
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `𝕄 start RUM View 𝕎 onFragmentResumed() { first display }`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `𝕄 start RUM View 𝕎 onFragmentResumed() { redisplay }`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn true

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verify(mockRumMonitor).startView(
            mockFragment,
            mockFragment.resolveViewUrl(),
            fakeAttributes
        )
    }

    @Test
    fun `𝕄 stop RUM View 𝕎 onActivityPaused()`() {
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
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 start RUM View 𝕎 onFragmentResumed() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentResumed(mockFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    @Test
    fun `𝕄 stop RUM View 𝕎 onActivityPaused() {activity not tracked}`() {
        // Given
        whenever(mockPredicate.accept(mockFragment)) doReturn false

        // When
        testedLifecycleCallbacks.onFragmentPaused(mockFragmentManager, mockFragment)

        // Then
        verifyNoInteractions(mockRumMonitor)
    }

    // endregion

    @Test
    fun `when fragment activity created on DialogFragment, it will register a Window Callback`() {
        val mockDialogFragment: DialogFragment = mock()
        whenever(mockDialogFragment.context) doReturn mockActivity
        whenever(mockDialogFragment.dialog) doReturn mockDialog
        whenever(mockDialog.window) doReturn mockWindow
        testedLifecycleCallbacks.register(mockActivity, mockSdkCore)

        testedLifecycleCallbacks.onFragmentActivityCreated(mock(), mockDialogFragment, null)

        verify(mockGesturesTracker).startTracking(mockWindow, mockActivity, mockSdkCore)
    }

    @Test
    fun `when fragment activity created on Fragment, registers nothing`() {
        whenever(mockFragment.context) doReturn mockActivity

        testedLifecycleCallbacks.onFragmentActivityCreated(mock(), mockFragment, null)

        verifyNoInteractions(mockGesturesTracker)
    }

    @Test
    fun `Lifecycle ReportFragment host is ignored`() {
        mockFragment = Class.forName("androidx.lifecycle.ReportFragment").newInstance() as Fragment

        testedLifecycleCallbacks.onFragmentActivityCreated(mock(), mockFragment, null)
        testedLifecycleCallbacks.onFragmentAttached(mock(), mockFragment, null)
        testedLifecycleCallbacks.onFragmentStarted(mock(), mockFragment)
        testedLifecycleCallbacks.onFragmentResumed(mock(), mockFragment)
        testedLifecycleCallbacks.onFragmentPaused(mock(), mockFragment)
        testedLifecycleCallbacks.onFragmentDestroyed(mock(), mockFragment)

        verifyNoInteractions(mockGesturesTracker, mockRumMonitor)
    }

    @Test
    fun `it will register the callback to fragment manager on O`() {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.O

        // When
        testedLifecycleCallbacks.register(mockActivity, mockSdkCore)

        // Then
        verify(mockFragmentManager).registerFragmentLifecycleCallbacks(
            testedLifecycleCallbacks,
            true
        )
    }

    @Test
    fun `it will unregister the callback from fragment manager on O`() {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.O

        // When
        testedLifecycleCallbacks.unregister(mockActivity)

        // Then
        verify(mockFragmentManager).unregisterFragmentLifecycleCallbacks(testedLifecycleCallbacks)
    }

    @Test
    fun `it will do nothing when calling register on M`() {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.M

        // When
        testedLifecycleCallbacks.register(mockActivity, mockSdkCore)

        // Then
        verifyNoInteractions(mockFragmentManager)
    }

    @Test
    fun `it will do nothing when calling unregister on M`() {
        // Given
        whenever(mockBuildSdkVersionProvider.version()) doReturn Build.VERSION_CODES.M

        // When
        testedLifecycleCallbacks.unregister(mockActivity)

        // Then
        verifyNoInteractions(mockFragmentManager)
    }
}
