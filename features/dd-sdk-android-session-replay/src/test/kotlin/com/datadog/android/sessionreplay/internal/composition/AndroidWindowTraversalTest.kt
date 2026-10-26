/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewStub
import android.webkit.WebView
import androidx.compose.ui.platform.ComposeView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedWebViewMapper
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidWindowTraversalTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
    private val mockDrawableToColorMapper: DrawableToColorMapper = mock()
    private lateinit var nextViewId: AtomicLong
    private lateinit var identityFactory: DefaultCapturedIdentityFactory
    private lateinit var fakeContext: CaptureGenerationContext
    private var fakeDensity: Float = 1f

    private val noOpFallback = CapturedViewMapper<View> { _, _ -> CapturedViewMapperResult.None }
    private val markerMapper = CapturedViewMapper<View> { view, mappingContext ->
        val bounds = mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        CapturedViewMapperResult.Wireframes(
            CapturedWireframe.Shape(
                identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                bounds = CapturedBounds(bounds.x, bounds.y, bounds.width, bounds.height)
            )
        )
    }

    @BeforeEach
    fun `set up`(
        forge: Forge,
        @StringForgery fakeScope: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeViewIdSeed: Long,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensityForgery: Float
    ) {
        nextViewId = AtomicLong(fakeViewIdSeed)
        identityFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        fakeDensity = fakeDensityForgery
        fakeContext = CaptureGenerationContext(
            id = forge.aLong(min = 1L),
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider { 0L }
        )
    }

    private fun mockView(bounds: GlobalBounds): View = mockViewWithDefaults(bounds)

    private fun mockViewGroup(bounds: GlobalBounds): ViewGroup = mockViewWithDefaults(bounds) {
        on { clipChildren } doReturn true
    }

    private inline fun <reified T : View> mockViewWithDefaults(
        fakeBounds: GlobalBounds,
        crossinline customize: KStubbing<T>.() -> Unit = {}
    ): T {
        val fakeMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock { on { displayMetrics } doReturn fakeMetrics }
        val mockView: T = mock {
            on { isShown } doReturn true
            on { scaleX } doReturn 1f
            on { scaleY } doReturn 1f
            on { left } doReturn fakeBounds.x.toInt()
            on { top } doReturn fakeBounds.y.toInt()
            on { width } doReturn fakeBounds.width.toInt().coerceAtLeast(1)
            on { height } doReturn fakeBounds.height.toInt().coerceAtLeast(1)
            on { getTag(any()) } doReturn null
            on { resources } doReturn mockResources
            customize()
        }
        whenever(mockViewIdentifierResolver.resolveViewId(mockView)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeDensity)).thenReturn(fakeBounds)
        return mockView
    }

    private fun ViewGroup.withChildren(vararg children: View) {
        stub {
            on { childCount } doReturn children.size
            children.forEachIndexed { index, child -> on { getChildAt(index) } doReturn child }
        }
    }

    private fun webViewTraversal(fallback: CapturedViewMapper<View> = noOpFallback) = traversal(
        fallback = fallback,
        typedMappers = listOf(
            CapturedMapperTypeWrapper(
                WebView::class.java,
                CapturedWebViewMapper(mockViewIdentifierResolver, mockViewBoundsResolver)
            )
        )
    )

    private fun traversal(
        fallback: CapturedViewMapper<View> = noOpFallback,
        typedMappers: List<CapturedMapperTypeWrapper<*>> = emptyList(),
        interopViewCallback: CapturedInteropViewCallback? = null
    ) = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(typedMappers, fallback, mock()),
        internalLogger = mock(),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        drawableToColorMapper = mockDrawableToColorMapper,
        viewUtilsInternal = ViewUtilsInternal(),
        interopViewCallback = interopViewCallback
    )

    /** These trees are tiny and the deadline is generous, so a call is expected to complete in one slice. */
    private fun WindowCopyResult.doneValue(): WindowWalkResult = buildWindow(fakeContext)

    /** A view whose bounds fully cover whatever it's stacked on top of, painted with opaque black. */
    @Suppress("DEPRECATION")
    private fun mockOpaqueCoveringView(bounds: GlobalBounds): View {
        val mockDrawable: ColorDrawable = mock { on { opacity } doReturn PixelFormat.OPAQUE }
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(mockDrawable), any())).thenReturn(Color.BLACK)
        return mockViewWithDefaults(bounds) {
            on { alpha } doReturn 1f
            on { background } doReturn mockDrawable
        }
    }

    @Test
    fun `M retain exact pixel geometry W traverseWindow { coordinates exceed float precision }`() {
        // Given
        val fakePixels = GlobalBounds(-2_000_000_123L, 0, 2_000_000_456L, 100)
        val mockRoot = mockView(fakePixels)
        doAnswer { invocation ->
            val fakeLocation = invocation.getArgument<IntArray>(0)
            fakeLocation[0] = fakePixels.x.toInt()
            fakeLocation[1] = fakePixels.y.toInt()
            null
        }.whenever(mockRoot).getLocationOnScreen(any())
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            drawableToColorMapper = mockDrawableToColorMapper,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        val copiedView = (result as WindowCopyResult.Present).window.views.single()
        assertThat(copiedView.pixelBounds).isEqualTo(fakePixels)
        val normalized = DefaultViewBoundsResolver.resolveViewGlobalBounds(mockRoot, fakeDensity)
        assertThat(copiedView.bounds).isEqualTo(
            CapturedBounds(normalized.x, normalized.y, normalized.width, normalized.height)
        )
    }

    @Test
    fun `M skip reparented child W traverseWindow { distinct parents compare equal }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockFirstChild = mockView(fakeBounds)
        val mockMovedChild = mockView(fakeBounds)
        val fakeOriginalParent = EqualParent()
        val fakeReplacementParent = EqualParent()
        whenever(mockMovedChild.parent).thenReturn(fakeOriginalParent)
        mockRoot.stub {
            on { childCount } doReturn 2
            on { getChildAt(0) } doReturn mockFirstChild
            on { getChildAt(1) } doReturn mockMovedChild
        }
        val fakeReparentingMapper = CapturedViewMapper<View> { view, _ ->
            if (view === mockFirstChild) {
                whenever(mockMovedChild.parent).thenReturn(fakeReplacementParent)
            }
            CapturedViewMapperResult.None
        }

        // When
        val result = traversal(fallback = fakeReparentingMapper).traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        assertThat(fakeReplacementParent).isEqualTo(fakeOriginalParent).isNotSameAs(fakeOriginalParent)
        assertThat((result as WindowCopyResult.Present).window.views).hasSize(2)
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockMovedChild)
    }

    @Test
    fun `M drop the child W traverseWindow() { not visible }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockHiddenChild = mockView(fakeChildBounds).apply { whenever(isShown).thenReturn(false) }
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockHiddenChild)
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
        assertThat(present.layers).hasSize(1)
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
    }

    @Test
    fun `M drop the child W traverseWindow() { system noise }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockViewStubChild: ViewStub = mockViewWithDefaults(fakeChildBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockViewStubChild)
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
    }

    @Test
    fun `M emit a placeholder and not recurse W traverseWindow() { hidden tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHiddenBounds: GlobalBounds,
        @Forgery fakeGrandChildBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockHiddenGroup = mockViewGroup(fakeHiddenBounds)
        val mockGrandChild = mockView(fakeGrandChildBounds)
        mockHiddenGroup.stub {
            on { getTag(any()) } doReturn true
            on { childCount } doReturn 1
            on { getChildAt(0) } doReturn mockGrandChild
        }
        mockRoot.withChildren(mockHiddenGroup)
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val hiddenLayer = present.layers.first { it.identity != present.rootLayer.identity }
        assertThat(hiddenLayer.children).hasSize(1)
        val placeholder = present.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo("Hidden")
        assertThat(present.layers).hasSize(2)
    }

    @Test
    fun `M stop at WebView slot and continue siblings W traverseWindow() { WebView has native descendants }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockWebView: WebView = mockViewWithDefaults(fakeBounds)
        val mockImplementationChild = mockViewGroup(fakeBounds)
        val mockImplementationGrandchild = mockView(fakeBounds)
        mockWebView.withChildren(mockImplementationChild)
        mockImplementationChild.withChildren(mockImplementationGrandchild)
        val mockSibling = mockViewGroup(fakeBounds)
        val mockSiblingChild = mockView(fakeBounds)
        mockSibling.withChildren(mockSiblingChild)
        mockRoot.withChildren(mockWebView, mockSibling)
        val mockSlotId = mockViewIdentifierResolver.resolveViewId(mockWebView)
        val testedTraversal = webViewTraversal(fallback = markerMapper)

        // When
        val present = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        val slot = present.wireframes.filterIsInstance<CapturedWireframe.WebView>().single()
        assertThat(slot.identity.wireId).isEqualTo(mockSlotId)
        assertThat(present.layers).hasSize(4)
        assertThat(present.wireframes).hasSize(4)
        val webViewLayer = present.layers.single { it.identity.localId == mockSlotId.toString() }
        assertThat(webViewLayer.children).containsExactly(CapturedChild.Wireframe(slot.identity))
        verify(mockWebView, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockImplementationChild)
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockImplementationGrandchild)
        verify(mockViewIdentifierResolver).resolveViewId(mockSiblingChild)
    }

    @Test
    fun `M hide WebView before mapping slot W traverseWindow() { WebView has hidden tag and native child }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockWebView: WebView = mockViewWithDefaults(fakeBounds)
        whenever(mockWebView.getTag(R.id.datadog_hidden)).thenReturn(true)
        val mockImplementationChild = mockView(fakeBounds)
        mockWebView.withChildren(mockImplementationChild)
        mockRoot.withChildren(mockWebView)
        val testedTraversal = webViewTraversal()

        // When
        val present = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(present.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(present.layers).hasSize(2)
        verify(mockWebView, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockImplementationChild)
    }

    @Test
    fun `M traverse mapped container children W traverseWindow() { dedicated mapper only describes own view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockChild = mockViewGroup(fakeBounds)
        val mockGrandchild = mockView(fakeBounds)
        mockRoot.withChildren(mockChild)
        mockChild.withChildren(mockGrandchild)
        val testedTraversal = traversal(
            fallback = markerMapper,
            typedMappers = listOf(
                CapturedMapperTypeWrapper(
                    ViewGroup::class.java,
                    CapturedViewMapper<ViewGroup> { view, context -> markerMapper.map(view, context) }
                )
            )
        )

        // When
        val present = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(present.layers).hasSize(3)
        assertThat(present.wireframes).hasSize(3)
        verify(mockViewIdentifierResolver).resolveViewId(mockGrandchild)
    }

    @Test
    fun `M preserve child order W traverseWindow() { multiple children }`(forge: Forge) {
        // Given
        val mockRoot = mockViewGroup(forge.getForgery<GlobalBounds>())
        val fakeChildren = List(3) { mockView(forge.getForgery<GlobalBounds>()) }
        whenever(mockRoot.childCount).thenReturn(fakeChildren.size)
        fakeChildren.forEachIndexed { index, child -> whenever(mockRoot.getChildAt(index)).thenReturn(child) }
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val childIdentities = present.rootLayer.children.map { it.identity }
        val expectedOrder = fakeChildren.map { child ->
            present.layers.first { layer ->
                layer.identity.localId == mockViewIdentifierResolver.resolveViewId(child).toString()
            }.identity
        }
        assertThat(childIdentities).isEqualTo(expectedOrder)
    }

    @Test
    fun `M compute clip against ancestor bounds W traverseWindow() { child overflows parent }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 500L) fakeRightOverflow: Long,
        @LongForgery(min = 1L, max = 500L) fakeBottomOverflow: Long
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val fakeChildBounds = GlobalBounds(
            x = fakeRootBounds.x,
            y = fakeRootBounds.y,
            width = fakeRootBounds.width + fakeRightOverflow,
            height = fakeRootBounds.height + fakeBottomOverflow
        )
        val mockChild = mockView(fakeChildBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal(
            fallback = markerMapper
        ).traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val childWireframe = present.wireframes.first {
            it.bounds.x == fakeChildBounds.x &&
                it.bounds.width == fakeChildBounds.width
        }
        assertThat(childWireframe.clip).isEqualTo(
            CapturedClip(top = null, bottom = fakeBottomOverflow, left = null, right = fakeRightOverflow)
        )
    }

    @Test
    fun `M abort the whole capture W traverseWindow() { deadline expires mid walk }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 1000L) fakeDeadlineNs: Long
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val fakeChildren = List(5) { mockView(fakeRootBounds) }
        whenever(mockRoot.childCount).thenReturn(fakeChildren.size)
        fakeChildren.forEachIndexed { index, child -> whenever(mockRoot.getChildAt(index)).thenReturn(child) }
        val fakeWindowIdentity = identityFactory.window("window")
        var fakeCalls = 0
        val fakeExpiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = fakeDeadlineNs,
            timeProvider = CaptureTimeProvider {
                fakeCalls++
                if (fakeCalls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeExpiringContext)

        // Then
        assertThat(result.doneValue()).isEqualTo(WindowWalkResult.Aborted)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M hand off typed Compose host without visiting native children W traverseWindow`(
        fakeReturnsWireframes: Boolean,
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeRootBounds)
        val mockComposeChild = mockView(fakeRootBounds)
        whenever(mockComposeHost.childCount).thenReturn(1)
        whenever(mockComposeHost.getChildAt(0)).thenReturn(mockComposeChild)
        val fakeMapping = if (fakeReturnsWireframes) {
            CapturedViewMapperResult.Wireframes(emptyList())
        } else {
            CapturedViewMapperResult.None
        }
        val mockComposeMapper: CapturedViewMapper<ComposeView> = mock()
        whenever(mockComposeMapper.map(eq(mockComposeHost), any())).thenReturn(fakeMapping)
        val fakeInteropCallback = CapturedInteropViewCallback { view, context ->
            (view as? ComposeView)?.let { mockComposeMapper.map(it, context) }
        }

        // When
        val result = traversal(interopViewCallback = fakeInteropCallback).traverseWindow(
            mockComposeHost,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        verify(mockComposeMapper).map(eq(mockComposeHost), isA())
        verify(mockComposeHost, never()).getChildAt(any())
        assertThat((result as WindowCopyResult.Present).window.views).hasSize(1)
    }

    @Test
    fun `M hand off non-Compose host W traverseWindow { integration recognizes view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockChild = mockView(fakeBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val fakeInteropCallback = CapturedInteropViewCallback { view, _ ->
            if (view === mockRoot) CapturedViewMapperResult.None else null
        }

        // When
        val result = traversal(interopViewCallback = fakeInteropCallback).traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        assertThat((result as WindowCopyResult.Present).window.views).hasSize(1)
        verify(mockRoot, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockChild)
    }

    @Test
    fun `M retain native mapping and children W traverseWindow { integration declines view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeBounds)
        val mockChild = mockView(fakeBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val mockInteropCallback: CapturedInteropViewCallback = mock()
        val testedTraversal = traversal(fallback = markerMapper, interopViewCallback = mockInteropCallback)

        // When
        val result = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        verify(mockInteropCallback).tryMap(eq(mockRoot), isA())
        verify(mockInteropCallback).tryMap(eq(mockChild), isA())
        assertThat(result.layers).hasSize(2)
        assertThat(result.wireframes).hasSize(2)
    }

    @Test
    fun `M retain hidden placeholder W traverseWindow { interop callback supplied }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeBounds)
        whenever(mockComposeHost.getTag(R.id.datadog_hidden)).thenReturn(true)
        val mockInteropCallback: CapturedInteropViewCallback = mock()

        // When
        val result = traversal(interopViewCallback = mockInteropCallback).traverseWindow(
            mockComposeHost,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        verifyNoInteractions(mockInteropCallback)
        assertThat(result.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M abort W traverseWindow() { deadline expires during compose host handoff, before next checkpoint tick }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 1000L) fakeDeadlineNs: Long
    ) {
        // Given
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeRootBounds)
        val fakeWindowIdentity = identityFactory.window("window")
        var fakeCalls = 0
        val fakeExpiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = fakeDeadlineNs,
            timeProvider = CaptureTimeProvider {
                fakeCalls++
                if (fakeCalls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            interopViewCallback = CapturedInteropViewCallback { view, _ ->
                if (view is ComposeView) CapturedViewMapperResult.None else null
            }
        )

        // When
        val result = testedTraversal.traverseWindow(
            mockComposeHost,
            fakeWindowIdentity,
            identityFactory,
            fakeExpiringContext
        )

        // Then
        assertThat(result.doneValue()).isEqualTo(WindowWalkResult.Aborted)
    }

    @Test
    fun `M retain local mapper clip W traverseWindow { ancestor clip does not restrict drawing }`() {
        // Given
        val mockRoot = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val fakeMapper = CapturedViewMapper<View> { _, context ->
            CapturedViewMapperResult.Wireframes(
                CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(context.ownerIdentity),
                    CapturedBounds(0, 0, 100, 100),
                    clip = CapturedClip(left = 20)
                )
            )
        }

        // When
        val result = traversal(fallback = fakeMapper).traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(result.wireframes.single().clip).isEqualTo(CapturedClip(left = 20))
    }

    @Test
    fun `M intersect local mapper clip with ancestor clip W traverseWindow`() {
        // Given
        val mockRoot = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val fakeMapper = CapturedViewMapper<View> { _, context ->
            CapturedViewMapperResult.Wireframes(
                CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(context.ownerIdentity),
                    CapturedBounds(-10, -20, 130, 150),
                    clip = CapturedClip(top = 5, bottom = 40, left = 25, right = 5)
                )
            )
        }

        // When
        val result = traversal(fallback = fakeMapper).traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(
            result.wireframes.single().clip
        ).isEqualTo(CapturedClip(top = 20, bottom = 40, left = 25, right = 20))
    }

    @Test
    fun `M add a touch override area W traverseWindow() { view tagged with touch privacy }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockChild = mockView(fakeChildBounds)
        whenever(mockChild.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(mockChild.getLocationOnScreen(any())).thenAnswer {
            val fakeLocation = it.getArgument<IntArray>(0)
            fakeLocation[0] = fakeLocationX
            fakeLocation[1] = fakeLocationY
            null
        }
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + mockChild.width,
            fakeLocationY + mockChild.height
        )
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M not add a touch override area W traverseWindow() { view has no touch privacy tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockChild = mockView(fakeChildBounds)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
    }

    @Test
    fun `M log an error and not add an area W traverseWindow() { invalid touch privacy tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @StringForgery fakeInvalidValue: String
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockChild = mockView(fakeChildBounds)
        whenever(mockChild.getTag(R.id.datadog_touch_privacy)).thenReturn(fakeInvalidValue)
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)
        val fakeWindowIdentity = identityFactory.window("window")
        val mockInternalLogger: InternalLogger = mock()
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mockInternalLogger,
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
            "Invalid privacy level",
            throwableClass = IllegalArgumentException::class.java
        )
    }

    @Test
    fun `M retain occluded touch overrides W traverseWindow() { opaque sibling covers view }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockVictim = mockView(fakeChildBounds)
        whenever(mockVictim.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(mockVictim.getLocationOnScreen(any())).thenAnswer {
            val fakeLocation = it.getArgument<IntArray>(0)
            fakeLocation[0] = fakeLocationX
            fakeLocation[1] = fakeLocationY
            null
        }
        val mockCoveringView = mockOpaqueCoveringView(fakeChildBounds)
        mockRoot.stub {
            on { childCount } doReturn 2
            on { getChildAt(0) } doReturn mockVictim
            on { getChildAt(1) } doReturn mockCoveringView
        }
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes).hasSize(2)
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + mockVictim.width,
            fakeLocationY + mockVictim.height
        )
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M still map both children W traverseWindow() { later sibling only partially overlaps }`(
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val fakeVictimBounds = GlobalBounds(x = 0L, y = 0L, width = 100L, height = 100L)
        val fakePartialCoverBounds = GlobalBounds(x = 0L, y = 0L, width = 50L, height = 50L)
        val mockVictim = mockView(fakeVictimBounds)
        val mockPartialCover = mockOpaqueCoveringView(fakePartialCoverBounds)
        mockRoot.stub {
            on { childCount } doReturn 2
            on { getChildAt(0) } doReturn mockVictim
            on { getChildAt(1) } doReturn mockPartialCover
        }
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes).hasSize(3)
    }

    @Test
    fun `M still map both children W traverseWindow() { later sibling fully overlaps but is translucent }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockVictim = mockView(fakeChildBounds)
        val mockTranslucentCover = mockOpaqueCoveringView(fakeChildBounds).apply {
            whenever(alpha).thenReturn(0.5f)
        }
        mockRoot.stub {
            on { childCount } doReturn 2
            on { getChildAt(0) } doReturn mockVictim
            on { getChildAt(1) } doReturn mockTranslucentCover
        }
        val fakeWindowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(mockRoot, fakeWindowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes).hasSize(3)
    }

    private class EqualParent : ViewParent by mock<ViewParent>() {
        override fun equals(other: Any?): Boolean = other is EqualParent

        override fun hashCode(): Int = 0
    }
}
