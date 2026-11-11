/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMappingContext
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.isA
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedNativeSnapshotBuilderTest {
    @Mock lateinit var mockNativeData: CapturedViewData

    private lateinit var fakeFactory: DefaultCapturedIdentityFactory
    private lateinit var fakeWindowIdentity: CapturedIdentity
    private lateinit var fakeHostIdentity: CapturedIdentity
    private lateinit var fakeNodeIdentity: CapturedIdentity
    private lateinit var fakeContentIdentity: CapturedIdentity
    private lateinit var fakeInteropIdentity: CapturedIdentity
    private lateinit var fakeGeneration: CaptureGenerationContext
    private var fakeDensity: Float = 1f
    private val fakeContentBounds = CapturedBounds(40, 60, 120, 20)
    private val testedBuilder = CapturedNativeSnapshotBuilder()

    @BeforeEach
    fun setUp(
        @StringForgery fakeScope: String,
        @StringForgery fakeWindowId: String,
        @StringForgery fakeHostId: String,
        @StringForgery(regex = "node-[a-z]{8}") fakeNodeId: String,
        @StringForgery(regex = "interop-[a-z]{8}") fakeInteropId: String,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensityValue: Float,
        @LongForgery(min = 1L) fakeGenerationId: Long
    ) {
        fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        fakeWindowIdentity = fakeFactory.window(fakeWindowId)
        fakeHostIdentity = fakeFactory.composeHost(fakeWindowIdentity, fakeHostId)
        fakeContentIdentity = fakeFactory.layer(fakeHostIdentity, "composition-content")
        fakeNodeIdentity = fakeFactory.composeNode(fakeHostIdentity, fakeNodeId)
        fakeInteropIdentity = fakeFactory.composeNode(fakeHostIdentity, fakeInteropId)
        fakeDensity = fakeDensityValue
        fakeGeneration = CaptureGenerationContext(fakeGenerationId, 0L, 100L, CaptureTimeProvider { 0L })
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M assemble clipped composition and native content W build() { copied inputs }`(
        fakeRestrictiveHostClip: Boolean
    ) {
        // Given
        val fakeNativeWireframe = CapturedWireframe.Shape(
            fakeFactory.shapeWireframe(fakeInteropIdentity),
            fakeContentBounds,
            clip = CapturedClip(bottom = 3)
        )
        whenever(mockNativeData.map(any()))
            .thenReturn(CapturedViewMapperResult.Wireframes(fakeNativeWireframe))
        val fakeInput = snapshotInput(fakeRestrictiveHostClip, hostAlpha = 0.5)

        // When
        val result = testedBuilder.build(fakeInput, fakeGeneration)

        // Then
        val snapshot = checkNotNull(result)
        val fakeComposeWireframeIdentity = fakeFactory.shapeWireframe(fakeNodeIdentity)
        assertThat(snapshot.wireframes.single { it.identity == fakeComposeWireframeIdentity }.clip).isEqualTo(
            if (fakeRestrictiveHostClip) {
                CapturedClip(top = 2, left = 30, right = 30)
            } else {
                CapturedClip(top = 2, left = 15, right = 10)
            }
        )
        assertThat(snapshot.wireframes.single { it.identity == fakeNativeWireframe.identity }.clip).isEqualTo(
            if (fakeRestrictiveHostClip) {
                CapturedClip(bottom = 3, left = 30, right = 30)
            } else {
                CapturedClip(bottom = 3, left = 10, right = 10)
            }
        )
        assertThat(snapshot.layers.single { it.identity == fakeHostIdentity }.modifiers)
            .containsExactly(CapturedModifier.Opacity(0.5))
        assertThat(snapshot.layers.single { it.identity == fakeContentIdentity }.modifiers).containsExactly(
            CapturedModifier.Clip(
                if (fakeRestrictiveHostClip) "M20 0 H80 V100 H20 Z" else "M0 0 H100 V100 H0 Z"
            )
        )
        assertThat(snapshot.layers.single { it.identity == fakeWindowIdentity }.modifiers).isEmpty()
        assertThat(snapshot.layers.single { it.identity == fakeInteropIdentity }.modifiers).isEmpty()
        assertThat(snapshot.layers.single { it.identity == fakeHostIdentity }.children)
            .containsExactly(CapturedChild.Layer(fakeContentIdentity))
        assertThat(snapshot.layers.single { it.identity == fakeContentIdentity }.children).containsExactly(
            CapturedChild.Layer(fakeNodeIdentity),
            CapturedChild.Layer(fakeInteropIdentity)
        )
        assertThat(snapshot.layers.single { it.identity == fakeInteropIdentity }.kind)
            .isEqualTo(CapturedLayerKind.NATIVE_VIEW)
        val mappingContextCaptor = argumentCaptor<CapturedMappingContext>()
        verify(mockNativeData).map(mappingContextCaptor.capture())
        assertThat(mappingContextCaptor.firstValue.ownerIdentity).isEqualTo(fakeInteropIdentity)
        assertThat(mappingContextCaptor.firstValue.screenDensity).isEqualTo(fakeDensity)
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    @ParameterizedTest
    @CsvSource(
        "200, 60, 10, 10, M150 10 H150 V20 H150 Z",
        "60, 200, 10, 10, M10 150 H20 V150 H10 Z",
        "60, 60, 0, 10, M10 10 H10 V20 H10 Z",
        "60, 60, 10, 0, M10 10 H20 V10 H10 Z"
    )
    fun `M preserve empty composition clips W build() { host intersection has no area }`(
        fakeClipX: Long,
        fakeClipY: Long,
        fakeClipWidth: Long,
        fakeClipHeight: Long,
        fakeExpectedPath: String
    ) {
        // Given
        whenever(mockNativeData.map(any())).thenReturn(CapturedViewMapperResult.None)
        val fakeInput = snapshotInput(
            explicitHostClip = CapturedBounds(fakeClipX, fakeClipY, fakeClipWidth, fakeClipHeight)
        )

        // When
        val result = testedBuilder.build(fakeInput, fakeGeneration)

        // Then
        val snapshot = checkNotNull(result)
        assertThat(snapshot.layers.single { it.identity == fakeContentIdentity }.modifiers)
            .containsExactly(CapturedModifier.Clip(fakeExpectedPath))
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M discard the snapshot W build() { cancellation before or during native construction }`(
        fakeCancelledBeforeBuild: Boolean
    ) {
        // Given
        whenever(mockNativeData.map(any())).thenAnswer {
            fakeGeneration.expire()
            CapturedViewMapperResult.None
        }
        val fakeInput = snapshotInput()
        if (fakeCancelledBeforeBuild) fakeGeneration.expire()

        // When
        val result = testedBuilder.build(fakeInput, fakeGeneration)

        // Then
        assertThat(result).isNull()
        if (fakeCancelledBeforeBuild) {
            verifyNoInteractions(mockNativeData)
        } else {
            verify(mockNativeData).map(isA())
        }
    }

    @ParameterizedTest
    @CsvSource("0, true", "3, true", "64, true", "65, false", "10000, false")
    fun `M bound native handoff nesting W build() { deeply alternating composition and native content }`(
        fakeDepth: Int,
        fakeAccepted: Boolean
    ) {
        // Given
        whenever(mockNativeData.map(any())).thenReturn(CapturedViewMapperResult.None)
        val fakeInput = nestedSnapshotInput(fakeDepth)

        // When
        val result = assertDoesNotThrow<CapturedFullSnapshot?> { testedBuilder.build(fakeInput, fakeGeneration) }

        // Then
        if (fakeAccepted) {
            val snapshot = checkNotNull(result)
            assertThat(snapshot.layers).hasSize(3 * fakeDepth + 1)
            assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
            verify(mockNativeData).map(isA())
        } else {
            assertThat(result).isNull()
            verifyNoInteractions(mockNativeData)
        }
    }

    private fun nestedSnapshotInput(depth: Int): CapturedNativeSnapshot {
        var nested: CapturedNativeWindow? = null
        for (level in depth downTo 0) {
            val rootIdentity = if (level == 0) {
                fakeWindowIdentity
            } else {
                fakeFactory.composeNode(fakeFactory.composeHost(fakeWindowIdentity, "host-${level - 1}"), "interop")
            }
            val root = CapturedNativeView(
                identity = rootIdentity,
                parentIndex = -1,
                bounds = fakeContentBounds,
                alpha = 1.0,
                viewData = if (nested ==
                    null
                ) {
                    mockNativeData
                } else {
                    CapturedViewData.Mapped(CapturedViewMapperResult.None)
                },
                clipToBounds = false,
                explicitClip = null,
                childPaddingClip = null,
                kind = if (level == 0) CapturedLayerKind.WINDOW_ROOT else CapturedLayerKind.NATIVE_VIEW
            )
            val views = if (nested == null) {
                listOf(root)
            } else {
                val host = root.copy(
                    identity = fakeFactory.composeHost(fakeWindowIdentity, "host-$level"),
                    parentIndex = 0,
                    kind = CapturedLayerKind.COMPOSE_HOST,
                    composition = CapturedComposition(
                        CompositionHostDecomposeResult(
                            listOf(CapturedChild.Layer(nested.views.first().identity)),
                            emptyList(),
                            emptyList(),
                            emptyMap()
                        ),
                        listOf(nested),
                        fakeFactory.layer(
                            fakeFactory.composeHost(fakeWindowIdentity, "host-$level"),
                            "composition-content"
                        )
                    )
                )
                listOf(root, host)
            }
            nested = CapturedNativeWindow(fakeDensity, views)
        }
        return CapturedNativeSnapshot(fakeGeneration.id, fakeFactory.scope, listOf(checkNotNull(nested)))
    }

    private fun snapshotInput(
        restrictiveHostClip: Boolean = false,
        hostAlpha: Double = 1.0,
        explicitHostClip: CapturedBounds? = null
    ): CapturedNativeSnapshot {
        val root = CapturedNativeView(
            identity = fakeWindowIdentity,
            parentIndex = -1,
            bounds = CapturedBounds(0, 0, 300, 300),
            alpha = 1.0,
            viewData = CapturedViewData.Mapped(CapturedViewMapperResult.None),
            clipToBounds = true,
            explicitClip = null,
            childPaddingClip = null
        )
        val host = CapturedNativeView(
            identity = fakeHostIdentity,
            parentIndex = 0,
            bounds = CapturedBounds(50, 50, 100, 100),
            alpha = hostAlpha,
            viewData = CapturedViewData.Mapped(CapturedViewMapperResult.None),
            clipToBounds = false,
            explicitClip = explicitHostClip ?: if (restrictiveHostClip) CapturedBounds(70, 50, 60, 100) else null,
            childPaddingClip = if (restrictiveHostClip) CapturedBounds(60, 50, 80, 100) else null,
            kind = CapturedLayerKind.COMPOSE_HOST,
            composition = compositionInput()
        )
        return CapturedNativeSnapshot(
            fakeGeneration.id,
            fakeFactory.scope,
            listOf(CapturedNativeWindow(fakeDensity, listOf(root, host)))
        )
    }

    private fun compositionInput(): CapturedComposition {
        val shape = CapturedWireframe.Shape(
            fakeFactory.shapeWireframe(fakeNodeIdentity),
            fakeContentBounds,
            clip = CapturedClip(top = 2, left = 15)
        )
        val node = CapturedLayer(
            fakeNodeIdentity,
            CapturedLayerKind.COMPOSE_NODE,
            fakeContentBounds,
            listOf(CapturedChild.Wireframe(shape.identity))
        )
        val native = CapturedNativeView(
            identity = fakeInteropIdentity,
            parentIndex = -1,
            bounds = fakeContentBounds,
            alpha = 1.0,
            viewData = mockNativeData,
            clipToBounds = false,
            explicitClip = null,
            childPaddingClip = null,
            kind = CapturedLayerKind.NATIVE_VIEW
        )
        return CapturedComposition(
            CompositionHostDecomposeResult(
                listOf(CapturedChild.Layer(fakeNodeIdentity), CapturedChild.Layer(fakeInteropIdentity)),
                listOf(node),
                listOf(shape),
                emptyMap()
            ),
            listOf(CapturedNativeWindow(fakeDensity, listOf(native))),
            fakeContentIdentity
        )
    }
}
