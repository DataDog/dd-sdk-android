/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Application
import android.content.res.Resources
import android.graphics.Point
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import android.view.Window
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.recorder.callback.NoOpWindowCallback
import com.datadog.android.sessionreplay.internal.storage.RecordWriter
import com.datadog.android.sessionreplay.internal.utils.RumContextProvider
import com.datadog.android.sessionreplay.internal.utils.SessionReplayRumContext
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionWindowTouchInterceptorTest {

    @Forgery
    lateinit var fakeRumContext: SessionReplayRumContext

    @Test
    fun `M defer callback wrapping W intercept() { decor unavailable }`() {
        // Given
        val fakeFixture = CallbackFixture(decorAvailable = false)

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // Then
        verify(fakeFixture.mockWindow, never()).callback = any()
    }

    @Test
    fun `M wrap callback W intercept() { previously missing decor becomes available }`() {
        // Given
        val fakeFixture = CallbackFixture(decorAvailable = false)
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.attachRoot()

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // Then
        verify(fakeFixture.mockWindow).callback = isA<CompositionWindowTouchCallback>()
    }

    @Test
    fun `M retain wrapper W intercept() { decor appeared after initial interception }`() {
        // Given
        val fakeFixture = CallbackFixture(decorAvailable = false)
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.attachRoot()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // Then
        verify(fakeFixture.mockWindow).callback = isA<CompositionWindowTouchCallback>()
    }

    @Test
    fun `M apply privacy W dispatchTouchEvent() { decor appeared after initial interception }`() {
        // Given
        val fakeFixture = CallbackFixture(decorAvailable = false)
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.attachRoot()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.publish()
        val mockEvent = fakeFixture.mockDownEvent()

        // When
        fakeFixture.installedCallback().dispatchTouchEvent(mockEvent)

        // Then
        verify(fakeFixture.mockPrivacyManager).shouldRecordTouch(isA(), isA())
    }

    @Test
    fun `M delegate touch W dispatchTouchEvent() { decor appeared after initial interception }`() {
        // Given
        val fakeFixture = CallbackFixture(decorAvailable = false)
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.attachRoot()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.publish()
        val mockEvent = fakeFixture.mockDownEvent()

        // When
        fakeFixture.installedCallback().dispatchTouchEvent(mockEvent)

        // Then
        verify(fakeFixture.mockOriginalCallback).dispatchTouchEvent(mockEvent)
    }

    @Test
    fun `M restore callback W stop() { decor appeared after initial interception }`() {
        // Given
        val fakeFixture = CallbackFixture(decorAvailable = false)
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.attachRoot()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // When
        fakeFixture.testedInterceptor.stop()

        // Then
        verify(fakeFixture.mockWindow).callback = fakeFixture.mockOriginalCallback
    }

    @Test
    fun `M defer privacy checks W dispatchTouchEvent() { window privacy unpublished }`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        val mockEvent = fakeFixture.mockDownEvent()

        // When
        fakeFixture.installedCallback().dispatchTouchEvent(mockEvent)

        // Then
        verifyNoInteractions(fakeFixture.mockPrivacyManager)
    }

    @Test
    fun `M apply published privacy W dispatchTouchEvent() { current RUM scope }`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.publish()
        val mockEvent = fakeFixture.mockDownEvent()

        // When
        fakeFixture.installedCallback().dispatchTouchEvent(mockEvent)

        // Then
        verify(fakeFixture.mockPrivacyManager).shouldRecordTouch(isA(), isA())
    }

    @Test
    fun `M reject previous scope privacy W dispatchTouchEvent() { RUM scope changed }`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.publish()
        whenever(
            fakeFixture.mockRumProvider.getRumContext()
        ).thenReturn(fakeRumContext.copy(viewId = fakeRumContext.viewId + "-new", viewTimeOffsetMs = 0L))
        val mockEvent = fakeFixture.mockDownEvent()

        // When
        fakeFixture.installedCallback().dispatchTouchEvent(mockEvent)

        // Then
        verifyNoInteractions(fakeFixture.mockPrivacyManager)
    }

    @Test
    fun `M clear window privacy W stop()`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.publish()

        // When
        fakeFixture.testedInterceptor.stop()

        // Then
        assertThat(
            fakeFixture.fakeSource.hasTouchPrivacy(fakeFixture.mockRoot, RumViewIdentityScope(fakeRumContext.viewId))
        ).isFalse()
    }

    @Test
    fun `M wrap the window callback W intercept`() {
        // Given
        val mockWindow = mockWindowWithDecor()
        val testedInterceptor = buildInterceptor()

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        assertThat(captor.firstValue).isInstanceOf(CompositionWindowTouchCallback::class.java)
    }

    @Test
    fun `M wrap the existing callback as the delegate W intercept`() {
        // Given
        val mockExistingCallback = mock<Window.Callback>()
        val mockWindow = mockWindowWithDecor()
        whenever(mockWindow.callback).thenReturn(mockExistingCallback)
        val testedInterceptor = buildInterceptor()

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        val installed = captor.firstValue as CompositionWindowTouchCallback
        assertThat(installed.wrappedCallback).isSameAs(mockExistingCallback)
    }

    @Test
    fun `M wrap a NoOpWindowCallback W intercept { window has no callback }`() {
        // Given
        val mockWindow = mockWindowWithDecor()
        whenever(mockWindow.callback).thenReturn(null)
        val testedInterceptor = buildInterceptor()

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        val captor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = captor.capture()
        val installed = captor.firstValue as CompositionWindowTouchCallback
        assertThat(installed.wrappedCallback).isInstanceOf(NoOpWindowCallback::class.java)
    }

    @Test
    fun `M not rewrap a window W intercept { window already wrapped }`() {
        // Given
        val mockWindow = mockWindowWithDecor()
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val fakeCaptor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = fakeCaptor.capture()
        whenever(mockWindow.callback).thenReturn(fakeCaptor.firstValue)

        // When
        testedInterceptor.intercept(listOf(mockWindow))

        // Then
        verify(mockWindow, times(1)).callback = isA()
    }

    @Test
    fun `M restore the original callback W intercept { window drops out of the list }`() {
        // Given
        val mockExistingCallback = mock<Window.Callback>()
        val mockWindow = mockWindowWithDecor()
        whenever(mockWindow.callback).thenReturn(mockExistingCallback)
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val fakeCaptor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = fakeCaptor.capture()
        whenever(mockWindow.callback).thenReturn(fakeCaptor.firstValue)

        // When
        testedInterceptor.intercept(emptyList())

        // Then
        verify(mockWindow).callback = mockExistingCallback
    }

    @Test
    fun `M clear the wrapped callback W intercept { window had no original callback }`() {
        // Given
        val mockWindow = mockWindowWithDecor()
        whenever(mockWindow.callback).thenReturn(null)
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val fakeCaptor = argumentCaptor<Window.Callback>()
        verify(mockWindow).callback = fakeCaptor.capture()
        whenever(mockWindow.callback).thenReturn(fakeCaptor.firstValue)

        // When
        testedInterceptor.intercept(emptyList())

        // Then
        verify(mockWindow).callback = null
    }

    @Test
    fun `M leave the window untouched W stop() { intercept, callback replaced since wrapping }`() {
        // Given
        val mockWindow = mockWindowWithDecor()
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindow))
        val mockOtherCallback = mock<Window.Callback>()
        whenever(mockWindow.callback).thenReturn(mockOtherCallback)
        clearInvocations(mockWindow)

        // When
        testedInterceptor.stop()

        // Then
        verify(mockWindow, never()).callback = any()
    }

    @Test
    fun `M restore every wrapped window W stop`() {
        // Given
        val mockWindowA = mockWindowWithDecor()
        val mockWindowB = mockWindowWithDecor()
        val testedInterceptor = buildInterceptor()
        testedInterceptor.intercept(listOf(mockWindowA, mockWindowB))
        val fakeCaptorA = argumentCaptor<Window.Callback>()
        val fakeCaptorB = argumentCaptor<Window.Callback>()
        verify(mockWindowA).callback = fakeCaptorA.capture()
        verify(mockWindowB).callback = fakeCaptorB.capture()
        whenever(mockWindowA.callback).thenReturn(fakeCaptorA.firstValue)
        whenever(mockWindowB.callback).thenReturn(fakeCaptorB.firstValue)

        // When
        testedInterceptor.stop()

        // Then
        verify(mockWindowA).callback = null
        verify(mockWindowB).callback = null
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard previous screen overrides W stop and resume`(fakeHasWindow: Boolean) {
        // Given
        val fakePrivacyManager = TouchPrivacyManager(TouchPrivacy.HIDE)
        val mockArea: Rect = mock()
        whenever(mockArea.contains(any<Int>(), any<Int>())).thenReturn(true)
        fakePrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(mockArea to TouchPrivacy.SHOW))
        val testedInterceptor = buildInterceptor(fakePrivacyManager)
        val fakeWindows = if (fakeHasWindow) listOf(mockWindowWithDecor()) else emptyList()
        testedInterceptor.intercept(fakeWindows)

        // When
        testedInterceptor.stop()
        testedInterceptor.intercept(fakeWindows)

        // Then
        assertThat(fakePrivacyManager.getCurrentOverrideAreas()).isEmpty()
        assertThat(fakePrivacyManager.shouldRecordTouch(Point())).isFalse()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M accept fresh overrides W replaceCurrentTouchOverrideAreas() { resumed }`(fakeHasWindow: Boolean) {
        // Given
        val fakePrivacyManager = TouchPrivacyManager(TouchPrivacy.HIDE)
        val mockArea: Rect = mock()
        whenever(mockArea.contains(any<Int>(), any<Int>())).thenReturn(true)
        fakePrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(mockArea to TouchPrivacy.SHOW))
        val testedInterceptor = buildInterceptor(fakePrivacyManager)
        val fakeWindows = if (fakeHasWindow) listOf(mockWindowWithDecor()) else emptyList()
        testedInterceptor.intercept(fakeWindows)

        testedInterceptor.stop()
        testedInterceptor.intercept(fakeWindows)

        // When
        fakePrivacyManager.replaceCurrentTouchOverrideAreas(mapOf(mockArea to TouchPrivacy.SHOW))

        // Then
        assertThat(fakePrivacyManager.shouldRecordTouch(Point())).isTrue()
    }

    @Test
    fun `M wrap replacement as delegate W intercept() { external callback replaced wrapper }`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        val mockReplacement = fakeFixture.replaceCallback()

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // Then
        assertThat(fakeFixture.installedCallback().wrappedCallback).isSameAs(mockReplacement)
    }

    @Test
    fun `M retain replacement wrapper W intercept() { replacement already wrapped }`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        fakeFixture.replaceCallback()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // When
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // Then
        verify(fakeFixture.mockWindow, times(2)).callback = isA<CompositionWindowTouchCallback>()
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M apply privacy once W dispatchTouchEvent() { replacement may delegate to old wrapper }`(
        fakeDelegatesToOldWrapper: Boolean
    ) {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        val mockReplacement = fakeFixture.replaceCallback(fakeDelegatesToOldWrapper)
        fakeFixture.publish()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        val mockEvent = fakeFixture.mockDownEvent()

        // When
        fakeFixture.installedCallback().dispatchTouchEvent(mockEvent)

        // Then
        verify(fakeFixture.mockPrivacyManager).shouldRecordTouch(isA(), isA())
        verify(mockReplacement).dispatchTouchEvent(mockEvent)
    }

    @Test
    fun `M restore replacement callback W stop()`() {
        // Given
        val fakeFixture = CallbackFixture()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))
        val mockReplacement = fakeFixture.replaceCallback()
        fakeFixture.testedInterceptor.intercept(listOf(fakeFixture.mockWindow))

        // When
        fakeFixture.testedInterceptor.stop()

        // Then
        verify(fakeFixture.mockWindow).callback = mockReplacement
    }

    private inner class CallbackFixture(decorAvailable: Boolean = true) {
        val mockRoot: View = mock()
        val mockOriginalCallback: Window.Callback = mock()
        val mockPrivacyManager: TouchPrivacyManager = mock()
        val mockRumProvider: RumContextProvider = mock {
            on { getRumContext() } doReturn fakeRumContext.copy(viewTimeOffsetMs = 0L)
        }
        private var mockDecor: View? = mockRoot.takeIf { decorAvailable }
        private var fakeCurrentCallback: Window.Callback? = mockOriginalCallback
        val mockWindow: Window = mock {
            on { peekDecorView() } doAnswer { mockDecor }
            on { callback } doAnswer { fakeCurrentCallback }
        }
        val fakeSource = ActiveWindowSource().apply { update(listOf(mockRoot)) }
        val testedInterceptor = buildInterceptor(mockPrivacyManager, fakeSource, mockRumProvider)

        init {
            doAnswer { fakeCurrentCallback = it.getArgument(0); null }
                .whenever(mockWindow).callback = anyOrNull()
        }

        fun attachRoot() {
            mockDecor = mockRoot
            fakeSource.update(listOf(mockRoot))
        }

        fun installedCallback(): CompositionWindowTouchCallback = fakeCurrentCallback as CompositionWindowTouchCallback

        fun publish() {
            fakeSource.publishTouchPrivacy(
                fakeSource.snapshot(),
                listOf(mockRoot),
                RumViewIdentityScope(fakeRumContext.viewId)
            ) {}
        }

        fun mockDownEvent(): MotionEvent = mock { on { action } doReturn MotionEvent.ACTION_DOWN }

        fun replaceCallback(delegateToOldWrapper: Boolean = false): Window.Callback {
            val fakeOldWrapper = installedCallback()
            val mockReplacement: Window.Callback = mock()
            if (delegateToOldWrapper) {
                whenever(mockReplacement.dispatchTouchEvent(any())).thenAnswer {
                    fakeOldWrapper.dispatchTouchEvent(it.getArgument(0))
                }
            }
            fakeCurrentCallback = mockReplacement
            return mockReplacement
        }
    }

    private fun mockWindowWithDecor(): Window = mock {
        whenever(it.peekDecorView()).thenReturn(mock<View>())
    }

    private fun buildInterceptor(
        touchPrivacyManager: TouchPrivacyManager = mock(),
        windowSource: ActiveWindowSource = ActiveWindowSource(),
        rumContextProvider: RumContextProvider = mock()
    ) = CompositionWindowTouchInterceptor(
        appContext = mock<Application> { mockApplication ->
            val mockResources = mock<Resources> {
                whenever(it.displayMetrics).thenReturn(DisplayMetrics())
            }
            whenever(mockApplication.resources).thenReturn(mockResources)
        },
        recordWriter = mock<RecordWriter>(),
        timeProvider = mock<TimeProvider>(),
        rumContextProvider = rumContextProvider,
        touchPrivacyManager = touchPrivacyManager,
        internalLogger = mock<InternalLogger>(),
        windowSource = windowSource
    )
}
