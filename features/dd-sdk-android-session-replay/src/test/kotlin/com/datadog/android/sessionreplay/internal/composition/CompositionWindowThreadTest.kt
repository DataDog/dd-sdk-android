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
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionWindowThreadTest {

    @StringForgery
    lateinit var fakeScopeValue: String

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
        val mockRoot = mockRoot(fakeOwner)

        // When
        val accepted = isAttachedToMainLooper(mockRoot)

        // Then
        assertThat(accepted).isEqualTo(fakeOwner == Owner.MAIN)
        verify(mockRoot).handler
        verifyNoMoreInteractions(mockRoot)
    }

    @ParameterizedTest
    @CsvSource("true, SECONDARY", "false, SECONDARY", "true, DETACHED", "false, DETACHED")
    fun `M exclude unsupported roots from draw and touch interception W start`(
        fakeTracked: Boolean,
        fakeOwner: Owner
    ) {
        // Given
        val mockFirst = mockRoot(Owner.MAIN)
        val mockUnsupported = mockRoot(fakeOwner)
        val mockLast = mockRoot(Owner.MAIN)
        val fakeFixture =
            DiscoveryFixture(
                listOf(mockFirst, mockUnsupported, mockLast),
                trackedRoot = mockUnsupported.takeIf {
                    fakeTracked
                }
            )

        // When
        fakeFixture.testedLifecycle.start()

        // Then
        verify(fakeFixture.mockDrawInterceptor).intercept(listOf(mockFirst, mockLast))
        verify(
            fakeFixture.mockTouchInterceptor
        ).intercept(listOf(fakeFixture.mockWindowFor(mockFirst), fakeFixture.mockWindowFor(mockLast)))
        verify(fakeFixture.mockWindowResolver, never()).invoke(mockUnsupported)
        verify(mockUnsupported, atLeastOnce()).handler
        if (fakeTracked && fakeOwner == Owner.DETACHED) {
            verify(mockUnsupported).addOnAttachStateChangeListener(isA())
        }
        verifyNoMoreInteractions(mockUnsupported)
    }

    @ParameterizedTest
    @CsvSource("true, MAIN", "false, MAIN", "true, SECONDARY", "false, SECONDARY")
    fun `M defer detached roots until main attachment W run() { periodic refresh }`(
        fakeTracked: Boolean,
        fakeOwner: Owner
    ) {
        // Given
        val mockRoot = mockRoot(Owner.DETACHED)
        val fakeFixture = DiscoveryFixture(
            listOf(mockRoot),
            trackedRoot = mockRoot.takeIf { fakeTracked },
            reportedRoots = if (fakeTracked) emptyList() else listOf(mockRoot)
        )
        fakeFixture.testedLifecycle.start()
        verify(fakeFixture.mockDrawInterceptor).intercept(emptyList())
        verify(fakeFixture.mockTouchInterceptor).intercept(emptyList())
        verify(fakeFixture.mockWindowResolver, never()).invoke(any())
        val fakeScheduled = fakeFixture.periodicRefresh()

        // When
        setOwner(mockRoot, fakeOwner)
        fakeFixture.reportedRoots = listOf(mockRoot)
        fakeScheduled.run()

        // Then
        if (fakeOwner == Owner.MAIN) {
            verify(fakeFixture.mockDrawInterceptor).intercept(listOf(mockRoot))
            verify(fakeFixture.mockTouchInterceptor).intercept(listOf(fakeFixture.mockWindowFor(mockRoot)))
        } else {
            verify(fakeFixture.mockDrawInterceptor, times(2)).intercept(emptyList())
            verify(fakeFixture.mockTouchInterceptor, times(2)).intercept(emptyList())
            verify(fakeFixture.mockWindowResolver, never()).invoke(any())
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M intercept before polling W onViewAttachedToWindow() { tracked activity root attaches }`(
        fakeAlreadyResumed: Boolean
    ) {
        // Given
        val fakeFixture = AttachmentFixture(fakeAlreadyResumed)
        fakeFixture.start()
        val fakeListener = fakeFixture.attachmentListener()
        clearInvocations(fakeFixture.mockHandler, fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)

        // When
        setOwner(fakeFixture.mockRoot, Owner.MAIN)
        fakeListener.onViewAttachedToWindow(fakeFixture.mockRoot)

        // Then
        verify(fakeFixture.mockDrawInterceptor).intercept(listOf(fakeFixture.mockRoot))
        verify(fakeFixture.mockTouchInterceptor).intercept(listOf(fakeFixture.mockWindow))
        verify(fakeFixture.mockRoot).removeOnAttachStateChangeListener(fakeListener)
        verify(fakeFixture.mockRoot).addOnAttachStateChangeListener(isA())
        verify(fakeFixture.mockHandler, never()).post(any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M remove attachment listener W onActivityPaused() + stop() { pending activity pauses or recording stops }`(
        fakeStop: Boolean
    ) {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()
        val fakeListener = fakeFixture.attachmentListener()

        // When
        if (fakeStop) {
            fakeFixture.testedLifecycle.stop()
        } else {
            fakeFixture.testedCallbacks.onActivityPaused(
                fakeFixture.mockActivity
            )
        }

        // Then
        verify(fakeFixture.mockRoot).removeOnAttachStateChangeListener(fakeListener)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M ignore detached listener W onViewAttachedToWindow() { activity paused or recording stopped }`(
        fakeStop: Boolean
    ) {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()
        val fakeListener = fakeFixture.attachmentListener()

        if (fakeStop) {
            fakeFixture.testedLifecycle.stop()
        } else {
            fakeFixture.testedCallbacks.onActivityPaused(
                fakeFixture.mockActivity
            )
        }

        clearInvocations(fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)
        setOwner(fakeFixture.mockRoot, Owner.MAIN)

        // When
        fakeListener.onViewAttachedToWindow(fakeFixture.mockRoot)

        // Then
        verifyNoInteractions(fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)
    }

    @Test
    fun `M keep one attachment listener W onActivityResumed() + run() { detached activity is refreshed repeatedly }`() {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()

        // When
        fakeFixture.testedCallbacks.onActivityResumed(fakeFixture.mockActivity)
        val scheduled = argumentCaptor<Runnable>()
        verify(fakeFixture.mockHandler).postDelayed(scheduled.capture(), eq(1_000L))
        scheduled.firstValue.run()

        // Then
        verify(fakeFixture.mockRoot).addOnAttachStateChangeListener(isA())
        verify(fakeFixture.mockRoot, never()).removeOnAttachStateChangeListener(any())
    }

    @Test
    fun `M reject secondary owner W onViewAttachedToWindow() { tracked root attaches off main thread }`() {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()
        val fakeListener = fakeFixture.attachmentListener()
        doAnswer { true }.whenever(fakeFixture.mockHandler).post(any())
        clearInvocations(fakeFixture.mockHandler, fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)

        // When
        setOwner(fakeFixture.mockRoot, Owner.SECONDARY)
        fakeListener.onViewAttachedToWindow(fakeFixture.mockRoot)

        // Then
        verify(fakeFixture.mockRoot).removeOnAttachStateChangeListener(fakeListener)
        verifyNoInteractions(fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)
    }

    @Test
    fun `M exclude secondary owner W run() { posted attachment refresh }`() {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()
        val fakeListener = fakeFixture.attachmentListener()
        doAnswer { true }.whenever(fakeFixture.mockHandler).post(any())
        clearInvocations(fakeFixture.mockHandler, fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)

        setOwner(fakeFixture.mockRoot, Owner.SECONDARY)
        fakeListener.onViewAttachedToWindow(fakeFixture.mockRoot)

        val fakePosted = argumentCaptor<Runnable>()
        verify(fakeFixture.mockHandler).post(fakePosted.capture())

        // When
        fakePosted.firstValue.run()

        // Then
        verify(fakeFixture.mockDrawInterceptor).intercept(emptyList())
        verify(fakeFixture.mockTouchInterceptor).intercept(emptyList())
        verify(fakeFixture.mockRoot, never()).viewTreeObserver
    }

    @Test
    fun `M ignore stale attachment callback W onViewAttachedToWindow() { recording restarted }`() {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()
        val fakeOldListener = fakeFixture.attachmentListener()
        fakeFixture.testedLifecycle.stop()
        fakeFixture.testedLifecycle.start()
        val fakeRegistrations = argumentCaptor<View.OnAttachStateChangeListener>()
        verify(fakeFixture.mockRoot, times(2)).addOnAttachStateChangeListener(fakeRegistrations.capture())
        clearInvocations(fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)

        // When
        setOwner(fakeFixture.mockRoot, Owner.MAIN)
        fakeOldListener.onViewAttachedToWindow(fakeFixture.mockRoot)

        // Then
        verifyNoInteractions(fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)
    }

    @Test
    fun `M accept current attachment callback W onViewAttachedToWindow() { recording restarted }`() {
        // Given
        val fakeFixture = AttachmentFixture()
        fakeFixture.start()
        val fakeOldListener = fakeFixture.attachmentListener()
        fakeFixture.testedLifecycle.stop()
        fakeFixture.testedLifecycle.start()
        val fakeRegistrations = argumentCaptor<View.OnAttachStateChangeListener>()
        verify(fakeFixture.mockRoot, times(2)).addOnAttachStateChangeListener(fakeRegistrations.capture())
        clearInvocations(fakeFixture.mockDrawInterceptor, fakeFixture.mockTouchInterceptor)

        setOwner(fakeFixture.mockRoot, Owner.MAIN)
        fakeOldListener.onViewAttachedToWindow(fakeFixture.mockRoot)

        // When
        fakeRegistrations.lastValue.onViewAttachedToWindow(fakeFixture.mockRoot)

        // Then
        verify(fakeFixture.mockDrawInterceptor).intercept(listOf(fakeFixture.mockRoot))
        verify(fakeFixture.mockTouchInterceptor).intercept(listOf(fakeFixture.mockWindow))
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M skip unsupported roots before identifying or traversing them W capture`(fakeOwner: Owner) {
        // Given
        val mockFirst = mockRoot(Owner.MAIN)
        val mockUnsupported = mockRoot(fakeOwner)
        val mockLast = mockRoot(Owner.MAIN)
        val fakeFixture = CaptureFixture(fakeScopeValue, listOf(mockFirst, mockUnsupported, mockLast))

        // When
        val snapshot = (
            fakeFixture.producer.capture(
                fakeFixture.context
            )
            ).buildSnapshot(fakeFixture.context)

        // Then
        val capturedSnapshot = checkNotNull(snapshot) { "Expected a snapshot of the supported windows" }
        val root = checkNotNull(capturedSnapshot.root) { "Expected a synthetic screen root" }
        assertThat(root.children.map { it.identity.localId }).containsExactly("1", "3")
        fakeFixture.verifyNotVisited(mockUnsupported)
        verify(mockUnsupported, atLeastOnce()).handler
        verifyNoMoreInteractions(mockUnsupported)
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M clear committed overrides W capture { no supported roots remain }`(fakeOwner: Owner) {
        // Given
        val mockRoot = mockRoot(fakeOwner)
        val fakeFixture = CaptureFixture(fakeScopeValue, listOf(mockRoot))

        // When
        val snapshot = (
            fakeFixture.producer.capture(
                fakeFixture.context
            )
            ).buildSnapshot(fakeFixture.context)

        // Then
        assertThat(snapshot).isNull()
        fakeFixture.verifyNotVisited(mockRoot)
        verify(fakeFixture.mockTouchPrivacyManager).replaceCurrentTouchOverrideAreas(emptyMap())
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M abort before identifying the next window W capture { ownership changes during earlier window }`(
        fakeOwner: Owner
    ) {
        // Given
        val mockFirst = mockRoot(Owner.MAIN)
        val mockNext = mockRoot(Owner.MAIN)
        val fakeFixture = CaptureFixture(fakeScopeValue, listOf(mockFirst, mockNext))
        whenever(fakeFixture.mockTraversal.traverseWindow(eq(mockFirst), any(), any(), any())).thenAnswer {
            setOwner(mockNext, fakeOwner)
            fakeFixture.windowResult(mockFirst)
        }

        // When
        val snapshot = (
            fakeFixture.producer.capture(
                fakeFixture.context
            )
            ).buildSnapshot(fakeFixture.context)

        // Then
        assertThat(snapshot).isNull()
        fakeFixture.verifyNotVisited(mockNext)
        verify(fakeFixture.mockTouchPrivacyManager, never()).replaceCurrentTouchOverrideAreas(any())
    }

    @ParameterizedTest
    @EnumSource(Owner::class)
    fun `M recheck root owner W capture { ownership changes during copying }`(fakeOwner: Owner) {
        // Given
        val mockRoot = mockRoot(Owner.MAIN)
        val fakeFixture = CaptureFixture(fakeScopeValue, listOf(mockRoot))
        whenever(fakeFixture.mockTraversal.traverseWindow(eq(mockRoot), any(), any(), any())).thenAnswer {
            setOwner(mockRoot, fakeOwner)
            fakeFixture.windowResult(mockRoot)
        }

        // When
        val snapshot = (fakeFixture.producer.capture(fakeFixture.context))

        // Then
        if (fakeOwner == Owner.MAIN) {
            assertThat(snapshot).isNotNull()
            verify(fakeFixture.mockTouchPrivacyManager).replaceCurrentTouchOverrideAreas(
                mapOf(
                    Rect() to TouchPrivacy.SHOW
                )
            )
        } else {
            assertThat(snapshot).isNull()
            verify(fakeFixture.mockTouchPrivacyManager, never()).replaceCurrentTouchOverrideAreas(any())
        }
    }

    @ParameterizedTest
    @EnumSource(value = Owner::class, names = ["SECONDARY", "DETACHED"])
    fun `M remove original draw listener without reading moved root W intercept`(fakeOwner: Owner) {
        // Given
        val mockRoot = mockRoot(Owner.MAIN)
        val mockObserver: ViewTreeObserver = mock()
        whenever(mockObserver.isAlive).thenReturn(true)
        whenever(mockRoot.viewTreeObserver).thenReturn(mockObserver)
        val fakeSource = ActiveWindowSource()
        val testedInterceptor = CompositionViewOnDrawInterceptor(fakeSource, {}, mock())
        testedInterceptor.intercept(listOf(mockRoot))
        val fakeRegistered = argumentCaptor<ViewTreeObserver.OnDrawListener>()
        verify(mockObserver).addOnDrawListener(fakeRegistered.capture())
        setOwner(mockRoot, fakeOwner)
        whenever(mockRoot.viewTreeObserver).thenThrow(AssertionError("Must not read a moved hierarchy"))

        // When
        testedInterceptor.intercept(emptyList())

        // Then
        verify(mockObserver).removeOnDrawListener(fakeRegistered.firstValue)
        assertThat(fakeSource.currentWindows()).isEmpty()
    }

    private fun mockRoot(owner: Owner): View = mock<View>().also { setOwner(it, owner) }

    private fun setOwner(root: View, owner: Owner) {
        whenever(root.handler).thenReturn(
            when (owner) {
                Owner.MAIN -> mockMainHandler
                Owner.SECONDARY -> mockSecondaryHandler
                Owner.DETACHED -> null
            }
        )
    }

    private fun mockWindow(root: View): Window = mock<Window>().also { whenever(it.peekDecorView()).thenReturn(root) }
    private fun mockActivity(
        window: Window
    ): Activity = mock<Activity>().also { whenever(it.window).thenReturn(window) }

    private fun mockImmediateHandler(): Handler = mock<Handler>().also { mockHandler ->
        doAnswer {
            it.getArgument<Runnable>(0).run()
            true
        }.whenever(mockHandler).post(any())
        whenever(mockHandler.postDelayed(any<Runnable>(), any())).thenReturn(true)
    }

    private inner class DiscoveryFixture(
        roots: List<View>,
        trackedRoot: View? = null,
        var reportedRoots: List<View> = roots
    ) {
        private val windows = roots.associateWith(::mockWindow)
        val mockDrawInterceptor: CompositionViewOnDrawInterceptor = mock()
        val mockTouchInterceptor: CompositionWindowTouchInterceptor = mock()
        val mockWindowResolver: (View) -> Window? = mock {
            on { invoke(any()) } doAnswer { invocation -> windows[invocation.getArgument<View>(0)] }
        }
        private val mockHandler = mockImmediateHandler()
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mock<Application>(),
            interceptor = mockDrawInterceptor,
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = trackedRoot?.let { mockActivity(mockWindowFor(it)) },
            uiHandler = mockHandler,
            windowProvider = { reportedRoots },
            windowFromDecorView = mockWindowResolver
        )

        fun mockWindowFor(root: View): Window = windows.getValue(root)

        fun periodicRefresh(): Runnable {
            val fakeRefresh = argumentCaptor<Runnable>()
            verify(mockHandler).postDelayed(fakeRefresh.capture(), eq(1_000L))
            return fakeRefresh.firstValue
        }
    }

    private inner class AttachmentFixture(private val alreadyResumed: Boolean = true) {
        val mockRoot = mockRoot(Owner.DETACHED)
        val mockWindow = mockWindow(mockRoot)
        val mockActivity = mockActivity(mockWindow)
        val mockHandler = mockImmediateHandler()
        val mockApplication: Application = mock()
        val mockDrawInterceptor: CompositionViewOnDrawInterceptor = mock()
        val mockTouchInterceptor: CompositionWindowTouchInterceptor = mock()
        lateinit var testedCallbacks: Application.ActivityLifecycleCallbacks
        val testedLifecycle = AndroidSnapshotCaptureLifecycle(
            application = mockApplication,
            interceptor = mockDrawInterceptor,
            touchInterceptor = mockTouchInterceptor,
            internalLogger = mock(),
            currentActivity = if (alreadyResumed) mockActivity else null,
            uiHandler = mockHandler,
            windowProvider = { emptyList() },
            windowFromDecorView = { null }
        )

        fun start() {
            testedLifecycle.registerCallbacks()
            val registered = argumentCaptor<Application.ActivityLifecycleCallbacks>()
            verify(mockApplication).registerActivityLifecycleCallbacks(registered.capture())
            testedCallbacks = registered.firstValue
            testedLifecycle.start()
            if (!alreadyResumed) testedCallbacks.onActivityResumed(mockActivity)
        }

        fun attachmentListener(): View.OnAttachStateChangeListener {
            val registered = argumentCaptor<View.OnAttachStateChangeListener>()
            verify(mockRoot).addOnAttachStateChangeListener(registered.capture())
            return registered.firstValue
        }
    }

    private class CaptureFixture(private val fakeScopeValue: String, private val windows: List<View>) {
        val mockTraversal: AndroidWindowTraversal = mock()
        val mockIdentifiers: ViewIdentifierResolver = mock()
        val mockTouchPrivacyManager: TouchPrivacyManager = mock()
        private val scope = CapturedRumViewScope(RumViewIdentityScope(fakeScopeValue), 0L)
        private val identityFactory = DefaultCapturedIdentityFactory(scope.scope)
        val context = CaptureGenerationContext(1, 0, 1_000, CaptureTimeProvider { 0 })

        init {
            windows.forEachIndexed { index, window ->
                whenever(mockIdentifiers.resolveViewId(window)).thenReturn(index + 1L)
            }
            whenever(mockTraversal.traverseWindow(any(), any(), any(), any())).thenAnswer {
                windowResult(it.getArgument(0))
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

        fun windowResult(window: View): WindowCopyResult.Present {
            val copiedView = CapturedNativeView(
                identityFactory.window((windows.indexOf(window) + 1).toString()),
                -1,
                CapturedBounds(0, 0, 100, 100),
                1.0,
                CapturedViewData.Mapped(
                    CapturedViewMapperResult.None
                ),
                true,
                null,
                null
            )
            return WindowCopyResult.Present(
                CapturedNativeWindow(1f, listOf(copiedView)),
                mapOf(Rect() to TouchPrivacy.SHOW)
            )
        }

        fun verifyNotVisited(root: View) {
            verify(mockIdentifiers, never()).resolveViewId(root)
            verify(mockTraversal, never()).traverseWindow(eq(root), any(), any(), any())
        }
    }

    enum class Owner { MAIN, SECONDARY, DETACHED }
}
