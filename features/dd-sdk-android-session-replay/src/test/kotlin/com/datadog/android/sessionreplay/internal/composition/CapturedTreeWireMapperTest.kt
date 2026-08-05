/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.model.MobileSegment
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CapturedTreeWireMapperTest {

    private val testedMapper = DefaultCapturedTreeWireMapper()

    @Test
    fun `M preserve wireframes and tree W mapFullSnapshot`() {
        // Given
        val tree = compositionTestTree()

        // When
        val result = testedMapper.mapFullSnapshot(tree.snapshot)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        assertThat(record.timestamp).isEqualTo(tree.snapshot.timestamp)
        assertThat(record.data.wireframes.single()).isEqualTo(
            MobileSegment.Wireframe.ShapeWireframe(
                id = tree.wireframeIdentity.wireId,
                x = 1,
                y = 2,
                width = 3,
                height = 4
            )
        )
        assertThat(record.data.compositionTree?.root?.id).isEqualTo(tree.root.identity.wireId)
        assertThat(record.data.compositionTree?.layers?.map { it.id })
            .containsExactly(tree.layer.identity.wireId)
        assertThat(record.toJson().asJsonObject.getAsJsonObject("data").has("compositionTree")).isTrue
    }

    @Test
    fun `M preserve modifier ordering W mapFullSnapshot`() {
        // Given
        val tree = compositionTestTree()
        val modifiers = listOf(
            CapturedModifier.Clip("M0 0", CapturedFillRule.EVEN_ODD),
            CapturedModifier.Opacity(0.5),
            CapturedModifier.ColorMatrix(List(20) { it.toDouble() }),
            CapturedModifier.GaussianBlur(2.0),
            CapturedModifier.Shadow("#AABBCC", 1.0, 2.0, 3.0),
            CapturedModifier.BrightnessBias(0.25),
            CapturedModifier.Saturate(1.5),
            CapturedModifier.MaskImage("resource")
        )
        val layer = tree.layer.copy(
            modifiers = modifiers,
            compositeOperation = CapturedCompositeOperation.DESTINATION_IN
        )

        // When
        val result = testedMapper.mapFullSnapshot(tree.snapshot.copy(layers = listOf(layer)))

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val wireLayer = record.data.compositionTree?.layers?.single()
        assertThat(wireLayer?.modifiers).containsExactly(
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
            MobileSegment.CompositionLayerModifier.CompositionLayerMaskImageModifier("resource")
        )
        assertThat(wireLayer?.compositeOperation).isEqualTo(MobileSegment.CompositeOperation.DESTINATIONIN)
    }

    @Test
    fun `M preserve ordered children W mapMutation`() {
        // Given
        val tree = compositionTestTree()
        val secondIdentity = tree.factory.placeholderWireframe(tree.layer.identity)
        val secondWireframe = CapturedWireframe.PrivacyPlaceholder(
            identity = secondIdentity,
            bounds = CapturedBounds(5, 6, 7, 8)
        )
        val baseLayer = tree.layer.copy(
            children = listOf(
                CapturedChild.Wireframe(tree.wireframeIdentity),
                CapturedChild.Wireframe(secondIdentity)
            )
        )
        val base = tree.snapshot.copy(
            layers = listOf(baseLayer),
            wireframes = listOf(tree.wireframe, secondWireframe)
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = tree.layer.identity,
                        children = CapturedChange.Set(
                            listOf(
                                CapturedChild.Wireframe(secondIdentity),
                                CapturedChild.Wireframe(tree.wireframeIdentity)
                            )
                        ),
                        modifiers = CapturedChange.Set(emptyList())
                    )
                )
            )
        )

        // When
        val result = testedMapper.mapMutation(mutation, base)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        val update = data.updates?.single()
        assertThat(update?.children?.map { it.id })
            .containsExactly(secondIdentity.wireId, tree.wireframeIdentity.wireId)
        assertThat(update?.modifiers).isEmpty()
        assertThat(update?.x).isNull()
        assertThat(data.adds).isNull()
        assertThat(data.removes).isNull()
    }

    @Test
    fun `M preserve absent and explicit empty fields W mapMutation`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(emptyList()),
            removes = CapturedChange.Set(emptyList())
        )

        // When
        val result = testedMapper.mapMutation(mutation, tree.snapshot)

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
        val tree = compositionTestTree()
        val pixelIdentity = tree.factory.imageWireframe(tree.layer.identity)
        val placeholderIdentity = tree.factory.placeholderWireframe(tree.layer.identity)
        val layer = tree.layer.copy(
            children = listOf(
                CapturedChild.Wireframe(pixelIdentity),
                CapturedChild.Wireframe(placeholderIdentity)
            )
        )
        val snapshot = tree.snapshot.copy(
            layers = listOf(layer),
            wireframes = listOf(
                CapturedWireframe.Pixel(
                    identity = pixelIdentity,
                    bounds = CapturedBounds(1, 2, 3, 4),
                    resource = PixelResource.Resolved("resource", "image/webp")
                ),
                CapturedWireframe.PrivacyPlaceholder(
                    identity = placeholderIdentity,
                    bounds = CapturedBounds(5, 6, 7, 8),
                    label = "Image"
                )
            )
        )

        // When
        val result = testedMapper.mapFullSnapshot(snapshot)

        // Then
        val wireframes = (result as CaptureWireMappingResult.Success).value.data.wireframes
        assertThat(wireframes).containsExactly(
            MobileSegment.Wireframe.ImageWireframe(
                id = pixelIdentity.wireId,
                x = 1,
                y = 2,
                width = 3,
                height = 4,
                resourceId = "resource",
                mimeType = "image/webp"
            ),
            MobileSegment.Wireframe.PlaceholderWireframe(
                id = placeholderIdentity.wireId,
                x = 5,
                y = 6,
                width = 7,
                height = 8,
                label = "Image"
            )
        )
    }

    @Test
    fun `M map cleared composite operation to source over W mapMutation`() {
        // Given
        val tree = compositionTestTree()
        val base = tree.snapshot.copy(
            layers = listOf(
                tree.layer.copy(compositeOperation = CapturedCompositeOperation.DESTINATION_IN)
            )
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = tree.layer.identity,
                        compositeOperation = CapturedChange.Set(null)
                    )
                )
            )
        )

        // When
        val result = testedMapper.mapMutation(mutation, base)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        assertThat(data.updates?.single()?.compositeOperation)
            .isEqualTo(MobileSegment.CompositeOperation.SOURCEOVER)
    }

    @Test
    fun `M map complete added layers W mapMutation`() {
        // Given
        val tree = compositionTestTree()
        val addedIdentity = tree.factory.layer(tree.window, "added")
        val addedLayer = tree.layer.copy(identity = addedIdentity)
        val replacementRoot = tree.root.copy(
            children = listOf(
                CapturedChild.Layer(tree.layer.identity),
                CapturedChild.Layer(addedIdentity)
            )
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            root = CapturedChange.Set(replacementRoot),
            adds = CapturedChange.Set(listOf(addedLayer)),
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = tree.layer.identity,
                        children = CapturedChange.Set(emptyList())
                    )
                )
            )
        )

        // When
        val result = testedMapper.mapMutation(mutation, tree.snapshot)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        assertThat(data.adds?.single()?.id).isEqualTo(addedIdentity.wireId)
        assertThat(data.adds?.single()?.children?.single()?.id)
            .isEqualTo(tree.wireframeIdentity.wireId)
    }

    @Test
    fun `M map removed layer identities W mapMutation`() {
        // Given
        val tree = compositionTestTree()
        val removedIdentity = tree.factory.layer(tree.window, "removed")
        val removedLayer = tree.layer.copy(identity = removedIdentity, children = emptyList())
        val base = tree.snapshot.copy(
            root = tree.root.copy(
                children = listOf(
                    CapturedChild.Layer(tree.layer.identity),
                    CapturedChild.Layer(removedIdentity)
                )
            ),
            layers = listOf(tree.layer, removedLayer)
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            root = CapturedChange.Set(tree.root),
            removes = CapturedChange.Set(listOf(removedIdentity))
        )

        // When
        val result = testedMapper.mapMutation(mutation, base)

        // Then
        val record = (result as CaptureWireMappingResult.Success).value
        val data = record.data as MobileSegment.MobileIncrementalData.CompositionTreeMutationData
        assertThat(data.removes).containsExactly(removedIdentity.wireId)
    }

    @Test
    fun `M return structured failures W mapFullSnapshot { invalid snapshot }`() {
        // Given
        val snapshot = compositionTestTree().snapshot.copy(root = null)

        // When
        val result = testedMapper.mapFullSnapshot(snapshot)

        // Then
        assertThat(result).isInstanceOf(CaptureWireMappingResult.Invalid::class.java)
        assertThat((result as CaptureWireMappingResult.Invalid).failures.map { it.code })
            .containsExactly(CaptureValidationErrorCode.MISSING_ROOT)
    }

    @Test
    fun `M return structured failure W mapFullSnapshot { validator accepts unresolved pixel }`() {
        // Given
        val tree = compositionTestTree()
        val unresolvedPixel = CapturedWireframe.Pixel(
            identity = tree.wireframeIdentity,
            bounds = CapturedBounds(1, 2, 3, 4),
            resource = PixelResource.Unresolved
        )
        val mapper = DefaultCapturedTreeWireMapper(
            validator = object : CapturedTreeValidator {
                override fun validate(snapshot: CapturedFullSnapshot) = CaptureValidationResult.Valid

                override fun validate(
                    mutation: CapturedMutationSet,
                    base: CapturedFullSnapshot
                ) = CaptureValidationResult.Valid
            }
        )

        // When
        val result = mapper.mapFullSnapshot(tree.snapshot.copy(wireframes = listOf(unresolvedPixel)))

        // Then
        assertThat(result).isEqualTo(
            CaptureWireMappingResult.Invalid(
                listOf(
                    CaptureValidationFailure(
                        CaptureValidationErrorCode.UNRESOLVED_PIXEL_RESOURCE,
                        tree.wireframeIdentity
                    )
                )
            )
        )
    }
}
