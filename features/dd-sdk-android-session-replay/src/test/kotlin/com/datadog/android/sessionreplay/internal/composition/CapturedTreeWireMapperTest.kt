/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedCompositeOperation
import com.datadog.android.internal.sessionreplay.composition.CapturedFillRule
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedTreeWireMapperTest {

    @Forgery
    lateinit var fakeTree: CompositionTestTree

    @StringForgery(regex = "[0-9a-f]{32}")
    lateinit var fakeResourceId: String

    private val testedMapper = DefaultCapturedTreeWireMapper()

    @Test
    fun `M preserve wireframes and tree W mapFullSnapshot`() {
        // When
        val result = testedMapper.mapFullSnapshot(fakeTree.snapshot)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        assertThat(record.timestamp).isEqualTo(fakeTree.snapshot.timestamp)
        val fakeBounds = (fakeTree.wireframe as CapturedWireframe.Shape).bounds
        assertThat(record.data.wireframes.single()).isEqualTo(
            MobileSegment.Wireframe.ShapeWireframe(
                id = fakeTree.wireframeIdentity.wireId,
                x = fakeBounds.x,
                y = fakeBounds.y,
                width = fakeBounds.width,
                height = fakeBounds.height
            )
        )
        val tree = checkNotNull(record.data.compositionTree) { "Expected a mapped composition tree" }
        val root = checkNotNull(tree.root) { "Expected the mapped tree root" }
        val layers = checkNotNull(tree.layers) { "Expected mapped tree layers" }
        assertThat(root.id).isEqualTo(fakeTree.root.identity.wireId)
        assertThat(layers.map { it.id })
            .containsExactly(fakeTree.layer.identity.wireId)
        assertThat(record.toJson().asJsonObject.getAsJsonObject("data").has("compositionTree")).isTrue
    }

    @Test
    fun `M preserve modifier ordering W mapFullSnapshot`() {
        // Given
        val fakeModifiers = listOf(
            CapturedModifier.Clip("M0 0", CapturedFillRule.EVEN_ODD),
            CapturedModifier.Opacity(0.5),
            CapturedModifier.ColorMatrix(List(20) { it.toDouble() }),
            CapturedModifier.GaussianBlur(2.0),
            CapturedModifier.Shadow("#AABBCC", 1.0, 2.0, 3.0),
            CapturedModifier.BrightnessBias(0.25),
            CapturedModifier.Saturate(1.5),
            CapturedModifier.MaskImage(fakeResourceId)
        )
        val fakeLayer = fakeTree.layer.copy(
            modifiers = fakeModifiers,
            compositeOperation = CapturedCompositeOperation.DESTINATION_IN
        )

        // When
        val result = testedMapper.mapFullSnapshot(fakeTree.snapshot.copy(layers = listOf(fakeLayer)))

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val wireLayer = checkNotNull(record.data.compositionTree?.layers?.single()) {
            "Expected one mapped composition layer"
        }
        assertThat(wireLayer.modifiers).containsExactly(
            MobileSegment.CompositionLayerModifier.CompositionLayerClipModifier(
                path = "M0 0",
                fillRule = MobileSegment.FillRule.EVENODD
            ),
            MobileSegment.CompositionLayerModifier.CompositionLayerOpacityModifier(0.5),
            MobileSegment.CompositionLayerModifier.CompositionLayerColorMatrixModifier(
                List(20) { it.toDouble() }
            ),
            MobileSegment.CompositionLayerModifier.CompositionLayerGaussianBlurModifier(2.0),
            MobileSegment.CompositionLayerModifier.CompositionLayerShadowModifier(
                color = "#AABBCC",
                offsetX = 1.0,
                offsetY = 2.0,
                radius = 3.0
            ),
            MobileSegment.CompositionLayerModifier.CompositionLayerBrightnessBiasModifier(0.25),
            MobileSegment.CompositionLayerModifier.CompositionLayerSaturateModifier(1.5),
            MobileSegment.CompositionLayerModifier.CompositionLayerMaskImageModifier(fakeResourceId)
        )
        assertThat(wireLayer.compositeOperation).isEqualTo(MobileSegment.CompositeOperation.DESTINATIONIN)
    }

    @Test
    fun `M preserve ordered children W mapMutation`() {
        // Given
        val fakeSecondIdentity = fakeTree.factory.placeholderWireframe(fakeTree.layer.identity)
        val fakeSecondWireframe = CapturedWireframe.PrivacyPlaceholder(
            identity = fakeSecondIdentity,
            bounds = CapturedBounds(5, 6, 7, 8)
        )
        val fakeBase = fakeTree.snapshotWithWireframes(fakeTree.wireframe, fakeSecondWireframe)
        val fakeUpdate = CapturedLayerUpdate(
            identity = fakeTree.layer.identity,
            children = CapturedChange.Set(
                listOf(CapturedChild.Wireframe(fakeSecondIdentity), CapturedChild.Wireframe(fakeTree.wireframeIdentity))
            ),
            modifiers = CapturedChange.Set(emptyList())
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(listOf(fakeUpdate))
        )

        // When
        val result = testedMapper.mapMutation(fakeMutation, fakeBase)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        val mappedUpdate = checkNotNull(data.updates) { "Expected mapped layer updates" }.single()
        val children = checkNotNull(mappedUpdate.children) { "Expected an explicit children update" }
        assertThat(children.map { it.id })
            .containsExactly(fakeSecondIdentity.wireId, fakeTree.wireframeIdentity.wireId)
        assertThat(mappedUpdate.modifiers).isEmpty()
        assertThat(mappedUpdate.x).isNull()
        assertThat(data.adds).isNull()
        assertThat(data.removes).isNull()
    }

    @Test
    fun `M preserve absent and explicit empty fields W mapMutation`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(emptyList()),
            removes = CapturedChange.Set(emptyList())
        )

        // When
        val result = testedMapper.mapMutation(fakeMutation, fakeTree.snapshot)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        assertThat(data.adds).isEmpty()
        assertThat(data.removes).isEmpty()
        assertThat(data.updates).isNull()
        assertThat(data.root).isNull()
    }

    @Test
    fun `M map resolved pixels and privacy placeholders W mapFullSnapshot`() {
        // Given
        val fakePixelIdentity = fakeTree.factory.imageWireframe(fakeTree.layer.identity)
        val fakePlaceholderIdentity = fakeTree.factory.placeholderWireframe(fakeTree.layer.identity)
        val fakePixelBounds = CapturedBounds(1, 2, 3, 4)
        val fakePlaceholderBounds = CapturedBounds(5, 6, 7, 8)
        val fakePixel = CapturedWireframe.Pixel(
            identity = fakePixelIdentity,
            bounds = fakePixelBounds,
            resource = PixelResource.Resolved(fakeResourceId, "image/webp")
        )
        val fakePlaceholder = CapturedWireframe.PrivacyPlaceholder(
            identity = fakePlaceholderIdentity,
            bounds = fakePlaceholderBounds,
            label = "Image"
        )
        val fakeSnapshot = fakeTree.snapshotWithWireframes(fakePixel, fakePlaceholder)

        // When
        val result = testedMapper.mapFullSnapshot(fakeSnapshot)

        // Then
        val wireframes = (result as CaptureWireMappingResult.Success).value.data.wireframes
        assertThat(wireframes).containsExactly(
            MobileSegment.Wireframe.ImageWireframe(
                id = fakePixelIdentity.wireId,
                x = fakePixelBounds.x,
                y = fakePixelBounds.y,
                width = fakePixelBounds.width,
                height = fakePixelBounds.height,
                resourceId = fakeResourceId,
                mimeType = "image/webp"
            ),
            MobileSegment.Wireframe.PlaceholderWireframe(
                id = fakePlaceholderIdentity.wireId,
                x = fakePlaceholderBounds.x,
                y = fakePlaceholderBounds.y,
                width = fakePlaceholderBounds.width,
                height = fakePlaceholderBounds.height,
                label = "Image"
            )
        )
    }

    @Test
    fun `M map cleared composite operation to source over W mapMutation`() {
        // Given
        val fakeBase = fakeTree.snapshot.copy(
            layers = listOf(
                fakeTree.layer.copy(compositeOperation = CapturedCompositeOperation.DESTINATION_IN)
            )
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = fakeTree.layer.identity,
                        compositeOperation = CapturedChange.Set(null)
                    )
                )
            )
        )

        // When
        val result = testedMapper.mapMutation(fakeMutation, fakeBase)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        val update = checkNotNull(data.updates) { "Expected a mapped layer update" }.single()
        assertThat(update.compositeOperation)
            .isEqualTo(MobileSegment.CompositeOperation.SOURCEOVER)
    }

    @Test
    fun `M map complete added layers W mapMutation`() {
        // Given
        val fakeAddedIdentity = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-added")
        val fakeAddedLayer = fakeTree.layer.copy(identity = fakeAddedIdentity)
        val fakeReplacementRoot = fakeTree.root.copy(
            children = listOf(
                CapturedChild.Layer(fakeTree.layer.identity),
                CapturedChild.Layer(fakeAddedIdentity)
            )
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            root = CapturedChange.Set(fakeReplacementRoot),
            adds = CapturedChange.Set(listOf(fakeAddedLayer)),
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = fakeTree.layer.identity,
                        children = CapturedChange.Set(emptyList())
                    )
                )
            )
        )

        // When
        val result = testedMapper.mapMutation(fakeMutation, fakeTree.snapshot)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        val addedLayer = checkNotNull(data.adds) { "Expected a mapped added layer" }.single()
        val children = checkNotNull(addedLayer.children) { "Expected the added layer's children" }
        assertThat(addedLayer.id).isEqualTo(fakeAddedIdentity.wireId)
        assertThat(children.single().id)
            .isEqualTo(fakeTree.wireframeIdentity.wireId)
    }

    @Test
    fun `M map removed layer identities W mapMutation`() {
        // Given
        val fakeRemovedIdentity = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-removed")
        val fakeRemovedLayer = fakeTree.layer.copy(identity = fakeRemovedIdentity, children = emptyList())
        val fakeBase = fakeTree.snapshot.copy(
            root = fakeTree.root.copy(
                children = listOf(
                    CapturedChild.Layer(fakeTree.layer.identity),
                    CapturedChild.Layer(fakeRemovedIdentity)
                )
            ),
            layers = listOf(fakeTree.layer, fakeRemovedLayer)
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            root = CapturedChange.Set(fakeTree.root),
            removes = CapturedChange.Set(listOf(fakeRemovedIdentity))
        )

        // When
        val result = testedMapper.mapMutation(fakeMutation, fakeBase)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        assertThat(data.removes).containsExactly(fakeRemovedIdentity.wireId)
    }

    @Test
    fun `M map webview wireframe with slotId equal to wire id W mapFullSnapshot`() {
        // Given
        val fakeWebViewIdentity = fakeTree.factory.webViewWireframe(fakeTree.layer.identity, slotId = 42L)
        val fakeLayer = fakeTree.layer.copy(children = listOf(CapturedChild.Wireframe(fakeWebViewIdentity)))
        val fakeBounds = CapturedBounds(1, 2, 3, 4)
        val fakeWebViewWireframe = CapturedWireframe.WebView(
            identity = fakeWebViewIdentity,
            bounds = fakeBounds,
            isVisible = true
        )
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer), wireframes = listOf(fakeWebViewWireframe))

        // When
        val result = testedMapper.mapFullSnapshot(fakeSnapshot)

        // Then
        val wireframe = (result as CaptureWireMappingResult.Success).value.data.wireframes.single()
            as MobileSegment.Wireframe.WebviewWireframe
        assertThat(wireframe.id).isEqualTo(fakeWebViewIdentity.wireId)
        assertThat(wireframe.slotId).isEqualTo(fakeWebViewIdentity.wireId.toString())
        assertThat(wireframe.isVisible).isTrue()
        assertThat(wireframe.width).isEqualTo(fakeBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeBounds.height)
    }

    @Test
    fun `M return structured failures W mapMutation { invalid mutation }`() {
        // Given
        val fakeUnknownIdentity = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-unknown")
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            removes = CapturedChange.Set(listOf(fakeUnknownIdentity))
        )

        // When
        val result = testedMapper.mapMutation(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result).isInstanceOf(CaptureWireMappingResult.Invalid::class.java)
        assertThat((result as CaptureWireMappingResult.Invalid).failures.map { it.code })
            .containsExactly(CaptureValidationErrorCode.UNKNOWN_MUTATION_TARGET)
    }

    @Test
    fun `M return structured failures W mapFullSnapshot { invalid snapshot }`() {
        // Given
        val fakeSnapshot = fakeTree.snapshot.copy(root = null)

        // When
        val result = testedMapper.mapFullSnapshot(fakeSnapshot)

        // Then
        assertThat(result).isInstanceOf(CaptureWireMappingResult.Invalid::class.java)
        assertThat((result as CaptureWireMappingResult.Invalid).failures.map { it.code })
            .containsExactly(CaptureValidationErrorCode.MISSING_ROOT)
    }

    @Test
    fun `M return structured failure W mapFullSnapshot { validator accepts unresolved pixel }`() {
        // Given
        val fakeUnresolvedPixel = CapturedWireframe.Pixel(
            identity = fakeTree.wireframeIdentity,
            bounds = CapturedBounds(1, 2, 3, 4),
            resource = PixelResource.Unresolved
        )
        val testedMapper = DefaultCapturedTreeWireMapper(
            validator = object : CapturedTreeValidator {
                override fun validate(snapshot: CapturedFullSnapshot) = CaptureValidationResult.Valid

                override fun validate(
                    mutation: CapturedMutationSet,
                    base: CapturedFullSnapshot
                ) = CaptureValidationResult.Valid
            }
        )

        // When
        val result = testedMapper.mapFullSnapshot(fakeTree.snapshot.copy(wireframes = listOf(fakeUnresolvedPixel)))

        // Then
        assertThat(result).isEqualTo(
            CaptureWireMappingResult.Invalid(
                listOf(
                    CaptureValidationFailure(
                        CaptureValidationErrorCode.UNRESOLVED_PIXEL_RESOURCE,
                        fakeTree.wireframeIdentity
                    )
                )
            )
        )
    }
    private fun CompositionTestTree.snapshotWithWireframes(vararg children: CapturedWireframe): CapturedFullSnapshot =
        snapshot.copy(
            layers = listOf(layer.copy(children = children.map { CapturedChild.Wireframe(it.identity) })),
            wireframes = children.toList()
        )
}
