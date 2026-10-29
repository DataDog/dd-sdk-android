/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.Rect
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.compose.ui.platform.ComposeView
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
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.Executor

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidWindowTraversalCompositionTest : AndroidWindowTraversalTestFixture() {

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M hand off typed Compose host without visiting native children W traverseWindow`(
        fakeReturnsWireframes: Boolean,
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val mockRoot = mockViewGroup(fakeRootBounds)
        val mockComposeHost: ComposeView = mockViewWithDefaults(fakeRootBounds)
        val mockComposeChild = mockView(fakeRootBounds)
        mockRoot.withChildren(mockComposeHost)
        whenever(mockComposeHost.childCount).thenReturn(1)
        whenever(mockComposeHost.getChildAt(0)).thenReturn(mockComposeChild)
        val mockDecomposer: CompositionHostDecomposer = mock()
        whenever(mockDecomposer.canDecompose(mockComposeHost)).thenReturn(true)
        whenever(mockDecomposer.decompose(eq(mockComposeHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val fakeWireframeIdentity = request.identityFactory.shapeWireframe(request.hostIdentity)
            CompositionHostDecomposeResult(
                if (fakeReturnsWireframes) listOf(CapturedChild.Wireframe(fakeWireframeIdentity)) else emptyList(),
                emptyList(),
                if (fakeReturnsWireframes) {
                    listOf(CapturedWireframe.Shape(fakeWireframeIdentity, fakeRootBounds.toCapturedBounds()))
                } else {
                    emptyList()
                }
            )
        }

        // When
        val result = traversal(compositionHostDecomposer = mockDecomposer).traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        )

        // Then
        verify(mockDecomposer).decompose(eq(mockComposeHost), isA())
        verify(mockComposeHost, never()).getChildAt(any())
        assertThat((result as WindowCopyResult.Present).window.views).hasSize(2)
        val fakePresent = result.doneValue() as WindowWalkResult.Present
        assertThat(fakePresent.wireframes).hasSize(if (fakeReturnsWireframes) 1 else 0)
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
        var fakeBeforeExpiry: Boolean? = null
        var fakeAfterExpiry: Boolean? = null
        val expiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = 100L,
            timeProvider = CaptureTimeProvider { now }
        )
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(composeHost)).thenReturn(true)
        whenever(decomposer.decompose(eq(composeHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            fakeBeforeExpiry = request.shouldContinue()
            now = 100L
            fakeAfterExpiry = request.shouldContinue()
            CompositionHostDecomposeResult(emptyList(), emptyList(), emptyList())
        }

        // When
        val result = traversal(
            compositionHostDecomposer = decomposer
        ).traverseWindow(root, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result).isEqualTo(WindowCopyResult.Aborted)
        assertThat(fakeBeforeExpiry).isTrue()
        assertThat(fakeAfterExpiry).isFalse()
        verify(decomposer).decompose(eq(composeHost), any())
    }

    @Test
    fun `M invoke the decomposer and splice its result W traverseWindow() { compose host }`(
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
            override fun readTouchOverrideAreas(view: View, shouldContinue: () -> Boolean): Map<Rect, TouchPrivacy> =
                emptyMap()

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
            compositionHostDecomposer = decomposer
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val hostLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_HOST }
        val nodeLayer = present.layers.first { it.kind == CapturedLayerKind.COMPOSE_NODE }
        assertThat(capturedHostIdentity).isEqualTo(hostLayer.identity)
        assertThat(capturedHostBounds).isEqualTo(hostLayer.bounds)
        val contentLayer = present.layers.single { it.kind == CapturedLayerKind.COMPOSITION_LAYER }
        assertThat(hostLayer.children).containsExactly(CapturedChild.Layer(contentLayer.identity))
        assertThat(contentLayer.children).containsExactly(CapturedChild.Layer(nodeLayer.identity))
        assertThat(present.wireframes).hasSize(1)
        assertThat(present.layers).hasSize(4)
    }

    @Test
    fun `M fall back to the mapper W traverseWindow() { compose host, decomposer cannot decompose }`(
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
            compositionHostDecomposer = decomposer
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes).hasSize(2)
        assertThat(present.wireframes.map { it.bounds.x }).contains(fakeHostBounds.x)
        val hostLayer = present.layers.first { it.kind == CapturedLayerKind.NATIVE_VIEW }
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
            override fun readTouchOverrideAreas(view: View, shouldContinue: () -> Boolean): Map<Rect, TouchPrivacy> =
                emptyMap()

            override fun canDecompose(view: View) = view === composeHost
            override fun decompose(
                view: View,
                request: CompositionHostDecomposeRequest
            ): CompositionHostDecomposeResult? {
                val childIdentity = request.identityFactory.composeNode(request.hostIdentity, "interop")
                if (!request.nativeViewHandoff(interopView, childIdentity)) return null
                return CompositionHostDecomposeResult(
                    rootChildren = listOf(CapturedChild.Layer(childIdentity)),
                    nodes = emptyList(),
                    wireframes = emptyList()
                )
            }
        }

        // When
        val result = traversal(
            fallback = markerMapper,
            compositionHostDecomposer = decomposer
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        val interopLayer = present.layers.first { it.kind == CapturedLayerKind.NATIVE_VIEW }
        assertThat(interopLayer.bounds.x).isEqualTo(fakeInteropBounds.x)
        val interopWireframe = present.wireframes.single { it.identity == interopLayer.children.single().identity }
        assertThat(interopWireframe.bounds.x).isEqualTo(fakeInteropBounds.x)
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
            compositionHostDecomposer = decomposer
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes.single().clip).isEqualTo(CapturedClip(40, 35, 30, 35))
        val hostLayer = present.layers.single { it.kind == CapturedLayerKind.COMPOSE_HOST }
        assertThat(hostLayer.modifiers).containsExactly(CapturedModifier.Opacity(0.5))
        assertThat(present.layers.single { it.kind == CapturedLayerKind.COMPOSITION_LAYER }.modifiers)
            .containsExactly(CapturedModifier.Clip("M10 10 H65 V65 H10 Z"))
        assertThat(present.layers).hasSize(3)
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
            compositionHostDecomposer = stubDecomposer
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
            assertThat(request.nativeViewHandoff(interopView, identity)).isTrue()
            CompositionHostDecomposeResult(listOf(CapturedChild.Layer(identity)), emptyList(), emptyList())
        }

        // When
        val result = traversal(
            fallback = markerMapper,
            compositionHostDecomposer = decomposer
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
            assertThat(request.nativeViewHandoff(nativeChild, identity)).isTrue()
            null
        }

        // When
        val result = traversal(
            fallback = markerMapper,
            compositionHostDecomposer = decomposer
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
            compositionHostDecomposer = mockDecomposer
        ).traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(present.layers).hasSize(2)
        verify(mockDecomposer, never()).canDecompose(any())
        verify(mockDecomposer, never()).decompose(any(), any())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain native content and log W traverseWindow() { decomposer throws a linkage error }`(
        fakeRecognitionFailure: Boolean,
        @StringForgery fakeErrorMessage: String
    ) {
        // Given
        val mockRoot = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val mockHost = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val mockChild = mockView(GlobalBounds(0, 0, 20, 20))
        mockRoot.withChildren(mockHost)
        mockHost.withChildren(mockChild)
        val mockDecomposer: CompositionHostDecomposer = mock()
        val fakeError = NoSuchMethodError(fakeErrorMessage)
        whenever(mockDecomposer.canDecompose(mockHost)).thenReturn(true)
        if (fakeRecognitionFailure) {
            whenever(mockDecomposer.canDecompose(mockHost)).thenThrow(fakeError)
        } else {
            whenever(mockDecomposer.decompose(eq(mockHost), any())).thenThrow(fakeError)
        }
        val mockLogger: InternalLogger = mock()
        val testedTraversal = traversal(
            fallback = markerMapper,
            compositionHostDecomposer = mockDecomposer,
            internalLogger = mockLogger
        )

        // When
        val result = assertDoesNotThrow<WindowCopyResult> {
            testedTraversal.traverseWindow(mockRoot, identityFactory.window("window"), identityFactory, fakeContext)
        }

        // Then
        val fakeWindow = result.doneValue() as WindowWalkResult.Present
        assertThat(fakeWindow.layers).hasSize(3)
        assertThat(fakeWindow.wireframes).hasSize(3)
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            "Composition host decomposition failed; falling back to native capture",
            throwable = fakeError,
            onlyOnce = true
        )
    }

    @Test
    fun `M defer native handoff mapping W traverseWindow() { worker has not run }`() {
        // Given
        val mockRoot = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val mockHost = mockView(GlobalBounds(0, 0, 100, 100))
        val mockInterop = mockView(GlobalBounds(0, 0, 20, 20))
        mockRoot.withChildren(mockHost)
        val mockCopiedData: CapturedViewData = mock()
        whenever(mockCopiedData.map(any())).thenAnswer { invocation ->
            val mappingContext = invocation.getArgument<CapturedMappingContext>(0)
            CapturedViewMapperResult.Wireframes(
                CapturedWireframe.Shape(
                    mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                    CapturedBounds(0, 0, 20, 20)
                )
            )
        }
        val mockMapper: CapturedViewMapper<View> = mock()
        whenever(mockMapper.capture(any(), any())).thenReturn(CapturedViewData.Mapped(CapturedViewMapperResult.None))
        whenever(mockMapper.capture(eq(mockInterop), any())).thenReturn(mockCopiedData)
        whenever(
            mockMapper.capture(eq(mockRoot), any())
        ).thenReturn(CapturedViewData.Mapped(CapturedViewMapperResult.None))
        val mockDecomposer: CompositionHostDecomposer = mock()
        whenever(mockDecomposer.canDecompose(mockHost)).thenReturn(true)
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer { invocation ->
            val request = invocation.getArgument<CompositionHostDecomposeRequest>(1)
            val identity = request.identityFactory.composeNode(request.hostIdentity, "interop")
            assertThat(request.nativeViewHandoff(mockInterop, identity)).isTrue()
            CompositionHostDecomposeResult(listOf(CapturedChild.Layer(identity)), emptyList(), emptyList())
        }
        val testedTraversal = traversal(fallback = mockMapper, compositionHostDecomposer = mockDecomposer)
        val fakeQueue = mutableListOf<Runnable>()
        val fakeResults = mutableListOf<SnapshotProcessingResult>()
        val testedProcessor = ExecutorCapturedSnapshotProcessor(Executor(fakeQueue::add), mock())

        // When
        val copy = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ) as WindowCopyResult.Present
        testedProcessor.process(
            SnapshotProcessingRequest(
                fakeContext,
                CapturedNativeSnapshot(1L, identityFactory.scope, listOf(copy.window))
            ),
            fakeResults::add
        )

        // Then
        verify(mockCopiedData, never()).map(any())
        assertThat(fakeResults).isEmpty()

        // When
        fakeQueue.single().run()

        // Then
        val snapshot = (fakeResults.single() as SnapshotProcessingResult.Completed).snapshot
        assertThat(snapshot.wireframes).hasSize(1)
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
        verify(mockCopiedData).map(isA())
    }

    @ParameterizedTest
    @ValueSource(strings = ["view", "surface", "texture"])
    fun `M preserve uncertain native handoffs W traverseWindow() + build() { covered composition host }`(
        fakeInteropType: String
    ) {
        // Given
        fakeDensity = 1f
        val fakeBounds = GlobalBounds(0, 0, 100, 100)
        val mockRoot = mockViewGroup(GlobalBounds(0, 0, 200, 200))
        val mockBack = mockViewGroup(fakeBounds)
        val mockHost = mockView(fakeBounds)
        val mockFront = mockViewGroup(fakeBounds)
        val mockCover = mockView(fakeBounds)
        val mockInterop = when (fakeInteropType) {
            "surface" -> mockViewWithDefaults<SurfaceView>(fakeBounds)
            "texture" -> mockViewWithDefaults<TextureView>(fakeBounds)
            else -> mockView(fakeBounds)
        }
        mockRoot.withChildren(mockBack, mockFront)
        mockBack.withChildren(mockHost)
        mockFront.withChildren(mockCover)
        listOf(mockRoot, mockBack, mockHost, mockFront, mockCover, mockInterop).forEach {
            whenever(it.alpha).thenReturn(1f)
        }
        val mockCoverData: CapturedViewData = mock()
        whenever(mockCoverData.hasOpaqueBackground).thenReturn(true)
        whenever(mockCoverData.isOcclusionBounded).thenReturn(true)
        whenever(mockCoverData.map(any())).thenAnswer {
            val context = it.getArgument<CapturedMappingContext>(0)
            CapturedViewMapperResult.Wireframes(
                CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(context.ownerIdentity),
                    fakeBounds.toCapturedBounds()
                )
            )
        }
        val mockMapper: CapturedViewMapper<View> = mock()
        whenever(mockMapper.capture(any(), any())).thenAnswer {
            val view = it.getArgument<View>(0)
            val context = it.getArgument<CapturedMappingContext>(1)
            when (view) {
                mockCover -> mockCoverData
                mockInterop -> CapturedViewData.Mapped(
                    CapturedViewMapperResult.Wireframes(
                        CapturedWireframe.Shape(
                            context.identityFactory.shapeWireframe(context.ownerIdentity),
                            fakeBounds.toCapturedBounds()
                        )
                    )
                )
                else -> CapturedViewData.Mapped(CapturedViewMapperResult.None)
            }
        }
        val mockDecomposer: CompositionHostDecomposer = mock()
        whenever(mockDecomposer.canDecompose(mockHost)).thenReturn(true)
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val identity = request.identityFactory.composeNode(request.hostIdentity, "interop")
            assertThat(request.nativeViewHandoff(mockInterop, identity)).isTrue()
            CompositionHostDecomposeResult(listOf(CapturedChild.Layer(identity)), emptyList(), emptyList())
        }
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), mockMapper, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            drawableToColorMapper = mockDrawableToColorMapper,
            compositionHostDecomposer = mockDecomposer
        )
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val copy = testedTraversal.traverseWindow(
            mockRoot,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ) as WindowCopyResult.Present
        val result = testedBuilder.build(
            CapturedNativeSnapshot(1L, identityFactory.scope, listOf(copy.window)),
            fakeContext
        )

        // Then
        val snapshot = checkNotNull(result)
        val host = copy.window.views.single { it.kind == CapturedLayerKind.COMPOSE_HOST }
        val native = checkNotNull(host.composition).nativeSubtrees.single()
        val fakeReliable = fakeInteropType == "view"
        assertThat(native.isDrawingOrderReliable).isEqualTo(fakeReliable)
        assertThat(copy.window.isDrawingOrderReliable).isEqualTo(fakeReliable)
        val fakeInteropWireframe = identityFactory.shapeWireframe(native.views.first().identity)
        if (fakeReliable) {
            assertThat(snapshot.wireframes.map { it.identity }).doesNotContain(fakeInteropWireframe)
        } else {
            assertThat(snapshot.wireframes.map { it.identity }).contains(fakeInteropWireframe)
        }
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    private fun GlobalBounds.toCapturedBounds() = CapturedBounds(x, y, width, height)

    @ParameterizedTest
    @CsvSource("HIDE,SHOW,HIDE", "SHOW,HIDE,HIDE", "SHOW,SHOW,SHOW")
    fun `M merge Compose and native touch overrides W traverseWindow()`(
        fakeNativePrivacy: TouchPrivacy,
        fakeComposePrivacy: TouchPrivacy,
        fakeExpected: TouchPrivacy
    ) {
        // Given
        val root = mockViewGroup(GlobalBounds(0, 0, 100, 100))
        val host = mockView(GlobalBounds(0, 0, 100, 100))
        root.withChildren(host)
        whenever(host.getTag(R.id.datadog_touch_privacy)).thenReturn(fakeNativePrivacy.name)
        val decomposer: CompositionHostDecomposer = mock()
        whenever(decomposer.canDecompose(host)).thenReturn(true)
        whenever(decomposer.decompose(eq(host), any())).thenReturn(
            CompositionHostDecomposeResult(
                emptyList(),
                emptyList(),
                emptyList(),
                mapOf(Rect(0, 0, 100, 100) to fakeComposePrivacy)
            )
        )

        // When
        val result = traversal(compositionHostDecomposer = decomposer)
            .traverseWindow(root, identityFactory.window("window"), identityFactory, fakeContext)

        // Then
        val captured = result as WindowCopyResult.Present
        assertThat(captured.touchOverrideAreas).containsEntry(Rect(0, 0, 100, 100), fakeExpected)
    }
}
