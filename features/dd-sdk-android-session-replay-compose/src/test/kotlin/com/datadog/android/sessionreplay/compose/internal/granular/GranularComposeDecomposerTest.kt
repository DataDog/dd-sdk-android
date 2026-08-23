/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import android.view.View
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentityKind
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframeKind
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.compose.internal.utils.ReflectionUtils
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposeRequest
import com.datadog.android.sessionreplay.recorder.composition.CompositionNativeSubtree
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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

    @Test
    fun `M capture a leaf entering the viewport W decompose() { clipped leaf is skipped initially }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        val fakeRequest = requestWithLargeGroup(stubLeaf)

        // When
        val outsideResult = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(outsideResult).isNotNull()
        assertThat(outsideResult?.nodes).isEmpty()
        assertThat(outsideResult?.wireframes).isEmpty()
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(stubLeaf), any())

        // When
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(stubLeaf), any()))
            .thenReturn(GlobalBounds(10, 90, 20, 20))
        val enteringResult = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(enteringResult?.nodes).hasSize(1)
        assertThat(enteringResult?.nodes?.single()?.bounds?.y).isEqualTo(90L)
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubLeaf), any())
    }

    @Test
    fun `M retain overflowing children W decompose() { parent bounds are outside viewport }`() {
        // Given
        val stubChild = mockNode(GlobalBounds(10, 10, 20, 20))
        val stubParent = mockNode(GlobalBounds(-50, 10, 20, 20), listOf(stubChild))
        val fakeRequest = requestWithLargeGroup(stubParent)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(2)
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubChild), any())
    }

    @Test
    fun `M retain nodes W decompose() { copied host clip is unknown }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        val fakeRequest = requestWithLargeGroup(stubLeaf, hostBounds = null)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(32)
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubLeaf), any())
    }

    @Test
    fun `M return an empty composition W decompose() { all leaves are clipped }`() {
        // Given
        val stubLeaves = List(32) { mockNode(GlobalBounds(10, 110, 20, 20)) }
        val fakeRequest = requestWithRoots(stubLeaves)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result).isNotNull()
        assertThat(result?.rootChildren).isEmpty()
        assertThat(result?.nodes).isEmpty()
        assertThat(result?.wireframes).isEmpty()
        assertThat(testedGate.isAvailable()).isTrue()
    }

    @Test
    fun `M retain small groups W decompose() { selective culling below threshold }`() {
        // Given
        val stubLeaves = List(31) { mockNode(GlobalBounds(10, 110, 20, 20)) }
        val stubParent = mockNode(GlobalBounds(0, 0, 100, 100), stubLeaves)
        val fakeRequest = requestWithRoot(stubParent)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(32)
        stubLeaves.forEach { verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(it), any()) }
    }

    @Test
    fun `M cull large groups W decompose() { selective culling reaches threshold }`() {
        // Given
        val stubOffscreenLeaves = List(31) { mockNode(GlobalBounds(10, 110, 20, 20)) }
        val stubVisibleLeaf = mockNode(GlobalBounds(10, 10, 20, 20))
        val stubParent = mockNode(GlobalBounds(0, 0, 100, 100), stubOffscreenLeaves + stubVisibleLeaf)
        val fakeRequest = requestWithRoot(stubParent)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(2)
        stubOffscreenLeaves.forEach { verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(it), any()) }
        verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(stubVisibleLeaf), any())
    }

    @Test
    fun `M retain intersecting leaves W decompose() { partial visibility and zero extent }`() {
        // Given
        val fakeBounds = listOf(
            GlobalBounds(-19, 10, 20, 20),
            GlobalBounds(99, 10, 20, 20),
            GlobalBounds(10, -19, 20, 20),
            GlobalBounds(10, 99, 20, 20),
            GlobalBounds(10, 110, 0, 20)
        )
        val stubLeaves = fakeBounds.map { mockNode(it) }
        val fakeRequest = requestWithRoots(stubLeaves + List(27) { mockNode(GlobalBounds(10, 110, 20, 20)) })

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(5)
        stubLeaves.forEach { verify(mockSemanticsUtils).resolveTextLayoutInfo(eq(it), any()) }
    }

    @Test
    fun `M retain hidden placeholders W decompose() { leaf is outside host clip }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        whenever(mockSemanticsUtils.isNodeHidden(stubLeaf)).thenReturn(true)
        val fakeRequest = requestWithLargeGroup(stubLeaf)

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).hasSize(1)
        assertThat(result?.wireframes).hasSize(1)
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(stubLeaf), any())
        verify(mockReflectionUtils, never()).getInteropView(stubLeaf)
    }

    @Test
    fun `M retain native handoff W decompose() { interop leaf is outside host clip }`() {
        // Given
        val stubLeaf = mockNode(GlobalBounds(10, 110, 20, 20))
        val stubInteropView: View = mock()
        whenever(mockReflectionUtils.getInteropView(stubLeaf)).thenReturn(stubInteropView)
        val fakeRoot = CapturedLayer(
            mock(),
            CapturedLayerKind.NATIVE_VIEW,
            CapturedBounds(10, 110, 20, 20),
            emptyList()
        )
        val fakeSubtree = CompositionNativeSubtree(fakeRoot, listOf(fakeRoot), emptyList())
        val fakeRequest = requestWithLargeGroup(stubLeaf, nativeHandoff = { view, _ ->
            assertThat(view).isSameAs(stubInteropView)
            fakeSubtree
        })

        // When
        val result = testedDecomposer.decompose(mockView, fakeRequest)

        // Then
        assertThat(result?.nodes).containsExactly(fakeRoot)
        verify(mockSemanticsUtils, never()).resolveTextLayoutInfo(eq(stubLeaf), any())
    }

    private fun mockNode(bounds: GlobalBounds, children: List<SemanticsNode> = emptyList()): SemanticsNode {
        val stubNode: SemanticsNode = mock()
        val stubLayout: LayoutInfo = mock()
        whenever(stubNode.layoutInfo).thenReturn(stubLayout)
        whenever(stubLayout.getModifierInfo()).thenReturn(emptyList())
        whenever(stubNode.children).thenReturn(children)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(stubNode), any())).thenReturn(bounds)
        whenever(mockSemanticsUtils.resolveBackgroundColor(stubNode)).thenReturn(null)
        return stubNode
    }

    private fun requestWithRoot(
        node: SemanticsNode,
        hostBounds: CapturedBounds? = CapturedBounds(0, 0, 100, 100),
        nativeHandoff: (View, CapturedIdentity) -> CompositionNativeSubtree? = { _, _ -> null }
    ): CompositionHostDecomposeRequest = requestWithRoots(listOf(node), hostBounds, nativeHandoff)

    private fun requestWithLargeGroup(
        node: SemanticsNode,
        hostBounds: CapturedBounds? = CapturedBounds(0, 0, 100, 100),
        nativeHandoff: (View, CapturedIdentity) -> CompositionNativeSubtree? = { _, _ -> null }
    ): CompositionHostDecomposeRequest = requestWithRoots(
        listOf(node) + List(31) { mockNode(GlobalBounds(10, 110, 20, 20)) },
        hostBounds,
        nativeHandoff
    )

    private fun requestWithRoots(
        nodes: List<SemanticsNode>,
        hostBounds: CapturedBounds? = CapturedBounds(0, 0, 100, 100),
        nativeHandoff: (View, CapturedIdentity) -> CompositionNativeSubtree? = { _, _ -> null }
    ): CompositionHostDecomposeRequest {
        val stubRoot: SemanticsNode = mock()
        whenever(stubRoot.children).thenReturn(nodes)
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(stubRoot)
        val stubIdentityFactory: CompositionIdentityFactory = mock()
        whenever(stubIdentityFactory.composeNode(any(), any())).thenAnswer { mock<CapturedIdentity>() }
        whenever(stubIdentityFactory.placeholderWireframe(any())).thenAnswer { mock<CapturedIdentity>() }
        return CompositionHostDecomposeRequest(stubIdentityFactory, mock(), 1f, hostBounds, nativeHandoff)
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
        whenever(layoutInfo.getModifierInfo()).thenReturn(emptyList())
        whenever(node.id).thenReturn(nextNodeId++)
        whenever(node.children).thenReturn(emptyList())
        whenever(mockSemanticsUtils.isNodeHidden(node)).thenReturn(false)
        whenever(mockReflectionUtils.getInteropView(node)).thenReturn(null)
        whenever(mockSemanticsUtils.resolveTextLayoutInfo(eq(node), any())).thenReturn(null)
        whenever(mockSemanticsUtils.resolveBackgroundColor(node)).thenReturn(null)
        whenever(mockSemanticsUtils.resolveInnerBounds(eq(node), any())).thenReturn(GlobalBounds(0, 0, 10, 10))
        return node
    }

    private fun mockRoot(children: List<SemanticsNode>): SemanticsNode {
        val root: SemanticsNode = mock()
        whenever(root.children).thenReturn(children)
        return root
    }

    private fun realRequest(shouldContinue: () -> Boolean): CompositionHostDecomposeRequest {
        val identityFactory = FakeCompositionIdentityFactory()
        return CompositionHostDecomposeRequest(
            identityFactory = identityFactory,
            hostIdentity = identityFactory.anyIdentity(),
            screenDensity = 1f,
            nativeViewHandoff = { _, _ -> null },
            shouldContinue = shouldContinue
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

        // Then
        assertThat(checkpointCalls).isEqualTo(3) // 6 nodes / checkpoint-every-2
    }

    // endregion

    @Test
    fun `M discard partial tree W decompose() { checkpoint denies after a completed node }`() {
        // Given
        val root = mockRoot(List(3) { mockLeafNode() })
        whenever(mockSemanticsUtils.findRootSemanticsNode(mockView)).thenReturn(root)
        testedDecomposer = GranularComposeDecomposer(
            semanticsUtils = mockSemanticsUtils,
            reflectionUtils = mockReflectionUtils,
            compatibilityGate = testedGate,
            nodesPerCheckpoint = 2
        )

        // When
        val result = testedDecomposer.decompose(mockView, realRequest(shouldContinue = { false }))

        // Then
        assertThat(result).isNull()
        assertThat(testedGate.isAvailable()).isTrue()
        verify(mockSemanticsUtils, never()).resolveInnerBounds(eq(root.children[2]), any())
    }

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
    }
}
