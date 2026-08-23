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
import android.view.ViewStub
import android.webkit.WebView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedWebViewMapper
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.utils.DefaultViewBoundsResolver
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
            listOf(
                CapturedWireframe.Shape(
                    identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                    bounds = CapturedBounds(bounds.x, bounds.y, bounds.width, bounds.height)
                )
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

    private fun mockView(bounds: GlobalBounds): View {
        val view: View = mock()
        stubDefaults(view, bounds)
        return view
    }

    private fun mockViewGroup(bounds: GlobalBounds): ViewGroup {
        val view: ViewGroup = mock()
        whenever(view.clipChildren).thenReturn(true)
        stubDefaults(view, bounds)
        return view
    }

    private fun stubDefaults(view: View, bounds: GlobalBounds) {
        whenever(view.isShown).thenReturn(true)
        whenever(view.scaleX).thenReturn(1f)
        whenever(view.scaleY).thenReturn(1f)
        whenever(view.left).thenReturn(bounds.x.toInt())
        whenever(view.top).thenReturn(bounds.y.toInt())
        whenever(view.width).thenReturn(bounds.width.toInt().coerceAtLeast(1))
        whenever(view.height).thenReturn(bounds.height.toInt().coerceAtLeast(1))
        whenever(view.getTag(any())).thenReturn(null)
        whenever(mockViewIdentifierResolver.resolveViewId(view)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, fakeDensity)).thenReturn(bounds)
        val mockResources: Resources = mock()
        val metrics = DisplayMetrics().apply { density = fakeDensity }
        whenever(mockResources.displayMetrics).thenReturn(metrics)
        whenever(view.resources).thenReturn(mockResources)
    }

    private fun traversal(
        fallback: CapturedViewMapper<View> = noOpFallback,
        typedMappers: List<CapturedMapperTypeWrapper<*>> = emptyList(),
        composeHostDecomposer: CompositionHostDecomposer? = null,
        isComposeHost: (View) -> Boolean = { false }
    ) = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(typedMappers, fallback, mock()),
        internalLogger = mock(),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        drawableToColorMapper = mockDrawableToColorMapper,
        viewUtilsInternal = ViewUtilsInternal(),
        composeHostDecomposer = composeHostDecomposer,
        isComposeHost = isComposeHost
    )

    /** These trees are tiny and the deadline is generous, so a call is expected to complete in one slice. */
    private fun WindowCopyResult.doneValue(): WindowWalkResult = buildWindow(fakeContext)

    /** A view whose bounds fully cover whatever it's stacked on top of, painted with opaque black. */
    @Suppress("DEPRECATION")
    private fun mockOpaqueCoveringView(bounds: GlobalBounds): View {
        val view = mockView(bounds)
        val drawable: ColorDrawable = mock()
        whenever(drawable.opacity).thenReturn(PixelFormat.OPAQUE)
        whenever(view.alpha).thenReturn(1f)
        whenever(view.scaleX).thenReturn(1f)
        whenever(view.scaleY).thenReturn(1f)
        whenever(view.background).thenReturn(drawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(drawable), any())).thenReturn(Color.BLACK)
        return view
    }

    @Test
    fun `M retain exact pixel geometry W traverseWindow { coordinates exceed float precision }`() {
        // Given
        val fakePixels = GlobalBounds(-2_000_000_123L, 0, 2_000_000_456L, 100)
        val root = mockView(fakePixels)
        doAnswer { invocation ->
            val location = invocation.getArgument<IntArray>(0)
            location[0] = fakePixels.x.toInt()
            location[1] = fakePixels.y.toInt()
            null
        }.whenever(root).getLocationOnScreen(any())
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            drawableToColorMapper = mockDrawableToColorMapper,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        val copiedView = (result as WindowCopyResult.Present).window.views.single()
        assertThat(copiedView.pixelBounds).isEqualTo(fakePixels)
        val normalized = DefaultViewBoundsResolver.resolveViewGlobalBounds(root, fakeDensity)
        assertThat(copiedView.bounds).isEqualTo(
            CapturedBounds(normalized.x, normalized.y, normalized.width, normalized.height)
        )
    }

    @Test
    fun `M drop the child W visit { not visible }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val hiddenChild = mockView(fakeChildBounds).apply { whenever(isShown).thenReturn(false) }
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(hiddenChild)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
        assertThat(present.layers).hasSize(1) // only the window root itself
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
    }

    @Test
    fun `M drop the child W visit { system noise }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val viewStubChild: ViewStub = mock()
        stubDefaults(viewStubChild, fakeChildBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(viewStubChild)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
    }

    @Test
    fun `M not recurse into native children W visit { fallback mapper returns a pixel wireframe }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakePixelGroupBounds: GlobalBounds,
        @Forgery fakeGrandChildBounds: GlobalBounds
    ) {
        // Given: [View.draw] already bakes grandChild into the bitmap this mapper rasterized -
        // walking it again as a separate native child would only double-describe it.
        val root = mockViewGroup(fakeRootBounds)
        val pixelGroup = mockViewGroup(fakePixelGroupBounds)
        val grandChild = mockView(fakeGrandChildBounds)
        whenever(pixelGroup.childCount).thenReturn(1)
        whenever(pixelGroup.getChildAt(0)).thenReturn(grandChild)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(pixelGroup)
        val pixelOnlyForGroup = CapturedViewMapper<View> { view, mappingContext ->
            if (view === pixelGroup) {
                CapturedViewMapperResult.Wireframes(
                    listOf(
                        CapturedWireframe.Pixel(
                            identity = mappingContext.identityFactory.imageWireframe(mappingContext.ownerIdentity),
                            bounds = CapturedBounds(0, 0, 10, 10),
                            resource = PixelResource.Unresolved
                        )
                    )
                )
            } else {
                CapturedViewMapperResult.None
            }
        }
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = pixelOnlyForGroup)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val pixelGroupLayer = present.layers.first { it.identity != present.rootLayer.identity }
        assertThat(pixelGroupLayer.children).hasSize(1) // the pixel wireframe only, no native child layer
        assertThat(present.wireframes.single()).isInstanceOf(CapturedWireframe.Pixel::class.java)
        // No layer was created for grandChild since a terminal pixel wireframe skips recursion.
        assertThat(present.layers).hasSize(2) // root + pixelGroup only
    }

    @Test
    fun `M emit a placeholder and not recurse W visit { hidden tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHiddenBounds: GlobalBounds,
        @Forgery fakeGrandChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val hiddenGroup = mockViewGroup(fakeHiddenBounds)
        whenever(hiddenGroup.getTag(any())).thenReturn(true)
        val grandChild = mockView(fakeGrandChildBounds)
        whenever(hiddenGroup.childCount).thenReturn(1)
        whenever(hiddenGroup.getChildAt(0)).thenReturn(grandChild)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(hiddenGroup)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val hiddenLayer = present.layers.first { it.identity != present.rootLayer.identity }
        assertThat(hiddenLayer.children).hasSize(1)
        val placeholder = present.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo("Hidden")
        // No layer was created for grandChild since the hidden view's children are never visited.
        assertThat(present.layers).hasSize(2) // root + hiddenGroup only
    }

    @Test
    fun `M stop at WebView slot and continue siblings W visit { WebView has native descendants }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeBounds)
        val mockWebView: WebView = mock()
        stubDefaults(mockWebView, fakeBounds)
        val implementationChild = mockViewGroup(fakeBounds)
        val implementationGrandchild = mockView(fakeBounds)
        whenever(mockWebView.childCount).thenReturn(1)
        whenever(mockWebView.getChildAt(0)).thenReturn(implementationChild)
        whenever(implementationChild.childCount).thenReturn(1)
        whenever(implementationChild.getChildAt(0)).thenReturn(implementationGrandchild)
        val sibling = mockViewGroup(fakeBounds)
        val siblingChild = mockView(fakeBounds)
        whenever(sibling.childCount).thenReturn(1)
        whenever(sibling.getChildAt(0)).thenReturn(siblingChild)
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(mockWebView)
        whenever(root.getChildAt(1)).thenReturn(sibling)
        val slotId = mockViewIdentifierResolver.resolveViewId(mockWebView)
        val testedTraversal = traversal(
            fallback = markerMapper,
            typedMappers = listOf(
                CapturedMapperTypeWrapper(
                    WebView::class.java,
                    CapturedWebViewMapper(mockViewIdentifierResolver, mockViewBoundsResolver)
                )
            )
        )

        // When
        val present = testedTraversal.traverseWindow(
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then: root, the WebView slot, and the ordinary sibling subtree only.
        val slot = present.wireframes.filterIsInstance<CapturedWireframe.WebView>().single()
        assertThat(slot.identity.wireId).isEqualTo(slotId)
        assertThat(present.layers).hasSize(4)
        assertThat(present.wireframes).hasSize(4)
        val webViewLayer = present.layers.single { it.identity.localId == slotId.toString() }
        assertThat(webViewLayer.children).containsExactly(CapturedChild.Wireframe(slot.identity))
        verify(mockWebView, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(implementationChild)
        verify(mockViewIdentifierResolver, never()).resolveViewId(implementationGrandchild)
        verify(mockViewIdentifierResolver).resolveViewId(siblingChild)
    }

    @Test
    fun `M hide WebView before mapping slot W visit { WebView has hidden tag and native child }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeBounds)
        val mockWebView: WebView = mock()
        stubDefaults(mockWebView, fakeBounds)
        whenever(mockWebView.getTag(R.id.datadog_hidden)).thenReturn(true)
        val implementationChild = mockView(fakeBounds)
        whenever(mockWebView.childCount).thenReturn(1)
        whenever(mockWebView.getChildAt(0)).thenReturn(implementationChild)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(mockWebView)
        val testedTraversal = traversal(
            typedMappers = listOf(
                CapturedMapperTypeWrapper(
                    WebView::class.java,
                    CapturedWebViewMapper(mockViewIdentifierResolver, mockViewBoundsResolver)
                )
            )
        )

        // When
        val present = testedTraversal.traverseWindow(
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(present.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(present.layers).hasSize(2)
        verify(mockWebView, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(implementationChild)
    }

    @Test
    fun `M traverse mapped container children W visit { dedicated mapper only describes own view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeBounds)
        val child = mockViewGroup(fakeBounds)
        val grandchild = mockView(fakeBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        whenever(child.childCount).thenReturn(1)
        whenever(child.getChildAt(0)).thenReturn(grandchild)
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
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(present.layers).hasSize(3)
        assertThat(present.wireframes).hasSize(3)
        verify(mockViewIdentifierResolver).resolveViewId(grandchild)
    }

    @Test
    fun `M preserve child order W visit { multiple children }`(forge: Forge) {
        // Given
        val root = mockViewGroup(forge.getForgery<GlobalBounds>())
        val children = List(3) { mockView(forge.getForgery<GlobalBounds>()) }
        whenever(root.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val childIdentities = present.rootLayer.children.map { it.identity }
        val expectedOrder = children.map { child ->
            present.layers.first { layer ->
                layer.identity.localId == mockViewIdentifierResolver.resolveViewId(child).toString()
            }.identity
        }
        assertThat(childIdentities).isEqualTo(expectedOrder)
    }

    @Test
    fun `M compute clip against ancestor bounds W child overflows parent`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 500L) fakeRightOverflow: Long,
        @LongForgery(min = 1L, max = 500L) fakeBottomOverflow: Long
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val childBounds = GlobalBounds(
            x = fakeRootBounds.x,
            y = fakeRootBounds.y,
            width = fakeRootBounds.width + fakeRightOverflow,
            height = fakeRootBounds.height + fakeBottomOverflow
        )
        val child = mockView(childBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(
            fallback = markerMapper
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val childWireframe = present.wireframes.first {
            it.bounds.x == childBounds.x &&
                it.bounds.width == childBounds.width
        }
        assertThat(childWireframe.clip).isEqualTo(
            CapturedClip(top = null, bottom = fakeBottomOverflow, left = null, right = fakeRightOverflow)
        )
    }

    @Test
    fun `M abort the whole capture W deadline expires mid walk`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 1000L) fakeDeadlineNs: Long
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val children = List(5) { mockView(fakeRootBounds) }
        whenever(root.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
        val windowIdentity = identityFactory.window("window")
        var calls = 0
        val expiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = fakeDeadlineNs,
            timeProvider = CaptureTimeProvider {
                calls++
                // Not expired for the root's own pre-pop checks (checked every item now, not just
                // periodically), expired from the first child's pre-pop check onward.
                if (calls <= 2) 0L else fakeDeadlineNs * 2
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
        val result = testedTraversal.traverseWindow(root, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result.doneValue()).isEqualTo(WindowWalkResult.Aborted)
    }

    @Test
    fun `M abort W deadline expires during compose host decomposition`() {
        // Given
        val root = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val composeHost = mockView(GlobalBounds(0, 0, 100, 100))
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")
        var now = 0L
        val expiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = 100L,
            timeProvider = CaptureTimeProvider { now }
        )
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(composeHost)).thenReturn(true)
        whenever(decomposer.decompose(eq(composeHost), any())).thenAnswer {
            now = 100L
            CompositionHostDecomposeResult(emptyList(), emptyList(), emptyList())
        }

        // When
        val result = traversal(
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result).isEqualTo(WindowCopyResult.Aborted)
        verify(decomposer).decompose(eq(composeHost), any())
    }

    // region touch privacy tests

    @Test
    fun `M add a touch override area W visit { view tagged with touch privacy }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(child.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(child.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = fakeLocationX
            location[1] = fakeLocationY
            null
        }
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + child.width,
            fakeLocationY + child.height
        )
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M not add a touch override area W visit { view has no touch privacy tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
    }

    @Test
    fun `M log an error and not add an area W visit { invalid touch privacy tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @StringForgery fakeInvalidValue: String
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(child.getTag(R.id.datadog_touch_privacy)).thenReturn(fakeInvalidValue)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")
        val mockInternalLogger: InternalLogger = mock()
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mockInternalLogger,
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.ERROR),
            targets = eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
            messageBuilder = any(),
            throwable = any(),
            onlyOnce = eq(false),
            additionalProperties = isNull()
        )
    }

    // endregion

    // region occlusion culling tests

    @Test
    fun `M skip mapping but still register touch override area W visit { fully covered by later opaque sibling }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val victim = mockView(fakeChildBounds)
        whenever(victim.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(victim.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = fakeLocationX
            location[1] = fakeLocationY
            null
        }
        // Drawn after (on top of) victim, with identical bounds - fully covers it.
        val coveringView = mockOpaqueCoveringView(fakeChildBounds)
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(victim)
        whenever(root.getChildAt(1)).thenReturn(coveringView)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        // Only the window root and the covering sibling were mapped - the occluded victim
        // contributed no wireframe of its own.
        assertThat(present.wireframes).hasSize(2)
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + victim.width,
            fakeLocationY + victim.height
        )
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M still map both children W visit { later sibling only partially overlaps }`(
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val victimBounds = GlobalBounds(x = 0L, y = 0L, width = 100L, height = 100L)
        val partialCoverBounds = GlobalBounds(x = 0L, y = 0L, width = 50L, height = 50L)
        val victim = mockView(victimBounds)
        val partialCover = mockOpaqueCoveringView(partialCoverBounds)
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(victim)
        whenever(root.getChildAt(1)).thenReturn(partialCover)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        // Root + both children - the partial cover doesn't fully contain the victim, so nothing is culled.
        assertThat(present.wireframes).hasSize(3)
    }

    @Test
    fun `M still map both children W visit { later sibling fully overlaps but is translucent }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val victim = mockView(fakeChildBounds)
        val translucentCover = mockOpaqueCoveringView(fakeChildBounds).apply {
            whenever(alpha).thenReturn(0.5f)
        }
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(victim)
        whenever(root.getChildAt(1)).thenReturn(translucentCover)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        // Root + both children - a translucent cover never counts as fully opaque, so nothing is culled.
        assertThat(present.wireframes).hasSize(3)
    }

    // endregion

    // region compose decomposition tests

    @Test
    fun `M invoke the decomposer and splice its result W visit { compose host }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHostBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val composeHost = mockView(fakeHostBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")

        var capturedHostIdentity: CapturedIdentity? = null
        var capturedHostBounds: CapturedBounds? = null
        val decomposer = object : CompositionHostDecomposer {
            override fun canDecompose(view: View) = view === composeHost
            override fun decompose(
                view: View,
                request: CompositionHostDecomposeRequest
            ): CompositionHostDecomposeResult {
                capturedHostIdentity = request.hostIdentity
                capturedHostBounds = request.hostBounds
                val nodeIdentity = request.identityFactory.composeNode(request.hostIdentity, "node")
                val wireframeIdentity = request.identityFactory.shapeWireframe(nodeIdentity)
                val wireframe = CapturedWireframe.Shape(
                    identity = wireframeIdentity,
                    bounds = fakeHostBounds.toCapturedBounds()
                )
                val node = CapturedLayer(
                    identity = nodeIdentity,
                    kind = CapturedLayerKind.COMPOSE_NODE,
                    bounds = fakeHostBounds.toCapturedBounds(),
                    children = listOf(CapturedChild.Wireframe(wireframeIdentity))
                )
                return CompositionHostDecomposeResult(
                    rootChildren = listOf(CapturedChild.Layer(nodeIdentity)),
                    nodes = listOf(node),
                    wireframes = listOf(wireframe)
                )
            }
        }

        // When
        val result = traversal(
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val hostLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_HOST }
        val nodeLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_NODE }
        assertThat(capturedHostIdentity).isEqualTo(hostLayer.identity)
        assertThat(capturedHostBounds).isEqualTo(hostLayer.bounds)
        assertThat(hostLayer.children).containsExactly(CapturedChild.Layer(nodeLayer.identity))
        assertThat(present.wireframes).hasSize(1)
        assertThat(present.layers).hasSize(3) // root + compose host + compose node
    }

    @Test
    fun `M fall back to the mapper W visit { compose host, decomposer cannot decompose }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHostBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val composeHost = mockView(fakeHostBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(composeHost)).thenReturn(false)

        // When
        val result = traversal(
            fallback = markerMapper,
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then - the host's layer keeps kind COMPOSE_HOST (its identity was already minted as one
        // via composeHost(), which the layer's kind must stay consistent with), but its content is
        // mapped by the fallback mapper like any other unmapped View - never as a Compose subtree -
        // in addition to the window root's own fallback-mapped wireframe.
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes).hasSize(2)
        assertThat(present.wireframes.map { it.bounds.x }).contains(fakeHostBounds.x)
        val hostLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_HOST }
        assertThat(hostLayer.children).hasSize(1)
    }

    @Test
    fun `M splice the native handoff subtree W decomposer hands back an interop view`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHostBounds: GlobalBounds,
        @Forgery fakeInteropBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val composeHost = mockView(fakeHostBounds)
        val interopView = mockView(fakeInteropBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")

        val decomposer = object : CompositionHostDecomposer {
            override fun canDecompose(view: View) = view === composeHost
            override fun decompose(
                view: View,
                request: CompositionHostDecomposeRequest
            ): CompositionHostDecomposeResult? {
                val childIdentity = request.identityFactory.composeNode(request.hostIdentity, "interop")
                val subtree = request.nativeViewHandoff(interopView, childIdentity) ?: return null
                return CompositionHostDecomposeResult(
                    rootChildren = listOf(CapturedChild.Layer(subtree.rootLayer.identity)),
                    nodes = subtree.layers,
                    wireframes = subtree.wireframes
                )
            }
        }

        // When
        val result = traversal(
            fallback = markerMapper,
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val interopLayer = present.layers.first { it.kind == CapturedLayerKind.NATIVE_VIEW }
        assertThat(interopLayer.bounds.x).isEqualTo(fakeInteropBounds.x)
        val interopWireframe = present.wireframes.first { it.bounds.x == fakeInteropBounds.x }
        assertThat(interopWireframe.identity).isEqualTo(interopLayer.children.single().identity)
    }

    @Test
    fun `M clip Compose output and retain host opacity W traverseWindow { padding and explicit clips }`() {
        // Given
        fakeDensity = 1f
        val root = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val composeHost = mockViewGroup(GlobalBounds(10, 10, 80, 80))
        val implementationChild = mockView(GlobalBounds(10, 10, 80, 80))
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        whenever(composeHost.childCount).thenReturn(1)
        whenever(composeHost.getChildAt(0)).thenReturn(implementationChild)
        whenever(composeHost.alpha).thenReturn(0.5f)
        whenever(composeHost.clipBounds).thenReturn(Rect(5, 5, 65, 65))
        whenever(composeHost.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = 10
            location[1] = 10
            Unit
        }
        whenever(composeHost.clipToPadding).thenReturn(true)
        whenever(composeHost.paddingLeft).thenReturn(10)
        whenever(mockViewBoundsResolver.resolveViewPaddedBounds(composeHost, fakeDensity))
            .thenReturn(GlobalBounds(20, 20, 60, 60))
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(composeHost)).thenReturn(true)
        whenever(decomposer.decompose(eq(composeHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val identity = request.identityFactory.shapeWireframe(request.hostIdentity)
            CompositionHostDecomposeResult(
                listOf(CapturedChild.Wireframe(identity)),
                emptyList(),
                listOf(
                    CapturedWireframe.Shape(identity, CapturedBounds(-10, -10, 120, 120), CapturedClip(top = 40))
                )
            )
        }

        // When
        val result = traversal(
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes.single().clip).isEqualTo(CapturedClip(40, 35, 30, 35))
        val hostLayer = present.layers.single { it.kind == CapturedLayerKind.COMPOSE_HOST }
        assertThat(hostLayer.modifiers).containsExactly(CapturedModifier.Opacity(0.5))
        assertThat(present.layers).hasSize(2)
        verify(mockViewBoundsResolver, never()).resolveViewGlobalBounds(eq(implementationChild), any())
    }

    @Test
    fun `M fully clip offscreen Compose wireframes W traverseWindow { host permits overflowing native children }`() {
        // Given
        fakeDensity = 1f
        val root = mockViewGroup(GlobalBounds(0, 0, 300, 300))
        val composeHost = mockViewGroup(GlobalBounds(50, 50, 100, 100))
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        whenever(root.clipChildren).thenReturn(false)
        whenever(composeHost.clipChildren).thenReturn(false)
        whenever(composeHost.clipToPadding).thenReturn(false)
        val fakeWireframeBounds = listOf(
            CapturedBounds(20, 60, 20, 20),
            CapturedBounds(150, 60, 20, 20),
            CapturedBounds(60, 20, 20, 20),
            CapturedBounds(60, 150, 20, 20),
            CapturedBounds(40, 60, 20, 20)
        )
        val stubDecomposer: CompositionHostDecomposer = mock()
        whenever(stubDecomposer.canDecompose(composeHost)).thenReturn(true)
        whenever(stubDecomposer.decompose(eq(composeHost), any())).thenAnswer { fakeInvocation ->
            val fakeRequest = fakeInvocation.getArgument<CompositionHostDecomposeRequest>(1)
            val fakeWireframes = fakeWireframeBounds.mapIndexed { index, bounds ->
                val fakeNode = fakeRequest.identityFactory.composeNode(fakeRequest.hostIdentity, index.toString())
                CapturedWireframe.Text(
                    identity = fakeRequest.identityFactory.textWireframe(fakeNode),
                    bounds = bounds,
                    text = "overflowing text",
                    textStyle = CapturedTextStyle("sans-serif", 12, "#000000FF")
                )
            }
            CompositionHostDecomposeResult(
                fakeWireframes.map { CapturedChild.Wireframe(it.identity) },
                emptyList(),
                fakeWireframes
            )
        }

        // When
        val result = traversal(
            composeHostDecomposer = stubDecomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val fakeWireframes = (result.doneValue() as WindowWalkResult.Present).wireframes
        assertThat(fakeWireframes).hasSize(5)
        fakeWireframes.take(4).forEach { wireframe ->
            val clip = requireNotNull(wireframe.clip)
            assertThat(
                (clip.left ?: 0) >= wireframe.bounds.width || (clip.right ?: 0) >= wireframe.bounds.width ||
                    (clip.top ?: 0) >= wireframe.bounds.height || (clip.bottom ?: 0) >= wireframe.bounds.height
            ).isTrue()
        }
        assertThat(fakeWireframes.last().clip).isEqualTo(CapturedClip(left = 10))
    }

    @Test
    fun `M preserve interop clips and touch overrides W traverseWindow { native handoff }`() {
        // Given
        fakeDensity = 1f
        val root = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val composeHost = mockView(GlobalBounds(0, 0, 100, 100))
        val interopView = mockView(GlobalBounds(-10, -10, 120, 120))
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        whenever(interopView.clipBounds).thenReturn(Rect(20, 20, 80, 80))
        whenever(interopView.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(interopView.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = -10
            location[1] = -10
            Unit
        }
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(composeHost)).thenReturn(true)
        whenever(decomposer.decompose(eq(composeHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val identity = request.identityFactory.composeNode(request.hostIdentity, "interop")
            val subtree = request.nativeViewHandoff(interopView, identity)!!
            CompositionHostDecomposeResult(
                listOf(CapturedChild.Layer(subtree.rootLayer.identity)),
                subtree.layers,
                subtree.wireframes
            )
        }

        // When
        val result = traversal(
            fallback = markerMapper,
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val interopWireframe = present.wireframes.single { it.bounds.x == -10L }
        assertThat(interopWireframe.clip).isEqualTo(CapturedClip(20, 40, 20, 40))
        assertThat(present.touchOverrideAreas).containsEntry(Rect(-10, -10, 110, 110), TouchPrivacy.HIDE)
        val snapshot = CapturedFullSnapshot(
            timestamp = 0L,
            scope = identityFactory.scope,
            root = CapturedLayer(
                identityFactory.screenRoot(),
                CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
                present.rootLayer.bounds,
                listOf(CapturedChild.Layer(present.rootLayer.identity))
            ),
            layers = present.layers,
            wireframes = present.wireframes
        )
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M retain native children W traverseWindow { decomposer returns null after native handoff }`() {
        // Given
        val root = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val composeHost = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val nativeChild = mockView(GlobalBounds(0, 0, 100, 100))
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        whenever(composeHost.childCount).thenReturn(1)
        whenever(composeHost.getChildAt(0)).thenReturn(nativeChild)
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(composeHost)).thenReturn(true)
        whenever(decomposer.decompose(eq(composeHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val identity = request.identityFactory.composeNode(request.hostIdentity, "interop")
            assertThat(request.nativeViewHandoff(nativeChild, identity)).isNotNull()
            null
        }

        // When
        val result = traversal(
            fallback = markerMapper,
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.layers).hasSize(3)
        assertThat(present.wireframes).hasSize(3)
    }

    @Test
    fun `M hide Compose host and skip its children W traverseWindow { hidden tag }`() {
        // Given
        val root = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val composeHost = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val nativeChild = mockView(GlobalBounds(0, 0, 100, 100))
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        whenever(composeHost.childCount).thenReturn(1)
        whenever(composeHost.getChildAt(0)).thenReturn(nativeChild)
        whenever(composeHost.getTag(R.id.datadog_hidden)).thenReturn(true)
        val mockDecomposer: CompositionHostDecomposer = mock()

        // When
        val result = traversal(
            composeHostDecomposer = mockDecomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(present.layers).hasSize(2)
        verify(mockDecomposer, never()).canDecompose(any())
        verify(mockDecomposer, never()).decompose(any(), any())
    }

    // endregion

    private fun GlobalBounds.toCapturedBounds() = CapturedBounds(x, y, width, height)
}
