/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedBackgroundShapeStyleResolver
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.IntForgery
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidWindowTraversalHostBackgroundTest : AndroidWindowTraversalTestFixture() {

    @ParameterizedTest
    @CsvSource("true, false", "true, true", "false, false", "false, true")
    fun `M retain the native background beneath composition W traverseWindow() + build()`(
        fakeOpaque: Boolean,
        fakePadding: Boolean,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeRgb: Int,
        @StringForgery(regex = "#[0-9A-F]{6}") fakeRgbHex: String,
        @StringForgery fakeWindowName: String,
        @StringForgery fakeNodeName: String
    ) {
        // Given
        fakeDensity = 1f
        val fakeBounds = GlobalBounds(0, 0, 100, 100)
        val fakeColor = ((if (fakeOpaque) 0xFF else 0x80) shl 24) or fakeRgb
        val fakeHex = fakeRgbHex + if (fakeOpaque) "FF" else "80"
        val mockRoot = mockViewGroup(fakeBounds)
        val mockBack = mockView(fakeBounds)
        val mockHost = mockViewGroup(fakeBounds)
        mockRoot.withChildren(mockBack, mockHost)
        listOf(mockRoot, mockBack, mockHost).forEach { whenever(it.alpha).thenReturn(1f) }
        val mockBackground: ColorDrawable = mock()
        whenever(mockBackground.opacity).thenReturn(if (fakeOpaque) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT)
        whenever(mockBack.background).thenReturn(mockBackground)
        whenever(mockHost.background).thenReturn(mockBackground)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(mockBackground), any())).thenReturn(fakeColor)
        val mockFormatter: ColorStringFormatter = mock()
        whenever(mockFormatter.formatColorAsHexString(fakeColor)).thenReturn(fakeHex)
        whenever(mockHost.clipToPadding).thenReturn(fakePadding)
        whenever(mockHost.paddingLeft).thenReturn(if (fakePadding) 10 else 0)
        whenever(mockViewBoundsResolver.resolveViewPaddedBounds(mockHost, 1f))
            .thenReturn(GlobalBounds(10, 0, 90, 100))
        val mockLogger: InternalLogger = mock()
        val mockDecomposer: CompositionHostDecomposer = mock()
        whenever(mockDecomposer.canDecompose(mockHost)).thenReturn(true)
        whenever(mockDecomposer.decompose(eq(mockHost), any())).thenAnswer {
            val request = it.getArgument<CompositionHostDecomposeRequest>(1)
            val node = request.identityFactory.composeNode(request.hostIdentity, fakeNodeName)
            val shape = request.identityFactory.shapeWireframe(node)
            CompositionHostDecomposeResult(
                listOf(CapturedChild.Layer(node)),
                listOf(
                    CapturedLayer(
                        node,
                        CapturedLayerKind.COMPOSE_NODE,
                        CapturedBounds(0, 0, 30, 30),
                        listOf(CapturedChild.Wireframe(shape)),
                        listOf(CapturedModifier.GaussianBlur(4.0))
                    )
                ),
                listOf(CapturedWireframe.Shape(shape, CapturedBounds(0, 0, 30, 30)))
            )
        }
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(
                emptyList(),
                CapturedViewGroupFallbackMapper(
                    mockViewBoundsResolver,
                    CapturedBackgroundShapeStyleResolver(mockLogger, mockFormatter, mockDrawableToColorMapper)
                ),
                mockLogger
            ),
            internalLogger = mockLogger,
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            drawableToColorMapper = mockDrawableToColorMapper,
            compositionHostDecomposer = mockDecomposer
        )
        val fakeWindow = identityFactory.window(fakeWindowName)
        val fakeHost = identityFactory.composeHost(
            fakeWindow,
            mockViewIdentifierResolver.resolveViewId(mockHost).toString()
        )
        val fakeBackground = identityFactory.shapeWireframe(fakeHost)
        val fakeBack = identityFactory.view(fakeWindow, mockViewIdentifierResolver.resolveViewId(mockBack).toString())
        val fakeBackWireframe = identityFactory.shapeWireframe(fakeBack)
        val testedBuilder = CapturedNativeSnapshotBuilder()

        // When
        val copy = testedTraversal.traverseWindow(mockRoot, fakeWindow, identityFactory, fakeContext)
            as WindowCopyResult.Present
        val result = testedBuilder.build(
            CapturedNativeSnapshot(1L, identityFactory.scope, listOf(copy.window)),
            fakeContext
        )
        val nextCopy = testedTraversal.traverseWindow(mockRoot, fakeWindow, identityFactory, fakeContext)
            as WindowCopyResult.Present
        val nextResult = testedBuilder.build(
            CapturedNativeSnapshot(2L, identityFactory.scope, listOf(nextCopy.window)),
            fakeContext
        )

        // Then
        val snapshot = checkNotNull(result)
        val nextSnapshot = checkNotNull(nextResult)
        val identities = snapshot.wireframes.map { it.identity }
        assertThat(identities).contains(fakeBackground)
        val background = snapshot.wireframes.single { it.identity == fakeBackground } as CapturedWireframe.Shape
        assertThat(background.style?.backgroundColor).isEqualTo(fakeHex)
        assertThat(background.clip).isNull()
        val host = snapshot.layers.single { it.identity == fakeHost }
        val content = snapshot.layers.single { it.kind == CapturedLayerKind.COMPOSITION_LAYER }
        assertThat(
            host.children
        ).containsExactly(CapturedChild.Wireframe(fakeBackground), CapturedChild.Layer(content.identity))
        assertThat(host.modifiers).isEmpty()
        assertThat(content.modifiers).containsExactly(
            CapturedModifier.Clip(if (fakePadding) "M10 0 H100 V100 H10 Z" else "M0 0 H100 V100 H0 Z")
        )
        assertThat(nextSnapshot.layers.single { it.kind == CapturedLayerKind.COMPOSITION_LAYER }.identity)
            .isEqualTo(content.identity)
        if (fakeOpaque) {
            assertThat(identities).doesNotContain(fakeBackWireframe)
        } else {
            assertThat(identities).contains(fakeBackWireframe)
        }
        assertThat(DefaultCapturedTreeValidator().validate(snapshot)).isEqualTo(CaptureValidationResult.Valid)
    }
}
