/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.app.Activity
import android.app.Application
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal class CompositionWindowThreadTest {
    private val mockMainLooper: Looper = mock()
    private val mockMainHandler: Handler = mock()
    private val mockSecondaryHandler: Handler = mock()
    private lateinit var mockedLooper: MockedStatic<Looper>

    @BeforeEach
    fun setUp() {
        mockedLooper = mockStatic(Looper::class.java)
        mockedLooper.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mockMainLooper)
        whenever(mockMainHandler.looper).thenReturn(mockMainLooper)
        whenever(mockSecondaryHandler.looper).thenReturn(mock())
    }

    @AfterEach
    fun tearDown() = mockedLooper.close()

    @ParameterizedTest
    @EnumSource(Owner::class)
    fun `M accept only attached main roots W isAttachedToMainLooper`(fakeOwner: Owner) {
        // Given
        val root = root(fakeOwner)

        // When
        val accepted = isAttachedToMainLooper(root)

        // Then: ownership checks must not inspect the hierarchy to determine its owner.
        assertThat(accepted).isEqualTo(fakeOwner == Owner.MAIN)
        verify(root).handler
        verifyNoMoreInteractions(root)
    }

    @ParameterizedTest
    @CsvSource("true, SECONDARY", "false, SECONDARY", "true, DETACHED", "false, DETACHED")
    fun `M exclude unsupported roots from draw and touch interception W start`(
        fakeTracked: Boolean,
        fakeOwner: Owner
    ) {
        // Given: supported roots on either side preserve their original order.
        val first = root(Owner.MAIN)
        val unsupported = root(fakeOwner)
        val last = root(Owner.MAIN)
        val firstWindow = window(first)
        val unsupportedWindow = window(unsupported)
        val lastWindow = window(last)
        val mockDrawInterceptor: CompositionViewOnDrawInterceptor = mock()
        val mockTouchInterceptor: CompositionWindowTouchInterceptor = mock()
        val mockWindowResolver: (View) -> Window? = mock()
        val windows = mapOf(first to firstWindow, unsupported to unsupportedWindow, last to lastWindow)
        whenever(mockWindowResolver.invoke(any())).thenAnswer { windows[it.getArgument<View>(0)] }
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock(),
            interceptor = mockDrawInterceptor,
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = if (fakeTracked) activity(unsupportedWindow) else null,
            uiHandler = immediateHandler(),
            windowProvider = { listOf(first, unsupported, last) },
            windowFromDecorView = mockWindowResolver
        )

        // When
        testedLifecycle.start()

        // Then: reject the root before reflection, listener registration or callback wrapping.
        verify(mockDrawInterceptor).intercept(listOf(first, last))
        verify(mockTouchInterceptor).intercept(listOf(firstWindow, lastWindow))
        verify(mockWindowResolver, never()).invoke(unsupported)
        verify(unsupported, atLeastOnce()).handler
        verifyNoMoreInteractions(unsupported)
    }

    @ParameterizedTest
    @CsvSource("true, MAIN", "false, MAIN", "true, SECONDARY", "false, SECONDARY")
    fun `M defer detached roots until main attachment W periodic refresh`(fakeTracked: Boolean, fakeOwner: Owner) {
        // Given: an activity can be reported before its decor view is attached to any looper.
        val root = root(Owner.DETACHED)
        val window = window(root)
        val mockDrawInterceptor: CompositionViewOnDrawInterceptor = mock()
        val mockTouchInterceptor: CompositionWindowTouchInterceptor = mock()
        val mockWindowResolver: (View) -> Window? = mock()
        whenever(mockWindowResolver.invoke(root)).thenReturn(window)
        val handler = immediateHandler()
        var reportedRoots = if (fakeTracked) emptyList() else listOf(root)
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock<Application>(),
            interceptor = mockDrawInterceptor,
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = if (fakeTracked) activity(window) else null,
            uiHandler = handler,
            windowProvider = { reportedRoots },
            windowFromDecorView = mockWindowResolver
        )
        testedLifecycle.start()
        verify(mockDrawInterceptor).intercept(emptyList())
        verify(mockTouchInterceptor).intercept(emptyList())
        verify(mockWindowResolver, never()).invoke(any())
        val scheduled = argumentCaptor<Runnable>()
        verify(handler).postDelayed(scheduled.capture(), any())

        // When: attachment becomes visible to the next refresh without another activity callback.
        setOwner(root, fakeOwner)
        reportedRoots = listOf(root)
        scheduled.firstValue.run()

        // Then
        if (fakeOwner == Owner.MAIN) {
            verify(mockDrawInterceptor).intercept(listOf(root))
            verify(mockTouchInterceptor).intercept(listOf(window))
        } else {
            verify(mockDrawInterceptor, times(2)).intercept(emptyList())
            verify(mockTouchInterceptor, times(2)).intercept(emptyList())
            verify(mockWindowResolver, never()).invoke(any())
        }
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M skip unsupported roots before identifying or traversing them W capture`(fakeOwner: Owner) {
        // Given: the producer also guards against a stale or independently populated window source.
        val first = root(Owner.MAIN)
        val unsupported = root(fakeOwner)
        val last = root(Owner.MAIN)
        val fixture = CaptureFixture(listOf(first, unsupported, last))

        // When
        val snapshot = (fixture.producer.capture(fixture.context) as CaptureStep.Done).value

        // Then
        assertThat(snapshot!!.root!!.children.map { it.identity.localId }).containsExactly("1", "3")
        fixture.verifyNotVisited(unsupported)
        verify(unsupported, atLeastOnce()).handler
        verifyNoMoreInteractions(unsupported)
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M clear committed overrides W capture { no supported roots remain }`(fakeOwner: Owner) {
        // Given
        val root = root(fakeOwner)
        val fixture = CaptureFixture(listOf(root))

        // When
        val snapshot = (fixture.producer.capture(fixture.context) as CaptureStep.Done).value

        // Then
        assertThat(snapshot).isNull()
        fixture.verifyNotVisited(root)
        verify(fixture.mockTouchPrivacyManager).replaceCurrentTouchOverrideAreas(emptyMap())
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M abort before identifying the next window W capture { ownership changes during earlier window }`(
        fakeOwner: Owner
    ) {
        // Given
        val first = root(Owner.MAIN)
        val next = root(Owner.MAIN)
        val fixture = CaptureFixture(listOf(first, next))
        whenever(fixture.mockTraversal.traverseWindow(eq(first), any(), any(), any())).thenAnswer {
            setOwner(next, fakeOwner)
            CaptureStep.Done(fixture.windowResult(first))
        }

        // When
        val snapshot = (fixture.producer.capture(fixture.context) as CaptureStep.Done).value

        // Then: abort rather than publish overrides from only the already completed window.
        assertThat(snapshot).isNull()
        fixture.verifyNotVisited(next)
        verify(fixture.mockTouchPrivacyManager, never()).replaceCurrentTouchOverrideAreas(any())
    }

    @ParameterizedTest
    @EnumSource(Owner::class)
    fun `M recheck root owner before resuming a yielded window W capture`(fakeOwner: Owner) {
        // Given: the continuation would otherwise read the hierarchy and publish a SHOW override.
        val root = root(Owner.MAIN)
        val fixture = CaptureFixture(listOf(root))
        var resumed = false
        whenever(fixture.mockTraversal.traverseWindow(eq(root), any(), any(), any())).thenReturn(
            CaptureStep.Yielded {
                resumed = true
                CaptureStep.Done(fixture.windowResult(root))
            }
        )
        val step = fixture.producer.capture(fixture.context) as CaptureStep.Yielded

        // When
        setOwner(root, fakeOwner)
        val snapshot = (step.resume() as CaptureStep.Done).value

        // Then
        assertThat(resumed).isEqualTo(fakeOwner == Owner.MAIN)
        if (fakeOwner == Owner.MAIN) {
            assertThat(snapshot).isNotNull()
            verify(fixture.mockTouchPrivacyManager).replaceCurrentTouchOverrideAreas(any())
        } else {
            assertThat(snapshot).isNull()
            verify(fixture.mockTouchPrivacyManager, never()).replaceCurrentTouchOverrideAreas(any())
        }
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M remove original draw listener without reading moved root W intercept`(fakeOwner: Owner) {
        // Given: the listener belongs to the observer captured on the original UI thread.
        val root = root(Owner.MAIN)
        val observer: ViewTreeObserver = mock()
        whenever(observer.isAlive).thenReturn(true)
        whenever(root.viewTreeObserver).thenReturn(observer)
        val source = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(source, {}, mock())
        testedInterceptor.intercept(listOf(root))
        val registered = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(observer).addOnDrawListener(registered.capture())
        setOwner(root, fakeOwner)
        whenever(root.viewTreeObserver).thenThrow(AssertionError("Must not read a moved hierarchy"))

        // When
        testedInterceptor.intercept(emptyList())

        // Then
        verify(observer).removeOnDrawListener(registered.firstValue)
        assertThat(source.currentWindows()).isEmpty()
    }

    private fun root(owner: Owner): View = mock<View>().also { setOwner(it, owner) }

    private fun setOwner(root: View, owner: Owner) {
        whenever(root.handler).thenReturn(
            when (owner) {
                Owner.MAIN -> mockMainHandler
                Owner.SECONDARY -> mockSecondaryHandler
                Owner.DETACHED -> null
            }
        )
    }

    private fun window(root: View): Window = mock<Window>().also { whenever(it.peekDecorView()).thenReturn(root) }
    private fun activity(window: Window): Activity = mock<Activity>().also { whenever(it.window).thenReturn(window) }

    private fun immediateHandler(): Handler = mock<Handler>().also { handler ->
        doAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }.whenever(handler).post(any())
        whenever(handler.postDelayed(any<Runnable>(), any())).thenReturn(true)
    }

    private class CaptureFixture(private val windows: List<View>) {
        val mockTraversal: AndroidWindowTraversal = mock()
        val mockIdentifiers: ViewIdentifierResolver = mock()
        val mockTouchPrivacyManager: TouchPrivacyManager = mock()
        private val scope = CapturedRumViewScope(RumViewIdentityScope("view"), 0L)
        private val identityFactory = DefaultCapturedIdentityFactory(scope.scope)
        val context = CaptureGenerationContext(1, 0, 1_000, CaptureTimeProvider { 0 })

        init {
            windows.forEachIndexed { index, window ->
                whenever(mockIdentifiers.resolveViewId(window)).thenReturn(index + 1L)
            }
            whenever(mockTraversal.traverseWindow(any(), any(), any(), any())).thenAnswer {
                CaptureStep.Done(windowResult(it.getArgument(0)))
            }
        }

        val producer = AndroidCapturedSnapshotProducer(
            windowSource = ActiveWindowSource().apply { update(windows) },
            scopeProvider = RumViewScopeProvider { scope },
            timeProvider = mock(),
            traversal = mockTraversal,
            touchPrivacyManager = mockTouchPrivacyManager,
            viewIdentifierResolver = mockIdentifiers
        )

        fun windowResult(window: View): WindowWalkResult.Present {
            val root = CapturedLayer(
                identityFactory.window((windows.indexOf(window) + 1).toString()),
                CapturedLayerKind.WINDOW_ROOT,
                CapturedBounds(0, 0, 100, 100),
                emptyList()
            )
            return WindowWalkResult.Present(root, listOf(root), emptyList(), mapOf(Rect() to TouchPrivacy.SHOW))
        }

        fun verifyNotVisited(root: View) {
            verify(mockIdentifiers, never()).resolveViewId(root)
            verify(mockTraversal, never()).traverseWindow(eq(root), any(), any(), any())
        }
    }

    enum class Owner { MAIN, SECONDARY, DETACHED }
}
