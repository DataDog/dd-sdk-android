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
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentityKind
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
import com.datadog.android.sessionreplay.utils.GlobalBounds
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

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
