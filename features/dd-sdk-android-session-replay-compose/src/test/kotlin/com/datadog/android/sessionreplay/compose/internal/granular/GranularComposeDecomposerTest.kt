/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.ValueElement
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentityKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframeKind
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class GranularComposeDecomposerTest {

    private val mockSemanticsUtils: SemanticsUtils = mock()
    private val mockReflectionUtils: ReflectionUtils = mock()
    private lateinit var testedGate: GranularComposeCompatibilityGate
    private lateinit var testedDecomposer: GranularComposeDecomposer
    private val mockView: View = mock()

    @BeforeEach
    fun `set up`() {
        testedGate = GranularComposeCompatibilityGate()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate
        )
    }

    @Test
    fun `M return false W canDecompose() { no root semantics node }`() {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(null)

        // When
        val result = testedDecomposer.canDecompose(mockView)

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return false W canDecompose() { gate already tripped }`() {
        // Given
        testedGate.markIncompatible(Throwable(), mock())

        // When
        val result = testedDecomposer.canDecompose(mockView)

        // Then
        assertThat(result).isFalse()
        verifyNoInteractions(mockSemanticsUtils)
    }

    @Test
    fun `M return null W decompose() { gate already tripped }`() {
        // Given
        testedGate.markIncompatible(Throwable(), mock())
        val request: CompositionHostDecomposeRequest = mock()

        // When
        val result = testedDecomposer.decompose(mockView, request)

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M trip the gate and return null W decompose() { runtime throws }`(
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenThrow(NoSuchMethodError(fakeErrorMessage))
        val request: CompositionHostDecomposeRequest = mock()

        // When
        val result = testedDecomposer.decompose(mockView, request)

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isFalse()
        assertThat(testedDecomposer.canDecompose(mockView)).isFalse()
    }

    @Test
    fun `M return null W decompose() { no root semantics node }`() {
        // Given
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(null)
        val request: CompositionHostDecomposeRequest = mock()

        // When
        val result = testedDecomposer.decompose(mockView, request)

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
    }

    // region shouldContinue checkpoint

    private var nextNodeId = 0

    /** A childless node that resolves to nothing (no text, no shape, no interop) - the cheapest leaf shape. */
    private fun mockLeafNode(): SemanticsNode {
        val node: SemanticsNode = mock()
        // getId()'s real implementation reads layoutInfo internally, so this must be stubbed first -
        // otherwise evaluating node.id below throws mid-stub and corrupts Mockito's recording state.
        val layoutInfo: LayoutInfo = mock()
        whenever(node.layoutInfo).thenReturn(layoutInfo)
        whenever(node.id).thenReturn(nextNodeId++)
        whenever(node.children).thenReturn(emptyList())
        whenever(mockSemanticsUtils.isNodeHidden(node)).thenReturn(false)
        whenever(mockReflectionUtils.getInteropView(node)).thenReturn(null)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any())).thenReturn(null)
        whenever(mockSemanticsUtils.resolveBackgroundColor(node)).thenReturn(null)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 10, 10))
        whenever(node.boundsInRoot).thenReturn(androidx.compose.ui.geometry.Rect(0f, 0f, 10f, 10f))
        return node
    }

    private fun mockRoot(children: List<SemanticsNode>): SemanticsNode {
        val root: SemanticsNode = mock()
        whenever(root.children).thenReturn(children)
        return root
    }

    private fun realRequest(
        shouldContinue: () -> Boolean = { true },
        pixelCapturePlaceholderLabelFor: (
            bounds: com.datadog.android.internal.sessionreplay.composition.CapturedBounds
        ) -> String? = { null },
        pendingPixelCaptureSink: PendingPixelCaptureSink = PendingPixelCaptureSink.NoOp
    ): CompositionHostDecomposeRequest {
        val identityFactory = FakeCompositionIdentityFactory()
        return CompositionHostDecomposeRequest(
            identityFactory = identityFactory,
            hostIdentity = identityFactory.anyIdentity(),
            screenDensity = 1f,
            nativeViewHandoff = { _, _ -> null },
            shouldContinue = shouldContinue,
            pixelCapturePlaceholderLabelFor = pixelCapturePlaceholderLabelFor,
            pendingPixelCaptureSink = pendingPixelCaptureSink
        )
    }

    @Test
    fun `M abort and return null W decompose() { shouldContinue denies at the first checkpoint }`() {
        // Given
        val root = mockRoot(List(3) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 1
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { false }))

        // Then
        assertThat(result).isNull()
    }

    @Test
    fun `M complete the walk W decompose() { shouldContinue keeps allowing }`() {
        // Given
        val root = mockRoot(List(5) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 1
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { true }))

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.rootChildren).hasSize(5)
    }

    @Test
    fun `M check shouldContinue only every nodesPerCheckpoint nodes W decompose()`() {
        // Given
        val root = mockRoot(List(6) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 2
        )
        var checkpointCalls = 0

        // When
        testedDecomposer.decompose(mockView, realRequest(shouldContinue = { checkpointCalls++; true }))

        // Then - 6 nodes / checkpoint-every-2 = 3, plus 1 more for the pre-batched-draw re-check
        // that fires because every childless mockLeafNode() here is itself a pixel-capture
        // candidate (see the "pixel capture" test region below).
        assertThat(checkpointCalls).isEqualTo(4)
    }

    // endregion

    // region pixel capture

    private class FakeComposeHostCaptureRasterizer(
        private val bitmapsToReturn: (regions: List<Rect>) -> List<Bitmap?> = { regions -> regions.map { mock() } }
    ) : ComposeHostCaptureRasterizer {
        var callCount = 0
        var lastRegions: List<Rect> = emptyList()

        override fun captureRegions(hostView: View, regions: List<Rect>): List<Bitmap?> {
            callCount++
            lastRegions = regions
            return bitmapsToReturn(regions)
        }
    }

    /** A node with a real drawing effect - a [DrawModifierNode] in its modifier chain. */
    private fun mockDrawingEffectNode(): SemanticsNode {
        val node = mockLeafNode()
        // A node with its own drawing effect commonly has children too (e.g. a Card wrapping
        // content) - non-empty here specifically to prove capture doesn't depend on childlessness.
        // Evaluated before any whenever(...).thenReturn(...) below - mockLeafNode() itself performs
        // several stubbing calls, and nesting a fresh one inside another's still-open .thenReturn(...)
        // argument corrupts Mockito's stubbing recorder (see the block-7 fix for this same mistake
        // earlier in this file).
        val childNode = mockLeafNode()
        val drawModifier = mock<Modifier>(extraInterfaces = arrayOf(DrawModifierNode::class))
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(drawModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.children).thenReturn(listOf(childNode))
        return node
    }

    @Test
    fun `M register a pending capture and emit a Pixel wireframe W decompose() { drawing-effect node }`() {
        // Given
        val node = mockDrawingEffectNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )
        val registered = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it })
        )

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(registered).hasSize(1)
        val pixelWireframe = result!!.wireframes.single() as CapturedWireframe.Pixel
        assertThat(pixelWireframe.resource).isEqualTo(PixelResource.Unresolved)
        assertThat(pixelWireframe.identity).isEqualTo(registered.single().wireframeIdentity)
        // Never recurses into a capture candidate's own children (would double-render).
        assertThat(result.wireframes).hasSize(1)
    }

    @Test
    fun `M register a pending capture W decompose() { childless leaf with no text or shape }`() {
        // Given - mockLeafNode() is itself exactly this: childless, no text, no resolvable background.
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(result!!.wireframes.single()).isInstanceOf(CapturedWireframe.Pixel::class.java)
    }

    @Test
    fun `M call the rasterizer exactly once W decompose() { two capture candidates under one host }`() {
        // Given - the shared-draw batching guarantee: repeated View#draw on the same Compose host
        // within one cycle can corrupt a stateful Painter's internal state, so this must never be N calls.
        val nodes = listOf(mockLeafNode(), mockLeafNode())
        val root = mockRoot(nodes)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(1)
        assertThat(fakeRasterizer.lastRegions).hasSize(2)
        assertThat(result!!.wireframes).hasSize(2)
    }

    @Test
    fun `M emit a placeholder without registering a capture W decompose() { privacy denies }`(
        @StringForgery fakeLabel: String
    ) {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )
        val registered = mutableListOf<PendingPixelCapture>()

        // When
        val result = testedDecomposer.decompose(
            mockView,
            realRequest(
                pixelCapturePlaceholderLabelFor = { fakeLabel },
                pendingPixelCaptureSink = PendingPixelCaptureSink { registered += it }
            )
        )

        // Then
        assertThat(result).isNotNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(0)
        assertThat(registered).isEmpty()
        val placeholder = result!!.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo(fakeLabel)
    }

    @Test
    fun `M emit a placeholder W decompose() { rasterizer returns null for the region }`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer(bitmapsToReturn = { regions -> regions.map { null } })
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        assertThat(result).isNotNull()
        assertThat(result!!.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
    }

    @Test
    fun `M abort before the batched draw W decompose() { shouldContinue denies right before capture }`() {
        // Given
        val node = mockLeafNode()
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        val fakeRasterizer = FakeComposeHostCaptureRasterizer()
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            hostRasterizer = fakeRasterizer,
            nodesPerCheckpoint = 1000 // never trips mid-walk; only the pre-capture check should fire
        )

        // When - denies every call, so the walk's own single node still passes (checkpoint never
        // reached with only 1 node) but the dedicated pre-capture re-check catches it.
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { false }))

        // Then
        assertThat(result).isNull()
        assertThat(fakeRasterizer.callCount).isEqualTo(0)
    }

    // endregion

    // region clip modifier

    /**
     * A `graphicsLayer` modifier exposing [properties] through [InspectableValue.inspectableElements] -
     * the same public mechanism the real `GraphicsLayerElement` uses (see
     * `GranularComposeDecomposer.resolveGraphicsLayerModifiers`'s doc), not reflection.
     */
    private fun mockGraphicsLayerModifier(properties: Map<String, Any?>): Modifier {
        val modifier = mock<Modifier>(extraInterfaces = arrayOf(InspectableValue::class))
        val inspectable = modifier as InspectableValue
        whenever(inspectable.nameFallback).thenReturn("graphicsLayer")
        whenever(inspectable.inspectableElements).thenReturn(
            properties.map { (name, value) -> ValueElement(name, value) }.asSequence()
        )
        return modifier
    }

    /** A node with one child (so it isn't treated as a pixel-capture candidate) and a `graphicsLayer` modifier exposing [clipEnabled]/[shape]. */
    private fun mockClippableNode(clipEnabled: Boolean, shape: Shape?): SemanticsNode {
        val node = mockLeafNode()
        val childNode = mockLeafNode()
        val graphicsLayerModifier = mockGraphicsLayerModifier(mapOf("clip" to clipEnabled, "shape" to shape))
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(graphicsLayerModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(node.children).thenReturn(listOf(childNode))
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 50))
        return node
    }

    @Test
    fun `M emit a Clip modifier W decompose() { graphicsLayer clip enabled with a rounded shape }`() {
        // Given
        val fakeShape: Shape = mock()
        val node = mockClippableNode(clipEnabled = true, shape = fakeShape)
        whenever(mockSemanticsUtils.resolveCornerRadius(eq(fakeShape), any(), any())).thenReturn(10f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val clippedLayer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val clip = clippedLayer.modifiers.filterIsInstance<CapturedModifier.Clip>().single()
        assertThat(clip.path).isEqualTo(
            "M 10.0,0 L 90.0,0 A 10.0,10.0 0 0 1 100.0,10.0 L 100.0,40.0 A 10.0,10.0 0 0 1 90.0,50.0 " +
                "L 10.0,50.0 A 10.0,10.0 0 0 1 0,40.0 L 0,10.0 A 10.0,10.0 0 0 1 10.0,0 Z"
        )
    }

    @Test
    fun `M not emit a Clip modifier W decompose() { graphicsLayer shape set but clip disabled }`() {
        // Given: a shape without clip=true is only used for the shadow outline, not to actually
        // clip content - emitting a Clip modifier here would be wrong, not just unnecessary.
        val fakeShape: Shape = mock()
        val node = mockClippableNode(clipEnabled = false, shape = fakeShape)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val clippedLayer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(clippedLayer.modifiers.filterIsInstance<CapturedModifier.Clip>()).isEmpty()
    }

    @Test
    fun `M not emit a Clip modifier W decompose() { clip enabled but shape resolves to no rounding }`() {
        // Given: a plain rectangular clip has no visual effect the existing per-wireframe
        // ancestor-bounds crop doesn't already provide - not worth a layer-level modifier for it.
        val fakeShape: Shape = mock()
        val node = mockClippableNode(clipEnabled = true, shape = fakeShape)
        whenever(mockSemanticsUtils.resolveCornerRadius(eq(fakeShape), any(), any())).thenReturn(0f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val clippedLayer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(clippedLayer.modifiers.filterIsInstance<CapturedModifier.Clip>()).isEmpty()
    }

    // endregion

    // region shadow modifier

    /** A node with one child (so it isn't treated as a pixel-capture candidate) and a `graphicsLayer` modifier exposing [elevationPx]/[spotColor]. */
    private fun mockShadowNode(elevationPx: Float?, spotColor: Color?): SemanticsNode {
        val node = mockLeafNode()
        val childNode = mockLeafNode()
        val graphicsLayerModifier = mockGraphicsLayerModifier(
            mapOf("shadowElevation" to elevationPx, "spotShadowColor" to spotColor)
        )
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(graphicsLayerModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(node.children).thenReturn(listOf(childNode))
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 50))
        return node
    }

    @Test
    fun `M emit a Shadow modifier W decompose() { graphicsLayer has positive elevation }`() {
        // Given: elevation 4px at density 1 rounds to the Material elevation table's 4dp row -
        // (offsetY=2, blur=4) for the key/umbra layer.
        val node = mockShadowNode(elevationPx = 4f, spotColor = Color.Black)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val shadow = layer.modifiers.filterIsInstance<CapturedModifier.Shadow>().single()
        assertThat(shadow.offsetX).isEqualTo(0.0)
        assertThat(shadow.offsetY).isEqualTo(2.0)
        assertThat(shadow.radius).isEqualTo(4.0)
        val expectedAlpha = (Color.Black.alpha * 0.2f * 255).roundToInt()
        assertThat(shadow.color).isEqualTo(
            DefaultColorStringFormatter.formatColorAndAlphaAsHexString(Color.Black.toArgb(), expectedAlpha)
        )
    }

    @Test
    fun `M default the shadow color to black W decompose() { spotShadowColor not resolvable }`() {
        // Given
        val node = mockShadowNode(elevationPx = 4f, spotColor = null)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val shadow = layer.modifiers.filterIsInstance<CapturedModifier.Shadow>().single()
        val expectedAlpha = (Color.Black.alpha * 0.2f * 255).roundToInt()
        assertThat(shadow.color).isEqualTo(
            DefaultColorStringFormatter.formatColorAndAlphaAsHexString(Color.Black.toArgb(), expectedAlpha)
        )
    }

    @Test
    fun `M not emit a Shadow modifier W decompose() { elevation is zero }`() {
        // Given: a flat node (no Z) casts no shadow.
        val node = mockShadowNode(elevationPx = 0f, spotColor = Color.Black)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.Shadow>()).isEmpty()
    }

    @Test
    fun `M not emit a Shadow modifier W decompose() { elevation not resolvable }`() {
        // Given
        val node = mockShadowNode(elevationPx = null, spotColor = Color.Black)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.Shadow>()).isEmpty()
    }

    // endregion

    // region blur modifier

    /** A node with one child (so it isn't treated as a pixel-capture candidate) and a `graphicsLayer` modifier exposing [renderEffect]. */
    private fun mockBlurNode(renderEffect: RenderEffect?): SemanticsNode {
        val node = mockLeafNode()
        val childNode = mockLeafNode()
        val graphicsLayerModifier = mockGraphicsLayerModifier(mapOf("renderEffect" to renderEffect))
        val modifierInfo: ModifierInfo = mock()
        whenever(modifierInfo.modifier).thenReturn(graphicsLayerModifier)
        whenever(node.layoutInfo.getModifierInfo()).thenReturn(listOf(modifierInfo))
        whenever(node.layoutInfo.density).thenReturn(Density(1f))
        whenever(node.children).thenReturn(listOf(childNode))
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 100, 50))
        return node
    }

    @Test
    fun `M emit a GaussianBlur modifier W decompose() { graphicsLayer has a uniform BlurEffect }`() {
        // Given
        val fakeBlurEffect: BlurEffect = mock()
        val node = mockBlurNode(fakeBlurEffect)
        whenever(mockReflectionUtils.getBlurRadii(fakeBlurEffect)).thenReturn(8f to 8f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        val blur = layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>().single()
        assertThat(blur.radius).isEqualTo(8.0)
    }

    @Test
    fun `M not emit a GaussianBlur modifier W decompose() { radiusX and radiusY differ }`() {
        // Given: CapturedModifier.GaussianBlur has a single radius - an elliptical blur has no
        // faithful representation, so it's left unrecognized rather than guessed at.
        val fakeBlurEffect: BlurEffect = mock()
        val node = mockBlurNode(fakeBlurEffect)
        whenever(mockReflectionUtils.getBlurRadii(fakeBlurEffect)).thenReturn(8f to 4f)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>()).isEmpty()
    }

    @Test
    fun `M not emit a GaussianBlur modifier W decompose() { renderEffect is not a BlurEffect }`() {
        // Given: e.g. an OffsetEffect - no CapturedModifier equivalent.
        val fakeRenderEffect: RenderEffect = mock()
        val node = mockBlurNode(fakeRenderEffect)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>()).isEmpty()
    }

    @Test
    fun `M not emit a GaussianBlur modifier W decompose() { reflection fails to resolve radii }`() {
        // Given
        val fakeBlurEffect: BlurEffect = mock()
        val node = mockBlurNode(fakeBlurEffect)
        whenever(mockReflectionUtils.getBlurRadii(fakeBlurEffect)).thenReturn(null)
        val root = mockRoot(listOf(node))
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)

        // When
        val result = testedDecomposer.decompose(mockView, realRequest())

        // Then
        checkNotNull(result)
        val layer = result.nodes.single { it.bounds.width == 100L && it.bounds.height == 50L }
        assertThat(layer.modifiers.filterIsInstance<CapturedModifier.GaussianBlur>()).isEmpty()
    }

    // endregion

    // region color matrix modifier
    //
    // resolveColorMatrixModifier/resolveSaturateValue/resolveBrightnessValue are plain top-level
    // functions taking a raw FloatArray - no mocking, no fakes, no decompose()-level integration
    // needed. The only part of this feature that isn't unit-tested is platformColorMatrixValues' few
    // lines of real Compose→Android ColorFilter interop, which unitTests.isReturnDefaultValues=true
    // makes impossible to observe meaningfully in this environment regardless of how it's structured
    // - see that function's own doc.

    @Test
    fun `M return null W resolveColorMatrixModifier { values is null }`() {
        assertThat(resolveColorMatrixModifier(null)).isNull()
    }

    @Test
    fun `M return a ColorMatrix modifier W resolveColorMatrixModifier { values is not a saturation matrix }`() {
        // Given: an arbitrary, deterministic 20-value matrix that doesn't match the saturation shape.
        val fakeValues = FloatArray(20) { it.toFloat() }

        // When
        val modifier = resolveColorMatrixModifier(fakeValues)

        // Then
        assertThat(modifier).isEqualTo(CapturedModifier.ColorMatrix(fakeValues.map { it.toDouble() }))
    }

    @Test
    fun `M return a Saturate modifier W resolveColorMatrixModifier { values is a saturation matrix }`() {
        // Given: ColorMatrix.setToSaturation is real, non-Android-stubbed Compose Kotlin code, so
        // it behaves correctly (and testably) even under this module's unit test setup.
        val fakeSaturation = 0.4f
        val fakeValues = ColorMatrix().apply { setToSaturation(fakeSaturation) }.values

        // When
        val modifier = resolveColorMatrixModifier(fakeValues)

        // Then
        val saturate = modifier as CapturedModifier.Saturate
        assertThat(saturate.value).isCloseTo(fakeSaturation.toDouble(), within(0.001))
    }

    @Test
    fun `M return null W resolveSaturateValue { values has the wrong size }`() {
        assertThat(resolveSaturateValue(FloatArray(4))).isNull()
    }

    @Test
    fun `M return null W resolveSaturateValue { values does not match the saturation shape }`() {
        assertThat(resolveSaturateValue(FloatArray(20) { it.toFloat() })).isNull()
    }

    @Test
    fun `M return a BrightnessBias modifier W resolveColorMatrixModifier { values is a brightness matrix }`(
        @FloatForgery(min = -1f, max = 1f) fakeBrightness: Float
    ) {
        // Given
        val fakeValues = brightnessMatrix(fakeBrightness, alphaOffset = 0f)

        // When
        val modifier = resolveColorMatrixModifier(fakeValues)

        // Then
        val brightnessBias = modifier as CapturedModifier.BrightnessBias
        assertThat(brightnessBias.value).isCloseTo(fakeBrightness.toDouble(), within(0.001))
    }

    @Test
    fun `M return null W resolveBrightnessValue { values has the wrong size }`() {
        assertThat(resolveBrightnessValue(FloatArray(4))).isNull()
    }

    @Test
    fun `M return null W resolveBrightnessValue { values does not match the brightness shape }`() {
        assertThat(resolveBrightnessValue(FloatArray(20) { it.toFloat() })).isNull()
    }

    @Test
    fun `M return null W resolveBrightnessValue { alpha translation is non-zero }`(
        @FloatForgery(min = -1f, max = 1f) fakeBrightness: Float,
        @FloatForgery(min = 1f, max = 10f) fakeAlphaOffset: Float
    ) {
        // Given
        val fakeValues = brightnessMatrix(fakeBrightness, alphaOffset = fakeAlphaOffset)

        // When + Then
        assertThat(resolveBrightnessValue(fakeValues)).isNull()
    }

    /** Matches the production identity-plus-translation brightness matrix shape. */
    private fun brightnessMatrix(brightness: Float, alphaOffset: Float): FloatArray {
        val offset = brightness * 255f
        return floatArrayOf(
            1f, 0f, 0f, 0f, offset,
            0f, 1f, 0f, 0f, offset,
            0f, 0f, 1f, 0f, offset,
            0f, 0f, 0f, 1f, alphaOffset
        )
    }

    // endregion

    private class FakeCompositionIdentityFactory : CompositionIdentityFactory {
        private var nextWireId = 0L
        private val scope = RumViewIdentityScope("fake-scope")

        private fun next(kind: CapturedIdentityKind, wireframeKind: CapturedWireframeKind? = null): CapturedIdentity =
            CapturedIdentity(
                scope = scope,
                kind = kind,
                wireframeKind = wireframeKind,
                namespace = emptyList(),
                localId = nextWireId.toString(),
                wireId = nextWireId++
            )

        fun anyIdentity(): CapturedIdentity = next(CapturedIdentityKind.VIEW)

        override fun composeHost(window: CapturedIdentity, hostId: String) = next(CapturedIdentityKind.COMPOSE_HOST)
        override fun composeNode(host: CapturedIdentity, nodeId: String) = next(CapturedIdentityKind.COMPOSE_NODE)
        override fun shapeWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.SHAPE)
        override fun textWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.TEXT)
        override fun placeholderWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.PLACEHOLDER)
        override fun imageWireframe(owner: CapturedIdentity) =
            next(CapturedIdentityKind.WIREFRAME, CapturedWireframeKind.IMAGE)
    }
}
