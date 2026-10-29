/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedTreeValidatorTest {

    @Forgery
    lateinit var fakeTree: CompositionTestTree

    @StringForgery(regex = "[0-9a-f]{32}")
    lateinit var fakeResourceId: String

    private val testedValidator = DefaultCapturedTreeValidator()

    @Test
    fun `M return valid W validate { minimal full snapshot }`() {
        // Given
        val fakeSnapshot = fakeTree.snapshot

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate { valid background gradient }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
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
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report invalid gradient W validate { fewer than two stops }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(
                backgroundGradient = CapturedBackgroundGradient(
                    stops = listOf(CapturedGradientStop("#000000", 0.0)),
                    startPoint = CapturedPoint(0.0, 0.0),
                    endPoint = CapturedPoint(1.0, 1.0)
                )
            )
        )
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { offset out of range }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
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
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { invalid color }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
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
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { decreasing offsets }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
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
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid gradient W validate { non-finite endpoint coordinate }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
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
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_GRADIENT)
    }

    @Test
    fun `M report invalid modifier W validate { non-finite color matrix entry }`() {
        // Given
        val fakeMatrix = List(19) { 0.0 } + Double.NaN
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(CapturedModifier.ColorMatrix(fakeMatrix)))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { non-finite shadow offset }`() {
        // Given
        val fakeShadow = CapturedModifier.Shadow(
            color = "#000000FF",
            offsetX = Double.NaN,
            offsetY = 0.0,
            radius = 4.0
        )
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(fakeShadow))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { infinite shadow radius }`() {
        // Given
        val fakeShadow = CapturedModifier.Shadow(
            color = "#000000FF",
            offsetX = 0.0,
            offsetY = 0.0,
            radius = Double.POSITIVE_INFINITY
        )
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(fakeShadow))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { infinite gaussian blur radius }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(
            modifiers = listOf(CapturedModifier.GaussianBlur(radius = Double.POSITIVE_INFINITY))
        )
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { infinite saturate value }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(
            modifiers = listOf(CapturedModifier.Saturate(value = Double.POSITIVE_INFINITY))
        )
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { brightness bias out of range }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(
            modifiers = listOf(CapturedModifier.BrightnessBias(value = 1.5))
        )
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { blank clip path }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(CapturedModifier.Clip(path = "")))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { blank mask image resource id }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(CapturedModifier.MaskImage(resourceId = " ")))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { opacity out of range }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(CapturedModifier.Opacity(value = 1.5)))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid modifier W validate { invalid shadow color }`() {
        // Given
        val fakeShadow = CapturedModifier.Shadow(
            color = "not-a-color",
            offsetX = 0.0,
            offsetY = 0.0,
            radius = 4.0
        )
        val fakeLayer = fakeTree.layer.copy(modifiers = listOf(fakeShadow))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_MODIFIER)
    }

    @Test
    fun `M report invalid bounds W validate { negative layer width }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(bounds = CapturedBounds(0, 0, -1, 10))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_BOUNDS)
    }

    @Test
    fun `M report invalid bounds W validate { negative wireframe height }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(bounds = CapturedBounds(0, 0, 10, -1))
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_BOUNDS)
    }

    @Test
    fun `M report unreferenced layer W validate { layer with no parent }`() {
        // Given
        val fakeOrphanIdentity = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-orphan")
        val fakeOrphanLayer = layer(fakeOrphanIdentity, CapturedLayerKind.COMPOSITION_LAYER, emptyList())
        val fakeSnapshot = fakeTree.snapshot.copy(layers = fakeTree.snapshot.layers + fakeOrphanLayer)

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.UNREFERENCED_LAYER)
    }

    @Test
    fun `M report wrong scope W validate mutation { mutation scope differs from base scope }`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = RumViewIdentityScope(fakeTree.scope.value + "-other")
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
    }

    @Test
    fun `M report invalid style W validate { non-finite opacity }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(opacity = Double.NaN)
        )
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { out of range opacity }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(opacity = 1.5)
        )
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { infinite corner radius }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(cornerRadius = Double.POSITIVE_INFINITY)
        )
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { malformed background color }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
            style = CapturedShapeStyle(backgroundColor = "red")
        )
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { malformed border color }`() {
        // Given
        val fakeWireframe = (fakeTree.wireframe as CapturedWireframe.Shape).copy(
            border = CapturedShapeBorder(color = "red", width = 1)
        )
        val fakeSnapshot = fakeTree.snapshot.copy(wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M report invalid style W validate { malformed text style color }`() {
        // Given
        val fakeTextIdentity = fakeTree.factory.textWireframe(fakeTree.layer.identity)
        val fakeWireframe = CapturedWireframe.Text(
            identity = fakeTextIdentity,
            bounds = fakeTree.wireframe.bounds,
            text = "hello",
            textStyle = CapturedTextStyle(family = "Roboto", size = 12, color = "red")
        )
        val fakeLayer = fakeTree.layer.copy(children = listOf(CapturedChild.Wireframe(fakeTextIdentity)))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeLayer), wireframes = listOf(fakeWireframe))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_STYLE)
    }

    @Test
    fun `M return valid W validate { multiple windows below synthetic root }`() {
        // Given
        val fakeScope = fakeTree.scope
        val fakeFactory = fakeTree.factory
        val fakeFirstWindowIdentity = fakeFactory.window("first")
        val fakeSecondWindowIdentity = fakeFactory.window("second")
        val fakeFirstWireframeIdentity = fakeFactory.shapeWireframe(fakeFirstWindowIdentity)
        val fakeSecondWireframeIdentity = fakeFactory.shapeWireframe(fakeSecondWindowIdentity)
        val fakeFirstWindow = layer(
            fakeFirstWindowIdentity,
            CapturedLayerKind.WINDOW_ROOT,
            listOf(CapturedChild.Wireframe(fakeFirstWireframeIdentity))
        )
        val fakeSecondWindow = layer(
            fakeSecondWindowIdentity,
            CapturedLayerKind.WINDOW_ROOT,
            listOf(CapturedChild.Wireframe(fakeSecondWireframeIdentity))
        )
        val fakeRoot = layer(
            fakeFactory.screenRoot(),
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            listOf(
                CapturedChild.Layer(fakeFirstWindowIdentity),
                CapturedChild.Layer(fakeSecondWindowIdentity)
            )
        )
        val fakeSnapshot = CapturedFullSnapshot(
            timestamp = fakeTree.snapshot.timestamp,
            scope = fakeScope,
            root = fakeRoot,
            layers = listOf(fakeFirstWindow, fakeSecondWindow),
            wireframes = listOf(
                semanticWireframe(fakeFirstWireframeIdentity),
                semanticWireframe(fakeSecondWireframeIdentity)
            )
        )

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
        assertThat(fakeRoot.children.map { it.identity })
            .containsExactly(fakeFirstWindowIdentity, fakeSecondWindowIdentity)
    }

    @Test
    fun `M return valid W validate { mixed native compose and composition layers }`() {
        // Given
        val fakeScope = fakeTree.scope
        val fakeFactory = fakeTree.factory
        val fakeWindowIdentity = fakeFactory.window("window")
        val fakeViewIdentity = fakeFactory.view(fakeWindowIdentity, "view")
        val fakeHostIdentity = fakeFactory.composeHost(fakeWindowIdentity, "host")
        val fakeNodeIdentity = fakeFactory.composeNode(fakeHostIdentity, "node")
        val fakeCompositionIdentity = fakeFactory.layer(fakeNodeIdentity, "layer")
        val fakeWireframeIdentity = fakeFactory.shapeWireframe(fakeCompositionIdentity)
        val fakeLayers = listOf(
            layer(fakeWindowIdentity, CapturedLayerKind.WINDOW_ROOT, listOf(CapturedChild.Layer(fakeViewIdentity))),
            layer(fakeViewIdentity, CapturedLayerKind.NATIVE_VIEW, listOf(CapturedChild.Layer(fakeHostIdentity))),
            layer(fakeHostIdentity, CapturedLayerKind.COMPOSE_HOST, listOf(CapturedChild.Layer(fakeNodeIdentity))),
            layer(
                fakeNodeIdentity,
                CapturedLayerKind.COMPOSE_NODE,
                listOf(CapturedChild.Layer(fakeCompositionIdentity))
            ),
            layer(
                fakeCompositionIdentity,
                CapturedLayerKind.COMPOSITION_LAYER,
                listOf(CapturedChild.Wireframe(fakeWireframeIdentity))
            )
        )
        val fakeRoot = layer(
            fakeFactory.screenRoot(),
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            listOf(CapturedChild.Layer(fakeWindowIdentity))
        )

        // When
        val result = testedValidator.validate(
            CapturedFullSnapshot(
                timestamp = fakeTree.snapshot.timestamp,
                scope = fakeScope,
                root = fakeRoot,
                layers = fakeLayers,
                wireframes = listOf(semanticWireframe(fakeWireframeIdentity))
            )
        )

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report missing root W validate`() {
        // Given
        val fakeSnapshot = fakeTree.snapshot.copy(root = null)

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).containsExactly(CaptureValidationErrorCode.MISSING_ROOT)
    }

    @Test
    fun `M report duplicate identity W validate`() {
        // Given
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeTree.layer, fakeTree.layer))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_IDENTITY)
    }

    @Test
    fun `M report dangling references W validate`() {
        // Given
        val fakeMissingLayer = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-missing-layer"
        )
        val fakeMissingWireframe = fakeTree.factory.textWireframe(fakeTree.layer.identity)
        val fakeRoot = fakeTree.root.copy(children = listOf(CapturedChild.Layer(fakeMissingLayer)))
        val fakeLayer = fakeTree.layer.copy(children = listOf(CapturedChild.Wireframe(fakeMissingWireframe)))

        // When
        val result = testedValidator.validate(fakeTree.snapshot.copy(root = fakeRoot, layers = listOf(fakeLayer)))

        // Then
        assertThat(result.codes()).contains(
            CaptureValidationErrorCode.DANGLING_LAYER_REFERENCE,
            CaptureValidationErrorCode.DANGLING_WIREFRAME_REFERENCE
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M validate deep hierarchy without stack overflow W validate`(fakeHasCycle: Boolean) {
        // Given
        val fakeIdentities = List(10_000) { fakeTree.factory.view(fakeTree.window, "deep-$it") }
        val fakeLayers = fakeIdentities.mapIndexed { index, identity ->
            val fakeChild = fakeIdentities.getOrNull(index + 1)
                ?: fakeIdentities.first().takeIf { fakeHasCycle }
            layer(identity, CapturedLayerKind.NATIVE_VIEW, listOfNotNull(fakeChild?.let(CapturedChild::Layer)))
        }
        val fakeSnapshot = fakeTree.snapshot.copy(
            root = fakeTree.root.copy(children = listOf(CapturedChild.Layer(fakeIdentities.first()))),
            layers = fakeLayers.asReversed(),
            wireframes = emptyList()
        )

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        if (fakeHasCycle) {
            assertThat(result.codes()).contains(CaptureValidationErrorCode.CYCLE)
        } else {
            assertThat(result).isEqualTo(CaptureValidationResult.Valid)
        }
    }

    @Test
    fun `M not report cycle W validate { completed layer referenced again }`() {
        // Given
        val fakeRoot = fakeTree.root.copy(children = fakeTree.root.children + fakeTree.root.children)

        // When
        val result = testedValidator.validate(fakeTree.snapshot.copy(root = fakeRoot))

        // Then
        assertThat(result.codes()).containsExactly(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report cycle W validate`() {
        // Given
        val fakeSecondIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeFirst = fakeTree.layer.copy(children = listOf(CapturedChild.Layer(fakeSecondIdentity)))
        val fakeSecond = layer(
            fakeSecondIdentity,
            CapturedLayerKind.COMPOSITION_LAYER,
            listOf(CapturedChild.Layer(fakeFirst.identity))
        )
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeFirst, fakeSecond), wireframes = emptyList())

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CYCLE)
    }

    @Test
    fun `M report cycle W validate { disconnected island not reachable from root }`() {
        // Given
        val fakeAIdentity = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-island-a")
        val fakeBIdentity = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-island-b")
        val fakeA =
            layer(fakeAIdentity, CapturedLayerKind.COMPOSITION_LAYER, listOf(CapturedChild.Layer(fakeBIdentity)))
        val fakeB =
            layer(fakeBIdentity, CapturedLayerKind.COMPOSITION_LAYER, listOf(CapturedChild.Layer(fakeAIdentity)))
        val fakeSnapshot = fakeTree.snapshot.copy(layers = listOf(fakeTree.layer, fakeA, fakeB))

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CYCLE)
    }

    @Test
    fun `M report multiple parents W validate`() {
        // Given
        val fakeSecondIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeSecond = layer(
            fakeSecondIdentity,
            CapturedLayerKind.COMPOSITION_LAYER,
            listOf(CapturedChild.Wireframe(fakeTree.wireframeIdentity))
        )
        val fakeRoot = fakeTree.root.copy(
            children = listOf(
                CapturedChild.Layer(fakeTree.layer.identity),
                CapturedChild.Layer(fakeSecondIdentity)
            )
        )

        // When
        val result = testedValidator.validate(
            fakeTree.snapshot.copy(root = fakeRoot, layers = listOf(fakeTree.layer, fakeSecond))
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report unresolved resource W validate { pixel wireframe }`() {
        // Given
        val fakePixelIdentity = fakeTree.factory.imageWireframe(fakeTree.layer.identity)
        val fakePixel = CapturedWireframe.Pixel(
            identity = fakePixelIdentity,
            bounds = CapturedBounds(1, 2, 3, 4),
            resource = PixelResource.Unresolved
        )
        val fakeLayer = fakeTree.layer.copy(
            children = listOf(CapturedChild.Wireframe(fakePixelIdentity))
        )

        // When
        val result = testedValidator.validate(
            fakeTree.snapshot.copy(layers = listOf(fakeLayer), wireframes = listOf(fakePixel))
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.UNRESOLVED_PIXEL_RESOURCE)
    }

    @Test
    fun `M report identity kind mismatch W validate { wrong wireframe namespace }`() {
        // Given
        val fakePixel = CapturedWireframe.Pixel(
            identity = fakeTree.wireframeIdentity,
            bounds = CapturedBounds(1, 2, 3, 4),
            resource = PixelResource.Resolved(fakeResourceId)
        )

        // When
        val result = testedValidator.validate(fakeTree.snapshot.copy(wireframes = listOf(fakePixel)))

        // Then
        assertThat(result.codes()).containsExactly(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH)
    }

    @Test
    fun `M return valid W validate { resolved pixel and privacy placeholder }`() {
        // Given
        val fakePixelIdentity = fakeTree.factory.imageWireframe(fakeTree.layer.identity)
        val fakePlaceholderIdentity = fakeTree.factory.placeholderWireframe(fakeTree.layer.identity)
        val fakeLayer = fakeTree.layer.copy(
            children = listOf(
                CapturedChild.Wireframe(fakePixelIdentity),
                CapturedChild.Wireframe(fakePlaceholderIdentity)
            )
        )
        val fakeSnapshot = fakeTree.snapshot.copy(
            layers = listOf(fakeLayer),
            wireframes = listOf(
                CapturedWireframe.Pixel(
                    identity = fakePixelIdentity,
                    bounds = CapturedBounds(0, 0, 5, 5),
                    resource = PixelResource.Resolved(fakeResourceId, "image/webp")
                ),
                CapturedWireframe.PrivacyPlaceholder(
                    identity = fakePlaceholderIdentity,
                    bounds = CapturedBounds(5, 0, 5, 5),
                    label = "Image"
                )
            )
        )

        // When
        val result = testedValidator.validate(fakeSnapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate { unreferenced hidden slot wireframes }`(
        @IntForgery fakeSlotId: Int
    ) {
        // Given
        val fakeWebViewIdentity = fakeTree.factory.webViewWireframe(fakeTree.layer.identity, fakeSlotId.toLong())
        val fakeHiddenWebView = CapturedWireframe.WebView(
            identity = fakeWebViewIdentity,
            bounds = CapturedBounds(0, 0, 0, 0),
            isVisible = false
        )

        // When
        val result = testedValidator.validate(
            fakeTree.snapshot.copy(
                wireframes = listOf(fakeTree.wireframe, fakeHiddenWebView)
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
        val fakeIdentity = fakeTree.factory.webViewWireframe(fakeTree.layer.identity, fakeSlotId.toLong())
        val fakeVisibleWebView = CapturedWireframe.WebView(
            identity = fakeIdentity,
            bounds = CapturedBounds(0, 0, 10, 10),
            isVisible = true
        )

        // When
        val result = testedValidator.validate(
            fakeTree.snapshot.copy(wireframes = listOf(fakeTree.wireframe, fakeVisibleWebView))
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.UNREFERENCED_WIREFRAME)
    }

    @Test
    fun `M report mismatched reference W validate { same wire ID from another scope }`() {
        // Given
        val fakeWrongIdentity = fakeTree.wireframeIdentity.copy(
            scope = RumViewIdentityScope(
                fakeTree.scope.value + "-other"
            )
        )
        val fakeLayer = fakeTree.layer.copy(children = listOf(CapturedChild.Wireframe(fakeWrongIdentity)))

        // When
        val result = testedValidator.validate(fakeTree.snapshot.copy(layers = listOf(fakeLayer)))

        // Then
        assertThat(result.codes()).contains(
            CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE,
            CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH
        )
    }

    @Test
    fun `M report identity kind mismatch W validate { layer kind does not match identity }`() {
        // Given
        val fakeLayer = fakeTree.layer.copy(kind = CapturedLayerKind.NATIVE_VIEW)

        // When
        val result = testedValidator.validate(fakeTree.snapshot.copy(layers = listOf(fakeLayer)))

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.IDENTITY_KIND_MISMATCH)
    }

    @Test
    fun `M report wrong scope W validate { layer belongs to another RUM view }`() {
        // Given
        val fakeOtherFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeTree.scope.value + "-other"))
        val fakeOtherLayerIdentity = fakeOtherFactory.layer(fakeOtherFactory.window("window"), "layer")
        val fakeOtherLayer = layer(fakeOtherLayerIdentity, CapturedLayerKind.COMPOSITION_LAYER, emptyList())
        val fakeRoot = fakeTree.root.copy(children = listOf(CapturedChild.Layer(fakeOtherLayerIdentity)))

        // When
        val result = testedValidator.validate(
            fakeTree.snapshot.copy(root = fakeRoot, layers = listOf(fakeOtherLayer), wireframes = emptyList())
        )

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
    }

    @Test
    fun `M return valid W validate mutation { sparse bounds update }`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(
                listOf(CapturedLayerUpdate(identity = fakeTree.layer.identity, x = CapturedChange.Set(42)))
            )
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate mutation { wireframe child removed separately }`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
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
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M return valid W validate mutation { wireframe child added separately }`() {
        // Given
        val fakeAddedWireframe = fakeTree.factory.textWireframe(fakeTree.layer.identity)
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = fakeTree.layer.identity,
                        children = CapturedChange.Set(
                            listOf(CapturedChild.Wireframe(fakeAddedWireframe))
                        )
                    )
                )
            )
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report wrong scope W validate mutation { wireframe child added separately }`() {
        // Given
        val fakeWrongIdentity = fakeTree.factory.textWireframe(fakeTree.layer.identity)
            .copy(scope = RumViewIdentityScope(fakeTree.scope.value + "-other"))
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(
                        identity = fakeTree.layer.identity,
                        children = CapturedChange.Set(
                            listOf(CapturedChild.Wireframe(fakeWrongIdentity))
                        )
                    )
                )
            )
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE)
    }

    @Test
    fun `M report contradiction W validate mutation { same layer removed and updated }`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            removes = CapturedChange.Set(listOf(fakeTree.layer.identity)),
            updates = CapturedChange.Set(listOf(CapturedLayerUpdate(fakeTree.layer.identity)))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CONTRADICTORY_MUTATION)
    }

    @Test
    fun `M report contradiction W validate mutation { added layer collides with existing wireframe }`() {
        // Given
        val fakeAddedLayer = CapturedLayer(
            identity = fakeTree.wireframeIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = emptyList()
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeAddedLayer))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.CONTRADICTORY_MUTATION)
    }

    @Test
    fun `M report multiple parents W validate mutation { added layer references existing wireframe }`() {
        // Given
        val fakeSecondLayerIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeSecondLayer = CapturedLayer(
            identity = fakeSecondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(fakeTree.wireframeIdentity))
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeSecondLayer))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report multiple parents W validate mutation { two added layers reference same undelivered wireframe }`() {
        // Given
        val fakeUndeliveredWireframeIdentity = fakeTree.factory.textWireframe(fakeTree.layer.identity)
        val fakeFirstLayerIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-first-layer"
        )
        val fakeSecondLayerIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeFirstLayer = CapturedLayer(
            identity = fakeFirstLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(fakeUndeliveredWireframeIdentity))
        )
        val fakeSecondLayer = CapturedLayer(
            identity = fakeSecondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(fakeUndeliveredWireframeIdentity))
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeFirstLayer, fakeSecondLayer))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.MULTIPLE_PARENTS)
    }

    @Test
    fun `M report reference identity mismatch W validate mutation { known wireframe id under mismatched identity }`() {
        // Given
        val fakeCollidingIdentity = fakeTree.wireframeIdentity.copy(localId = "different-local-id")
        val fakeSecondLayerIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeSecondLayer = CapturedLayer(
            identity = fakeSecondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(fakeCollidingIdentity))
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeSecondLayer))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH)
    }

    @Test
    fun `M report duplicate identity W validate mutation { undelivered wireframe id collides with existing layer }`() {
        // Given
        val fakeCollidingIdentity = fakeTree.factory.textWireframe(fakeTree.layer.identity)
            .copy(wireId = fakeTree.layer.identity.wireId)
        val fakeSecondLayerIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeSecondLayer = CapturedLayer(
            identity = fakeSecondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(fakeCollidingIdentity))
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeSecondLayer))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_IDENTITY)
    }

    @Test
    fun `M report duplicate identity W validate mutation { undelivered wireframe id collides with root }`() {
        // Given
        val fakeCollidingIdentity = fakeTree.factory.textWireframe(fakeTree.layer.identity)
            .copy(wireId = fakeTree.root.identity.wireId)
        val fakeSecondLayerIdentity = fakeTree.factory.layer(
            fakeTree.window,
            fakeTree.layer.identity.localId + "-second-layer"
        )
        val fakeSecondLayer = CapturedLayer(
            identity = fakeSecondLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(0, 0, 1, 1),
            children = listOf(CapturedChild.Wireframe(fakeCollidingIdentity))
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeSecondLayer))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_IDENTITY)
    }

    @Test
    fun `M report duplicate operations W validate mutation`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(
                listOf(
                    CapturedLayerUpdate(fakeTree.layer.identity),
                    CapturedLayerUpdate(fakeTree.layer.identity)
                )
            )
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.DUPLICATE_MUTATION_OPERATION)
    }

    @Test
    fun `M report every pairwise contradiction W validate mutation { add remove and update }`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            adds = CapturedChange.Set(listOf(fakeTree.layer)),
            removes = CapturedChange.Set(listOf(fakeTree.layer.identity)),
            updates = CapturedChange.Set(listOf(CapturedLayerUpdate(fakeTree.layer.identity)))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes().count { it == CaptureValidationErrorCode.CONTRADICTORY_MUTATION })
            .isGreaterThanOrEqualTo(3)
    }

    @Test
    fun `M report mismatched target W validate mutation { same wire ID from another scope }`() {
        // Given
        val fakeWrongIdentity = fakeTree.layer.identity.copy(
            scope = RumViewIdentityScope(
                fakeTree.scope.value + "-other"
            )
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            updates = CapturedChange.Set(listOf(CapturedLayerUpdate(fakeWrongIdentity)))
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(
            CaptureValidationErrorCode.WRONG_IDENTITY_SCOPE,
            CaptureValidationErrorCode.REFERENCE_IDENTITY_MISMATCH
        )
    }

    @Test
    fun `M return valid W validate mutation { root replacement }`() {
        // Given
        val fakeReplacement = fakeTree.root.copy(bounds = CapturedBounds(0, 0, 200, 400))
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            root = CapturedChange.Set(fakeReplacement)
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M report invalid root replacement W validate mutation`() {
        // Given
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            root = CapturedChange.Set(fakeTree.layer)
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_ROOT_REPLACEMENT)
    }

    @Test
    fun `M report invalid root replacement W validate mutation { different identity from base root }`() {
        // Given
        val fakeReplacement = fakeTree.root.copy(
            identity = fakeTree.root.identity.copy(wireId = fakeTree.root.identity.wireId + 1)
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            root = CapturedChange.Set(fakeReplacement)
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeTree.snapshot)

        // Then
        assertThat(result.codes()).contains(CaptureValidationErrorCode.INVALID_ROOT_REPLACEMENT)
    }

    @Test
    fun `M report invalid root replacement W validate mutation { new root collides with existing wireframe }`() {
        // Given
        val fakeBaseWithoutRoot = fakeTree.snapshot.copy(root = null)
        val fakeCollidingRoot = fakeTree.root.copy(
            identity = fakeTree.root.identity.copy(wireId = fakeTree.wireframeIdentity.wireId)
        )
        val fakeMutation = CapturedMutationSet(
            timestamp = fakeTree.snapshot.timestamp + 1L,
            scope = fakeTree.scope,
            root = CapturedChange.Set(fakeCollidingRoot)
        )

        // When
        val result = testedValidator.validate(fakeMutation, fakeBaseWithoutRoot)

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
