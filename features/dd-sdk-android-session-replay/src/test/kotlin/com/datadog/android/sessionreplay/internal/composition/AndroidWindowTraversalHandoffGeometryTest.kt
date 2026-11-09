/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidWindowTraversalHandoffGeometryTest : AndroidWindowTraversalTestFixture() {

    @ParameterizedTest
    @CsvSource(
        "STABLE, false, false",
        "STABLE, true, false",
        "STABLE, true, true",
        "SCALED_HOST, true, false",
        "SCALED_HOST, true, true",
        "SCALED_ANCESTOR, true, false",
        "SCALED_ANCESTOR, true, true",
        "ANIMATED_HOST, true, false",
        "ANIMATED_HOST, true, true",
        "ANIMATED_ANCESTOR, true, false",
        "ANIMATED_ANCESTOR, true, true"
    )
    fun `M cull only verified geometry W traverseWindow() + build() { native handoffs }`(
        fakeScenario: GeometryScenario,
        fakeHandoff: Boolean,
        fakeNestedHandoff: Boolean,
        @StringForgery fakeWindowId: String,
        @StringForgery fakeInteropId: String
    ) {
        // Given
        fakeDensity = 1f
        val fakeBounds = GlobalBounds(0, 0, 200, 200)
        val mockRoot = mockViewGroup(GlobalBounds(0, 0, 300, 300))
        val mockAncestor = mockViewGroup(fakeBounds)
        val mockHost = mockViewGroup(fakeBounds)
        val mockContentRoot = mockViewGroup(fakeBounds)
        val mockNestedHost = if (fakeNestedHandoff) mockViewGroup(fakeBounds) else null
        val mockInteropRoot = if (fakeNestedHandoff) mockViewGroup(fakeBounds) else mockContentRoot
        val mockBack = mockViewGroup(fakeBounds)
        val mockTarget = mockView(GlobalBounds(50, 0, 50, 100))
        val mockFront = mockViewGroup(fakeBounds)
        val mockCover = mockView(GlobalBounds(0, 0, 100, 100))
        mockRoot.withChildren(mockAncestor)
        mockAncestor.withChildren(mockHost)
        mockHost.withChildren(mockInteropRoot)
        if (mockNestedHost != null) {
            mockInteropRoot.withChildren(mockNestedHost)
            mockNestedHost.withChildren(mockContentRoot)
        }
        mockContentRoot.withChildren(mockBack, mockFront)
        mockBack.withChildren(mockTarget)
        mockFront.withChildren(mockCover)
        val mockViews = listOfNotNull(
            mockRoot, mockAncestor, mockHost, mockContentRoot, mockNestedHost,
            mockInteropRoot, mockBack, mockTarget, mockFront, mockCover
        ).distinct()
        mockViews.forEach { view ->
            whenever(view.alpha).thenReturn(1f)
            whenever(view.getLocationOnScreen(any())).thenAnswer {
                val location = it.getArgument<IntArray>(0)
                location[0] = if (view === mockTarget) 50 else 0
                location[1] = 0
                Unit
            }
        }
        applyGeometry(fakeScenario, mockHost, mockAncestor, mockTarget)
        val mockDecomposer: CompositionHostDecomposer = mock()
        whenever(mockDecomposer.canDecompose(any())).thenAnswer {
            val view = it.getArgument<View>(0)
            view === mockHost || view === mockNestedHost
        }
        whenever(mockDecomposer.decompose(any(), any())).thenAnswer {
            val view = it.getArgument<View>(0)
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val childIdentity = request.identityFactory.composeNode(request.hostIdentity, fakeInteropId)
            val child = if (view === mockHost) mockInteropRoot else mockContentRoot
            assertThat(request.nativeViewHandoff(child, childIdentity)).isTrue()
            CompositionHostDecomposeResult(listOf(CapturedChild.Layer(childIdentity)), emptyList(), emptyList())
        }
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), nativeMapper(mockCover), mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            drawableToColorMapper = mockDrawableToColorMapper,
            compositionHostDecomposer = mockDecomposer.takeIf { fakeHandoff }
        )
        val fakeWindowIdentity = identityFactory.window(fakeWindowId)
        val fakeTargetIdentity = identityFactory.view(
            fakeWindowIdentity,
            mockViewIdentifierResolver.resolveViewId(mockTarget).toString()
        )
        val fakeTargetWireframe = identityFactory.shapeWireframe(fakeTargetIdentity)
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val copy = testedTraversal.traverseWindow(
            mockRoot,
            fakeWindowIdentity,
            identityFactory,
            fakeContext
        ) as WindowCopyResult.Present
        val result = testedBuilder.build(
            CapturedNativeSnapshot(1L, identityFactory.scope, listOf(copy.window)),
            fakeContext
        )

        // Then
        val snapshot = checkNotNull(result)
        if (fakeHandoff) {
            assertThat(snapshot.wireframes.map { it.identity }).contains(fakeTargetWireframe)
            var window = copy.window
            repeat(if (fakeNestedHandoff) 2 else 1) {
                val host = window.views.single { view -> view.kind == CapturedLayerKind.COMPOSE_HOST }
                window = checkNotNull(host.composition).nativeSubtrees.single()
                assertThat(window.views).allSatisfy { view -> assertThat(view.isGeometryReliable).isFalse() }
            }
        } else {
            assertThat(snapshot.wireframes.map { it.identity }).doesNotContain(fakeTargetWireframe)
            assertThat(copy.window.views).allSatisfy { view -> assertThat(view.isGeometryReliable).isTrue() }
        }
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    private fun nativeMapper(cover: View): CapturedViewMapper<View> {
        val mockMapper: CapturedViewMapper<View> = mock()
        whenever(mockMapper.capture(any(), any())).thenAnswer {
            val view = it.getArgument<View>(0)
            val context = it.getArgument<CapturedMappingContext>(1)
            val mapping = CapturedViewMapperResult.Wireframes(
                CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(context.ownerIdentity),
                    checkNotNull(context.bounds)
                )
            )
            mock<CapturedViewData> {
                on { isOcclusionBounded } doReturn true
                on { hasOpaqueBackground } doReturn (view === cover)
                on { map(any()) } doReturn mapping
            }
        }
        return mockMapper
    }

    private fun applyGeometry(scenario: GeometryScenario, host: View, ancestor: View, target: View) {
        when (scenario) {
            GeometryScenario.STABLE -> Unit
            GeometryScenario.SCALED_HOST -> whenever(host.scaleX).thenReturn(0.5f)
            GeometryScenario.SCALED_ANCESTOR -> whenever(ancestor.scaleX).thenReturn(0.5f)
            GeometryScenario.ANIMATED_HOST -> whenever(host.animation).thenReturn(mock())
            GeometryScenario.ANIMATED_ANCESTOR -> whenever(ancestor.animation).thenReturn(mock())
        }
        if (scenario == GeometryScenario.SCALED_HOST || scenario == GeometryScenario.SCALED_ANCESTOR) {
            whenever(target.left).thenReturn(100)
        }
    }

    internal enum class GeometryScenario {
        STABLE,
        SCALED_HOST,
        SCALED_ANCESTOR,
        ANIMATED_HOST,
        ANIMATED_ANCESTOR
    }
}
