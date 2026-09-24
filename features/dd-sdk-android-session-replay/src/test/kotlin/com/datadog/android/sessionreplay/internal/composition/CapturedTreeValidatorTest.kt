/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedTreeValidatorTest {

    private val testedValidator = DefaultCapturedTreeValidator()

    @Test
    fun `M return valid W validate { minimal full snapshot }`() {
        // Given
        val snapshot = compositionTestTree().snapshot

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate { valid background gradient }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(
                        CapturedGradientStop("#000000", 0.0),
                        CapturedGradientStop("#FFFFFFFF", 1.0)
                    ),
                    startPoint = CapturedPoint(0.0, 0.0),
                    endPoint = CapturedPoint(1.0, 1.0)
                )
            )
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report invalid gradient W validate { fewer than two stops }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(CapturedGradientStop("#000000", 0.0)),
                    startPoint = CapturedPoint(0.0, 0.0),
                    endPoint = CapturedPoint(1.0, 1.0)
                )
            )
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { offset out of range }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(
                        CapturedGradientStop("#000000", 0.0),
                        CapturedGradientStop("#FFFFFF", 5.0)
                    ),
                    startPoint = CapturedPoint(0.0, 0.0),
                    endPoint = CapturedPoint(1.0, 1.0)
                )
            )
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { invalid color }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(
                        CapturedGradientStop("not-a-color", 0.0),
                        CapturedGradientStop("#FFFFFF", 1.0)
                    ),
                    startPoint = CapturedPoint(0.0, 0.0),
                    endPoint = CapturedPoint(1.0, 1.0)
                )
            )
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { decreasing offsets }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(
                        CapturedGradientStop("#000000", 0.6),
                        CapturedGradientStop("#FFFFFF", 0.4)
                    ),
                    startPoint = CapturedPoint(0.0, 0.0),
                    endPoint = CapturedPoint(1.0, 1.0)
                )
            )
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { non-finite endpoint coordinate }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(
                        CapturedGradientStop("#000000", 0.0),
                        CapturedGradientStop("#FFFFFF", 1.0)
                    ),
                    startPoint = CapturedPoint(Double.NaN, 0.0),
                    endPoint = CapturedPoint(1.0, Double.POSITIVE_INFINITY)
                )
            )
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid modifier W validate { non-finite color matrix entry }`() {
        // Given
        val tree = compositionTestTree()
        val matrix = List(19) { 0.0 } + Double.NaN
        val layer = tree.layer.copy(modifiers = listOf(CapturedModifier.ColorMatrix(matrix)))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { non-finite shadow offset }`() {
        // Given
        val tree = compositionTestTree()
        val shadow = CapturedModifier.Shadow(
            color = "#000000FF",
            offsetX = Double.NaN,
            offsetY = 0.0,
            radius = 4.0
        )
        val layer = tree.layer.copy(modifiers = listOf(shadow))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { infinite shadow radius }`() {
        // Given
        val tree = compositionTestTree()
        val shadow = CapturedModifier.Shadow(
            color = "#000000FF",
            offsetX = 0.0,
            offsetY = 0.0,
            radius = Double.POSITIVE_INFINITY
        )
        val layer = tree.layer.copy(modifiers = listOf(shadow))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { infinite gaussian blur radius }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(
            modifiers = listOf(CapturedModifier.GaussianBlur(radius = Double.POSITIVE_INFINITY))
        )
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { infinite saturate value }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(
            modifiers = listOf(CapturedModifier.Saturate(value = Double.POSITIVE_INFINITY))
        )
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { brightness bias out of range }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(
            modifiers = listOf(CapturedModifier.BrightnessBias(value = 1.5))
        )
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { blank clip path }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(modifiers = listOf(CapturedModifier.Clip(path = "")))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { blank mask image resource id }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(modifiers = listOf(CapturedModifier.MaskImage(resourceId = " ")))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { opacity out of range }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(modifiers = listOf(CapturedModifier.Opacity(value = 1.5)))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { invalid shadow color }`() {
        // Given
        val tree = compositionTestTree()
        val shadow = CapturedModifier.Shadow(
            color = "not-a-color",
            offsetX = 0.0,
            offsetY = 0.0,
            radius = 4.0
        )
        val layer = tree.layer.copy(modifiers = listOf(shadow))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid bounds W validate { negative layer width }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(bounds = CapturedBounds(0, 0, -1, 10))
        val snapshot = tree.snapshot.copy(layers = listOf(layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_BOUNDS)
    }

    @Test
    fun `M report invalid bounds W validate { negative wireframe height }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(bounds = CapturedBounds(0, 0, 10, -1))
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_BOUNDS)
    }

    @Test
    fun `M report unreferenced layer W validate { layer with no parent }`() {
        // Given
        val tree = compositionTestTree()
        val orphanIdentity = tree.factory.layer(tree.window, "orphan")
        val orphanLayer = layer(orphanIdentity, CapturedLayerKind.COMPOSITION_LAYER, emptyList())
        val snapshot = tree.snapshot.copy(layers = tree.snapshot.layers + orphanLayer)

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.UNREFERENCED_LAYER)
    }

    @Test
    fun `M report wrong scope W validate mutation { mutation scope differs from base scope }`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = RumViewIdentityScope("other-view")
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
    }

    @Test
    fun `M report invalid style W validate { non-finite opacity }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(opacity = Double.NaN)
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { out of range opacity }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(opacity = 1.5)
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { infinite corner radius }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(cornerRadius = Double.POSITIVE_INFINITY)
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { malformed background color }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(backgroundColor = "red")
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { malformed border color }`() {
        // Given
        val tree = compositionTestTree()
        val wireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            border = CapturedShapeBorder(color = "red", width = 1)
        )
        val snapshot = tree.snapshot.copy(wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { malformed text style color }`() {
        // Given
        val tree = compositionTestTree()
        val textIdentity = tree.factory.textWireframe(tree.layer.identity)
        val wireframe = CapturedWireframe.Text(
            identity = textIdentity,
            bounds = tree.wireframe.bounds,
            text = "hello",
            textStyle = CapturedTextStyle(family = "Roboto", size = 12, color = "red")
        )
        val layer = tree.layer.copy(children = listOf(CapturedChild.Wireframe(textIdentity)))
        val snapshot = tree.snapshot.copy(layers = listOf(layer), wireframes = listOf(wireframe))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M return valid W validate { multiple windows below synthetic root }`() {
        // Given
        val scope = RumViewIdentityScope("view")
        val factory = DefaultCapturedIdentityFactory(scope)
        val firstWindowIdentity = factory.window("first")
        val secondWindowIdentity = factory.window("second")
        val firstWireframeIdentity = factory.shapeWireframe(firstWindowIdentity)
        val secondWireframeIdentity = factory.shapeWireframe(secondWindowIdentity)
        val firstWindow = layer(
            firstWindowIdentity,
            CapturedLayerKind.WINDOW_ROOT,
            listOf(CapturedChild.Wireframe(firstWireframeIdentity))
        )
        val secondWindow = layer(
            secondWindowIdentity,
            CapturedLayerKind.WINDOW_ROOT,
            listOf(CapturedChild.Wireframe(secondWireframeIdentity))
        )
        val root = layer(
            factory.screenRoot(),
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            listOf(
                CapturedChild.Layer(firstWindowIdentity),
                CapturedChild.Layer(secondWindowIdentity)
            )
        )
        val snapshot = CapturedFullSnapshot(
            timestamp = 1,
            scope = scope,
            root = root,
            layers = listOf(firstWindow, secondWindow),
            wireframes = listOf(
                semanticWireframe(firstWireframeIdentity),
                semanticWireframe(secondWireframeIdentity)
            )
        )

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
        assertThat(root.children.map { it.identity })
            .containsExactly(firstWindowIdentity, secondWindowIdentity)
    }

    @Test
    fun `M return valid W validate { mixed native compose and composition layers }`() {
        // Given
        val scope = RumViewIdentityScope("view")
        val factory = DefaultCapturedIdentityFactory(scope)
        val windowIdentity = factory.window("window")
        val viewIdentity = factory.view(windowIdentity, "view")
        val hostIdentity = factory.composeHost(windowIdentity, "host")
        val nodeIdentity = factory.composeNode(hostIdentity, "node")
        val compositionIdentity = factory.layer(nodeIdentity, "layer")
        val wireframeIdentity = factory.shapeWireframe(compositionIdentity)
        val layers = listOf(
            layer(windowIdentity, CapturedLayerKind.WINDOW_ROOT, listOf(CapturedChild.Layer(viewIdentity))),
            layer(viewIdentity, CapturedLayerKind.NATIVE_VIEW, listOf(CapturedChild.Layer(hostIdentity))),
            layer(hostIdentity, CapturedLayerKind.COMPOSE_HOST, listOf(CapturedChild.Layer(nodeIdentity))),
            layer(nodeIdentity, CapturedLayerKind.COMPOSE_NODE, listOf(CapturedChild.Layer(compositionIdentity))),
            layer(
                compositionIdentity,
                CapturedLayerKind.COMPOSITION_LAYER,
                listOf(CapturedChild.Wireframe(wireframeIdentity))
            )
        )
        val root = layer(
            factory.screenRoot(),
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            listOf(CapturedChild.Layer(windowIdentity))
        )

        // When
        val result = testedValidator.validate(
            CapturedFullSnapshot(
                timestamp = 1,
                scope = scope,
                root = root,
                layers = layers,
                wireframes = listOf(semanticWireframe(wireframeIdentity))
            )
        )

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report missing root W validate`() {
        // Given
        val snapshot = compositionTestTree().snapshot.copy(root = null)

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).containsExactly(CaptureValidationErrorCode.MISSING_ROOT)
    }

    @Test
    fun `M report duplicate identity W validate`() {
        // Given
        val tree = compositionTestTree()
        val snapshot = tree.snapshot.copy(layers = listOf(tree.layer, tree.layer))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_IDENTITY)
    }

    @Test
    fun `M report dangling references W validate`() {
        // Given
        val tree = compositionTestTree()
        val missingLayer = tree.factory.layer(tree.window, "missing-layer")
        val missingWireframe = tree.factory.textWireframe(tree.layer.identity)
        val root = tree.root.copy(children = listOf(CapturedChild.Layer(missingLayer)))
        val layer = tree.layer.copy(children = listOf(CapturedChild.Wireframe(missingWireframe)))

        // When
        val result = testedValidator.validate(tree.snapshot.copy(root = root, layers = listOf(layer)))

        // Then
        assertThat(result.codes()).contains(
            CaptureValidationErrorCode.DANGLING_LAYER_REFERENCE,
            CaptureValidationErrorCode.DANGLING_WIREFRAME_REFERENCE
        )
    }

    @Test
    fun `M report cycle W validate`() {
        // Given
        val tree = compositionTestTree()
        val secondIdentity = tree.factory.layer(tree.window, "second-layer")
        val first = tree.layer.copy(children = listOf(CapturedChild.Layer(secondIdentity)))
        val second = layer(
            secondIdentity,
            CapturedLayerKind.COMPOSITION_LAYER,
            listOf(CapturedChild.Layer(first.identity))
        )
        val snapshot = tree.snapshot.copy(layers = listOf(first, second), wireframes = emptyList())

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CYCLE)
    }

    @Test
    fun `M report cycle W validate { disconnected island not reachable from root }`() {
        // Given
        val tree = compositionTestTree()
        val aIdentity = tree.factory.layer(tree.window, "island-a")
        val bIdentity = tree.factory.layer(tree.window, "island-b")
        val a = layer(aIdentity, CapturedLayerKind.COMPOSITION_LAYER, listOf(CapturedChild.Layer(bIdentity)))
        val b = layer(bIdentity, CapturedLayerKind.COMPOSITION_LAYER, listOf(CapturedChild.Layer(aIdentity)))
        val snapshot = tree.snapshot.copy(layers = listOf(tree.layer, a, b))

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CYCLE)
    }

    @Test
    fun `M report multiple parents W validate`() {
        // Given
        val tree = compositionTestTree()
        val secondIdentity = tree.factory.layer(tree.window, "second-layer")
        val second = layer(
            secondIdentity,
            CapturedLayerKind.COMPOSITION_LAYER,
            listOf(CapturedChild.Wireframe(tree.wireframeIdentity))
        )
        val root = tree.root.copy(
            children = listOf(
                CapturedChild.Layer(tree.layer.identity),
                CapturedChild.Layer(secondIdentity)
            )
        )

        // When
        val result = testedValidator.validate(
            tree.snapshot.copy(root = root, layers = listOf(tree.layer, second))
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report unresolved resource W validate { pixel wireframe }`() {
        // Given
        val tree = compositionTestTree()
        val pixelIdentity = tree.factory.imageWireframe(tree.layer.identity)
        val pixel = CapturedWireframe.Pixel(
            identity = pixelIdentity,
            bounds = CapturedBounds(1, 2, 3, 4),
            resource = PixelResource.Unresolved
        )
        val layer = tree.layer.copy(
            children = listOf(CapturedChild.Wireframe(pixelIdentity))
        )

        // When
        val result = testedValidator.validate(
            tree.snapshot.copy(layers = listOf(layer), wireframes = listOf(pixel))
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.UNRESOLVED_PIXEL_RESOURCE)
    }

    @Test
    fun `M report identity kind mismatch W validate { wrong wireframe namespace }`() {
        // Given
        val tree = compositionTestTree()
        val pixel = CapturedWireframe.Pixel(
            identity = tree.wireframeIdentity,
            bounds = CapturedBounds(1, 2, 3, 4),
            resource = PixelResource.Resolved("resource")
        )

        // When
        val result = testedValidator.validate(tree.snapshot.copy(wireframes = listOf(pixel)))

        // Then
        assertThat(result.codes()).containsExactly(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH)
    }

    @Test
    fun `M return valid W validate { resolved pixel and privacy placeholder }`() {
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
                    bounds = CapturedBounds(0, 0, 5, 5),
                    resource = PixelResource.Resolved("resource", "image/webp")
                ),
                CapturedWireframe.PrivacyPlaceholder(
                    identity = placeholderIdentity,
                    bounds = CapturedBounds(5, 0, 5, 5),
                    label = "Image"
                )
            )
        )

        // When
        val result = testedValidator.validate(snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate { unreferenced hidden slot wireframes }`(
        @IntForgery fakeSlotId: Int
    ) {
        // Given
        val tree = compositionTestTree()
        val webViewIdentity = tree.factory.webViewWireframe(tree.layer.identity, fakeSlotId.toLong())
        val hiddenWebView = CapturedWireframe.WebView(
            identity = webViewIdentity,
            bounds = CapturedBounds(0, 0, 0, 0),
            isVisible = false
        )

        // When
        val result = testedValidator.validate(
            tree.snapshot.copy(
                wireframes = listOf(tree.wireframe, hiddenWebView)
            )
        )

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report unreferenced wireframe W validate { visible slot wireframe }`(
        @IntForgery fakeSlotId: Int
    ) {
        // Given
        val tree = compositionTestTree()
        val identity = tree.factory.webViewWireframe(tree.layer.identity, fakeSlotId.toLong())
        val visibleWebView = CapturedWireframe.WebView(
            identity = identity,
            bounds = CapturedBounds(0, 0, 10, 10),
            isVisible = true
        )

        // When
        val result = testedValidator.validate(
            tree.snapshot.copy(wireframes = listOf(tree.wireframe, visibleWebView))
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.UNREFERENCED_WIREFRAME)
    }

    @Test
    fun `M report mismatched reference W validate { same wire ID from another scope }`() {
        // Given
        val tree = compositionTestTree()
        val wrongIdentity = tree.wireframeIdentity.copy(scope = RumViewIdentityScope("other-view"))
        val layer = tree.layer.copy(children = listOf(CapturedChild.Wireframe(wrongIdentity)))

        // When
        val result = testedValidator.validate(tree.snapshot.copy(layers = listOf(layer)))

        // Then
        assertThat(result.codes()).contains(
            CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE,
            CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH
        )
    }

    @Test
    fun `M report identity kind mismatch W validate { layer kind does not match identity }`() {
        // Given
        val tree = compositionTestTree()
        val layer = tree.layer.copy(kind = CapturedLayerKind.NATIVE_VIEW)

        // When
        val result = testedValidator.validate(tree.snapshot.copy(layers = listOf(layer)))

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH)
    }

    @Test
    fun `M report wrong scope W validate { layer belongs to another RUM view }`() {
        // Given
        val tree = compositionTestTree()
        val otherFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("other-view"))
        val otherLayerIdentity = otherFactory.layer(otherFactory.window("window"), "layer")
        val otherLayer = layer(otherLayerIdentity, CapturedLayerKind.COMPOSITION_LAYER, emptyList())
        val root = tree.root.copy(children = listOf(CapturedChild.Layer(otherLayerIdentity)))

        // When
        val result = testedValidator.validate(
            tree.snapshot.copy(root = root, layers = listOf(otherLayer), wireframes = emptyList())
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
    }

    @Test
    fun `M return valid W validate mutation { sparse bounds update }`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(
                listOf(CapturedLayerUpdate(identity = tree.layer.identity, x = CapturedChange.Set(42)))
            )
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate mutation { wireframe child removed separately }`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
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
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate mutation { wireframe child added separately }`() {
        // Given
        val tree = compositionTestTree()
        val addedWireframe = tree.factory.textWireframe(tree.layer.identity)
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = tree.layer.identity,
                        children = CapturedChange.Set(
                            listOf(CapturedChild.Wireframe(addedWireframe))
                        )
                    )
                )
            )
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report wrong scope W validate mutation { wireframe child added separately }`() {
        // Given
        val tree = compositionTestTree()
        val wrongIdentity = tree.factory.textWireframe(tree.layer.identity)
            .copy(scope = RumViewIdentityScope("other-view"))
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = tree.layer.identity,
                        children = CapturedChange.Set(
                            listOf(CapturedChild.Wireframe(wrongIdentity))
                        )
                    )
                )
            )
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
    }

    @Test
    fun `M report contradiction W validate mutation { same layer removed and updated }`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            removes = CapturedChange.Set(listOf(tree.layer.identity)),
            updates = CapturedChange.Set(listOf(CapturedLayerUpdate(tree.layer.identity)))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CONTRADICTORY_MUTATION)
    }

    @Test
    fun `M report contradiction W validate mutation { added layer collides with existing wireframe }`() {
        // Given
        val tree = compositionTestTree()
        val addedLayer = CapturedLayer(
            identity = tree.wireframeIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = emptyList()
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(addedLayer))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CONTRADICTORY_MUTATION)
    }

    @Test
    fun `M report multiple parents W validate mutation { added layer references existing wireframe }`() {
        // Given
        val tree = compositionTestTree()
        val secondLayerIdentity = tree.factory.layer(tree.window, "second-layer")
        val secondLayer = CapturedLayer(
            identity = secondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(tree.wireframeIdentity))
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(secondLayer))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report multiple parents W validate mutation { two added layers reference same undelivered wireframe }`() {
        // Given
        val tree = compositionTestTree()
        val undeliveredWireframeIdentity = tree.factory.textWireframe(tree.layer.identity)
        val firstLayerIdentity = tree.factory.layer(tree.window, "first-layer")
        val secondLayerIdentity = tree.factory.layer(tree.window, "second-layer")
        val firstLayer = CapturedLayer(
            identity = firstLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(undeliveredWireframeIdentity))
        )
        val secondLayer = CapturedLayer(
            identity = secondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(undeliveredWireframeIdentity))
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(firstLayer, secondLayer))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report reference identity mismatch W validate mutation { known wireframe id under mismatched identity }`() {
        // Given
        val tree = compositionTestTree()
        val collidingIdentity = tree.wireframeIdentity.copy(localId = "different-local-id")
        val secondLayerIdentity = tree.factory.layer(tree.window, "second-layer")
        val secondLayer = CapturedLayer(
            identity = secondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(collidingIdentity))
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(secondLayer))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH)
    }

    @Test
    fun `M report duplicate identity W validate mutation { undelivered wireframe id collides with existing layer }`() {
        // Given
        val tree = compositionTestTree()
        val collidingIdentity = tree.factory.textWireframe(tree.layer.identity)
            .copy(wireId = tree.layer.identity.wireId)
        val secondLayerIdentity = tree.factory.layer(tree.window, "second-layer")
        val secondLayer = CapturedLayer(
            identity = secondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(collidingIdentity))
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(secondLayer))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_IDENTITY)
    }

    @Test
    fun `M report duplicate identity W validate mutation { undelivered wireframe id collides with root }`() {
        // Given
        val tree = compositionTestTree()
        val collidingIdentity = tree.factory.textWireframe(tree.layer.identity)
            .copy(wireId = tree.root.identity.wireId)
        val secondLayerIdentity = tree.factory.layer(tree.window, "second-layer")
        val secondLayer = CapturedLayer(
            identity = secondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(collidingIdentity))
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(secondLayer))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_IDENTITY)
    }

    @Test
    fun `M report duplicate operations W validate mutation`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(tree.layer.identity),
                    CapturedLayerUpdate(tree.layer.identity)
                )
            )
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_MUTATION_OPERATION)
    }

    @Test
    fun `M report every pairwise contradiction W validate mutation { add remove and update }`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            adds = CapturedChange.Set(listOf(tree.layer)),
            removes = CapturedChange.Set(listOf(tree.layer.identity)),
            updates = CapturedChange.Set(listOf(CapturedLayerUpdate(tree.layer.identity)))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes().count { it == CaptureValidationErrorCode.CONTRADICTORY_MUTATION })
            .isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `M report mismatched target W validate mutation { same wire ID from another scope }`() {
        // Given
        val tree = compositionTestTree()
        val wrongIdentity = tree.layer.identity.copy(scope = RumViewIdentityScope("other-view"))
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            updates = CapturedChange.Set(listOf(CapturedLayerUpdate(wrongIdentity)))
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(
            CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE,
            CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH
        )
    }

    @Test
    fun `M return valid W validate mutation { root replacement }`() {
        // Given
        val tree = compositionTestTree()
        val replacement = tree.root.copy(bounds = CapturedBounds(0, 0, 200, 400))
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            root = CapturedChange.Set(replacement)
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report invalid root replacement W validate mutation`() {
        // Given
        val tree = compositionTestTree()
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            root = CapturedChange.Set(tree.layer)
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_ROOT_REPLACEMENT)
    }

    @Test
    fun `M report invalid root replacement W validate mutation { different identity from base root }`() {
        // Given
        val tree = compositionTestTree()
        val replacement = tree.root.copy(
            identity = tree.root.identity.copy(wireId = tree.root.identity.wireId + 1)
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            root = CapturedChange.Set(replacement)
        )

        // When
        val result = testedValidator.validate(mutation, tree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_ROOT_REPLACEMENT)
    }

    @Test
    fun `M report invalid root replacement W validate mutation { new root collides with existing wireframe }`() {
        // Given
        val tree = compositionTestTree()
        val baseWithoutRoot = tree.snapshot.copy(root = null)
        val collidingRoot = tree.root.copy(
            identity = tree.root.identity.copy(wireId = tree.wireframeIdentity.wireId)
        )
        val mutation = CapturedMutationSet(
            timestamp = 456,
            scope = tree.scope,
            root = CapturedChange.Set(collidingRoot)
        )

        // When
        val result = testedValidator.validate(mutation, baseWithoutRoot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_ROOT_REPLACEMENT)
    }

    private fun layer(
        identity: CapturedIdentity,
        kind: CapturedLayerKind,
        children: List<CapturedChild>
    ) = CapturedLayer(
        identity = identity,
        kind = kind,
        bounds = CapturedBounds(0, 0, 10, 10),
        children = children
    )

    private fun semanticWireframe(identity: CapturedIdentity) = CapturedWireframe.Shape(
        identity = identity,
        bounds = CapturedBounds(0, 0, 10, 10)
    )

    private fun CaptureValidationResult.codes(): List<CaptureValidationErrorCode> =
        (this as CaptureValidationResult.Invalid).failures.map { it.code }
}
