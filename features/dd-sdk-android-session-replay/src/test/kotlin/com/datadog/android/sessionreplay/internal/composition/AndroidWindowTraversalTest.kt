/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import com.datadog.android.heatmaps.heatmapViewKey
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.HeatmapIdentifierResolver
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeResult
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.mock
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
        stubDefaults(view, bounds)
        return view
    }

    private fun stubDefaults(view: View, bounds: GlobalBounds) {
        whenever(view.isShown).thenReturn(true)
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
        isComposeHost: (View) -> Boolean = { false },
        heatmapResolver: HeatmapIdentifierResolver? = null
    ) = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(typedMappers, fallback, mock()),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        viewUtilsInternal = ViewUtilsInternal(),
        composeHostDecomposer = composeHostDecomposer,
        isComposeHost = isComposeHost,
        heatmapResolver = heatmapResolver
    )

    private fun makeTapTarget(view: View) {
        whenever(view.isClickable).thenReturn(true)
        whenever(view.visibility).thenReturn(View.VISIBLE)
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
        val present = result as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
        assertThat(present.layers).hasSize(1) // only the window root itself
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
        val present = result as WindowWalkResult.Present
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
        val present = result as WindowWalkResult.Present
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
        val present = result as WindowWalkResult.Present
        val hiddenLayer = present.layers.first { it.identity != present.rootLayer.identity }
        assertThat(hiddenLayer.children).hasSize(1)
        val placeholder = present.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo("Hidden")
        // No layer was created for grandChild since the hidden view's children are never visited.
        assertThat(present.layers).hasSize(2) // root + hiddenGroup only
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
        val present = result as WindowWalkResult.Present
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
        val present = result as WindowWalkResult.Present
        val childWireframe = present.wireframes.first {
            it.bounds.x == childBounds.x &&
                it.bounds.width == childBounds.width
        }
        assertThat(childWireframe.clip).isEqualTo(
            CapturedClip(top = null, bottom = fakeBottomOverflow, left = null, right = fakeRightOverflow)
        )
    }

    // region resolveNativeClipModifier
    //
    // resolveNativeClipModifier is a plain top-level function taking a raw Float? radius - no
    // mocking, no fakes, no traverseWindow()-level integration needed. The only part of native clip
    // support that isn't unit-tested is nativeOutlineRadiusPx's own few lines of real
    // Outline/ViewOutlineProvider interop, which unitTests.isReturnDefaultValues=true makes
    // impossible to observe meaningfully in this environment regardless of how it's structured -
    // see that function's own doc.

    @Test
    fun `M emit a Clip modifier W resolveNativeClipModifier { radius is positive }`(
        @LongForgery(min = 200L, max = 2000L) fakeWidth: Long,
        @LongForgery(min = 200L, max = 2000L) fakeHeight: Long,
        @FloatForgery(min = 1f, max = 50f) fakeRadiusPx: Float,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensity: Float
    ) {
        // Given: width/height stay well above any possible radius/density value here (max 50px /
        // min 0.75 density =~ 66.7, vs. a 100+ half-side floor), so the clamp never engages and the
        // expected radius stays a simple radiusPx/density conversion.
        val bounds = CapturedBounds(0, 0, fakeWidth, fakeHeight)

        // When
        val clip = resolveNativeClipModifier(fakeRadiusPx, bounds, fakeDensity)

        // Then
        val expectedRadius = (fakeRadiusPx / fakeDensity).toDouble()
        assertThat(clip?.path).isEqualTo(
            "M $expectedRadius,0 " +
                "L ${fakeWidth - expectedRadius},0 A $expectedRadius,$expectedRadius 0 0 1 " +
                "$fakeWidth.0,$expectedRadius " +
                "L $fakeWidth.0,${fakeHeight - expectedRadius} A $expectedRadius,$expectedRadius 0 0 1 " +
                "${fakeWidth - expectedRadius},$fakeHeight.0 " +
                "L $expectedRadius,$fakeHeight.0 A $expectedRadius,$expectedRadius 0 0 1 " +
                "0,${fakeHeight - expectedRadius} " +
                "L 0,$expectedRadius A $expectedRadius,$expectedRadius 0 0 1 $expectedRadius,0 Z"
        )
    }

    @Test
    fun `M return null W resolveNativeClipModifier { radius is null }`(
        @Forgery fakeChildBounds: GlobalBounds,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensity: Float
    ) {
        assertThat(resolveNativeClipModifier(null, fakeChildBounds.toCapturedBounds(), fakeDensity)).isNull()
    }

    @Test
    fun `M return null W resolveNativeClipModifier { radius resolves to zero }`(
        @Forgery fakeChildBounds: GlobalBounds,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensity: Float
    ) {
        // Given: a plain rectangular outline has no visual effect the existing per-wireframe
        // ancestor-bounds crop doesn't already provide.
        assertThat(resolveNativeClipModifier(0f, fakeChildBounds.toCapturedBounds(), fakeDensity)).isNull()
    }
    // endregion

    @Test
    fun `M emit a Shadow modifier W visit { view has positive elevation }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given: elevationPx is chosen relative to the test's own (randomized) fakeDensity so it
        // always converts to exactly 4dp, rounding to the Material elevation table's 4dp row -
        // (offsetY=2, blur=4) for the key/umbra layer.
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(child.elevation).thenReturn(4f * fakeDensity)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(
            fallback = markerMapper
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        val childLayer = present.layers.first {
            it.identity.localId == mockViewIdentifierResolver.resolveViewId(child).toString()
        }
        val shadow = childLayer.modifiers.filterIsInstance<CapturedModifier.Shadow>().single()
        assertThat(shadow.offsetX).isEqualTo(0.0)
        assertThat(shadow.offsetY).isEqualTo(2.0)
        assertThat(shadow.radius).isEqualTo(4.0)
    }

    @Test
    fun `M not emit a Shadow modifier W visit { view has zero elevation and translationZ }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given: a flat view (no Z) casts no shadow.
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(
            fallback = markerMapper
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        val childLayer = present.layers.first {
            it.identity.localId == mockViewIdentifierResolver.resolveViewId(child).toString()
        }
        assertThat(childLayer.modifiers.filterIsInstance<CapturedModifier.Shadow>()).isEmpty()
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
                // Not expired for the upfront per-window check and the first checkpoint,
                // expired from the second checkpoint onward.
                if (calls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal(),
            viewsPerCheckpoint = 1
        )

        // When
        val result = testedTraversal.traverseWindow(root, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result).isEqualTo(WindowWalkResult.Aborted)
    }

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
        val decomposer = object : CompositionHostDecomposer {
            override fun canDecompose(view: View) = view === composeHost
            override fun decompose(
                view: View,
                request: CompositionHostDecomposeRequest
            ): CompositionHostDecomposeResult {
                capturedHostIdentity = request.hostIdentity
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
        val present = result as WindowWalkResult.Present
        val hostLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_HOST }
        val nodeLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_NODE }
        assertThat(capturedHostIdentity).isEqualTo(hostLayer.identity)
        assertThat(hostLayer.children).containsExactly(CapturedChild.Layer(nodeLayer.identity))
        assertThat(present.wireframes).hasSize(1)
        assertThat(present.layers).hasSize(3) // root + compose host + compose node
    }

    @Test
    fun `M forward the generation's shouldContinue into the decompose request W visit { compose host }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHostBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val composeHost = mockView(fakeHostBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")

        var capturedShouldContinue: (() -> Boolean)? = null
        val decomposer = object : CompositionHostDecomposer {
            override fun canDecompose(view: View) = view === composeHost
            override fun decompose(
                view: View,
                request: CompositionHostDecomposeRequest
            ): CompositionHostDecomposeResult? {
                capturedShouldContinue = request.shouldContinue
                return null
            }
        }

        // When
        traversal(
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val shouldContinue = requireNotNull(capturedShouldContinue)
        assertThat(shouldContinue.invoke()).isEqualTo(fakeContext.shouldContinue())
    }

    @Test
    fun `M surface a Compose-registered pixel capture through the same pending list as native W visit`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHostBounds: GlobalBounds
    ) {
        // Given - proves a Compose decomposer's pixel captures flow through the exact same
        // pendingPixelCaptures list the native View pixel-fallback path already uses, so they get
        // identical downstream treatment (OCR text masking, resource resolution, the budget-aware
        // detector timeout) with no Compose-specific privacy code needed - see PixelFallbackSnapshotProcessor.
        val root = mockViewGroup(fakeRootBounds)
        val composeHost = mockView(fakeHostBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")
        val mockBitmap: android.graphics.Bitmap = mock()

        val decomposer = object : CompositionHostDecomposer {
            override fun canDecompose(view: View) = view === composeHost
            override fun decompose(
                view: View,
                request: CompositionHostDecomposeRequest
            ): CompositionHostDecomposeResult? {
                val nodeIdentity = request.identityFactory.composeNode(request.hostIdentity, "node")
                val pixelIdentity = request.identityFactory.imageWireframe(nodeIdentity)
                request.pendingPixelCaptureSink.register(
                    PendingPixelCapture(
                        wireframeIdentity = pixelIdentity,
                        ownerIdentity = nodeIdentity,
                        bitmap = mockBitmap
                    )
                )
                return null
            }
        }

        // When
        val result = traversal(
            composeHostDecomposer = decomposer,
            isComposeHost = { it === composeHost }
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        assertThat(present.pendingPixelCaptures).hasSize(1)
        assertThat(present.pendingPixelCaptures.single().bitmap).isSameAs(mockBitmap)
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
        val present = result as WindowWalkResult.Present
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
        val present = result as WindowWalkResult.Present
        val interopLayer = present.layers.first { it.kind == CapturedLayerKind.NATIVE_VIEW }
        assertThat(interopLayer.bounds.x).isEqualTo(fakeInteropBounds.x)
        val interopWireframe = present.wireframes.first { it.bounds.x == fakeInteropBounds.x }
        assertThat(interopWireframe.identity).isEqualTo(interopLayer.children.single().identity)
    }

    @Test
    fun `M attach a permanentId W visit { heatmap resolver configured, view is a valid tap target }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @StringForgery fakeViewUrl: String
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        makeTapTarget(root)
        val windowIdentity = identityFactory.window("window")
        val registry = HeatmapIdentifierRegistry.create()
        val resolver = HeatmapIdentifierResolver(
            appPackageName = "com.example.app",
            registry = registry,
            internalLogger = mock()
        )

        // When
        val result = traversal(fallback = markerMapper, heatmapResolver = resolver)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext, fakeViewUrl)

        // Then
        val present = result as WindowWalkResult.Present
        val wireframe = present.wireframes.single()
        assertThat(wireframe.permanentId).isNotNull()
        assertThat(registry.getHeatmapIdentifier(heatmapViewKey(root), fakeViewUrl)?.rawValue)
            .isEqualTo(wireframe.permanentId)
    }

    @Test
    fun `M not attach a permanentId W visit { no heatmap resolver configured }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @StringForgery fakeViewUrl: String
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        makeTapTarget(root)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext, fakeViewUrl)

        // Then
        val present = result as WindowWalkResult.Present
        assertThat(present.wireframes.single().permanentId).isNull()
    }

    @Test
    fun `M not attach a permanentId W visit { heatmap resolver configured but no viewUrl }`(
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        makeTapTarget(root)
        val windowIdentity = identityFactory.window("window")
        val resolver = HeatmapIdentifierResolver(
            appPackageName = "com.example.app",
            registry = HeatmapIdentifierRegistry.create(),
            internalLogger = mock()
        )

        // When
        val result = traversal(fallback = markerMapper, heatmapResolver = resolver)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        assertThat(present.wireframes.single().permanentId).isNull()
    }

    @Test
    fun `M not attach a permanentId W visit { view is not a valid tap target }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeLeafBounds: GlobalBounds,
        @StringForgery fakeViewUrl: String
    ) {
        // Given: a leaf View that is neither clickable nor a container needing a path for children.
        val root = mockViewGroup(fakeRootBounds)
        makeTapTarget(root)
        val leaf = mockView(fakeLeafBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(leaf)
        val windowIdentity = identityFactory.window("window")
        val resolver = HeatmapIdentifierResolver(
            appPackageName = "com.example.app",
            registry = HeatmapIdentifierRegistry.create(),
            internalLogger = mock()
        )

        // When
        val result = traversal(fallback = markerMapper, heatmapResolver = resolver)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext, fakeViewUrl)

        // Then
        val present = result as WindowWalkResult.Present
        val leafWireframe = present.wireframes.first { it.bounds.x == fakeLeafBounds.x }
        assertThat(leafWireframe.permanentId).isNull()
    }

    @Test
    fun `M not attach a permanentId W native View is reached through a Compose interop handoff`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHostBounds: GlobalBounds,
        @Forgery fakeInteropBounds: GlobalBounds,
        @StringForgery fakeViewUrl: String
    ) {
        // Given: heatmaps for Compose-embedded native content are explicitly out of scope for now.
        val root = mockViewGroup(fakeRootBounds)
        val composeHost = mockView(fakeHostBounds)
        val interopView = mockView(fakeInteropBounds)
        makeTapTarget(interopView)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(composeHost)
        val windowIdentity = identityFactory.window("window")
        val resolver = HeatmapIdentifierResolver(
            appPackageName = "com.example.app",
            registry = HeatmapIdentifierRegistry.create(),
            internalLogger = mock()
        )

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
            isComposeHost = { it === composeHost },
            heatmapResolver = resolver
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext, fakeViewUrl)

        // Then
        val present = result as WindowWalkResult.Present
        val interopWireframe = present.wireframes.first { it.bounds.x == fakeInteropBounds.x }
        assertThat(interopWireframe.permanentId).isNull()
    }

    private fun GlobalBounds.toCapturedBounds() = CapturedBounds(x, y, width, height)
}
