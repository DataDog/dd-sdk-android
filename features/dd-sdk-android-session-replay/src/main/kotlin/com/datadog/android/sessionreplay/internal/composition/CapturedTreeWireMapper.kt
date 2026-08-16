/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("TooManyFunctions") // This file contains stateless wire-model conversion extensions.

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.model.MobileSegment

internal sealed interface CaptureWireMappingResult<out T> {
    data class Success<T>(val value: T) : CaptureWireMappingResult<T>

    data class Invalid(
        val failures: List<CaptureValidationFailure>
    ) : CaptureWireMappingResult<Nothing>
}

internal interface CapturedTreeWireMapper {
    fun mapFullSnapshot(
        snapshot: CapturedFullSnapshot
    ): CaptureWireMappingResult<MobileSegment.MobileRecord.MobileFullSnapshotRecord>

    fun mapMutation(
        mutation: CapturedMutationSet,
        base: CapturedFullSnapshot
    ): CaptureWireMappingResult<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord>
}

internal class DefaultCapturedTreeWireMapper(
    private val validator: CapturedTreeValidator = DefaultCapturedTreeValidator()
) : CapturedTreeWireMapper {

    @Suppress("ReturnCount") // Invalid capture data is returned at the point where it is detected.
    override fun mapFullSnapshot(
        snapshot: CapturedFullSnapshot
    ): CaptureWireMappingResult<MobileSegment.MobileRecord.MobileFullSnapshotRecord> {
        val validation = validator.validate(snapshot)
        if (validation is CaptureValidationResult.Invalid) {
            return CaptureWireMappingResult.Invalid(validation.failures)
        }

        val root = snapshot.root
            ?: return invalidMapping(CaptureValidationErrorCode.MISSING_ROOT)
        val wireframes = snapshot.wireframes.map { captured ->
            captured.toWireframeOrNull()
                ?: return invalidMapping(
                    CaptureValidationErrorCode.UNRESOLVED_PIXEL_RESOURCE,
                    captured.identity
                )
        }
        val tree = MobileSegment.CompositionTree(
            root = root.toWireLayer(),
            layers = snapshot.layers.map { it.toWireLayer() }.ifEmpty { null }
        )
        return CaptureWireMappingResult.Success(
            MobileSegment.MobileRecord.MobileFullSnapshotRecord(
                timestamp = snapshot.timestamp,
                data = MobileSegment.Data(
                    wireframes = wireframes,
                    compositionTree = tree
                )
            )
        )
    }

    override fun mapMutation(
        mutation: CapturedMutationSet,
        base: CapturedFullSnapshot
    ): CaptureWireMappingResult<MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord> {
        val validation = validator.validate(mutation, base)
        if (validation is CaptureValidationResult.Invalid) {
            return CaptureWireMappingResult.Invalid(validation.failures)
        }

        val data = MobileSegment.MobileIncrementalData.CompositionTreeMutationData(
            root = mutation.root.mapOrNull { it.toWireLayer() },
            adds = mutation.adds.mapOrNull { layers -> layers.map { it.toWireLayer() } },
            removes = mutation.removes.mapOrNull { identities -> identities.map(CapturedIdentity::wireId) },
            updates = mutation.updates.mapOrNull { updates -> updates.map { it.toWireUpdate() } }
        )
        return CaptureWireMappingResult.Success(
            MobileSegment.MobileRecord.MobileIncrementalSnapshotRecord(
                timestamp = mutation.timestamp,
                data = data
            )
        )
    }
}

private fun CapturedLayer.toWireLayer(): MobileSegment.CompositionLayer =
    MobileSegment.CompositionLayer(
        id = identity.wireId,
        x = bounds.x,
        y = bounds.y,
        width = bounds.width,
        height = bounds.height,
        children = children.map { it.toWireChild() },
        modifiers = modifiers.map { it.toWireModifier() }.ifEmpty { null },
        compositeOperation = compositeOperation?.toWireCompositeOperation()
    )

private fun CapturedLayerUpdate.toWireUpdate(): MobileSegment.CompositionLayerUpdate =
    MobileSegment.CompositionLayerUpdate(
        id = identity.wireId,
        x = x.mapOrNull { it },
        y = y.mapOrNull { it },
        width = width.mapOrNull { it },
        height = height.mapOrNull { it },
        children = children.mapOrNull { children -> children.map { it.toWireChild() } },
        modifiers = modifiers.mapOrNull { modifiers -> modifiers.map { it.toWireModifier() } },
        compositeOperation = compositeOperation.mapOrNull {
            it?.toWireCompositeOperation() ?: MobileSegment.CompositeOperation.SOURCEOVER
        }
    )

private fun CapturedWireframe.toWireframeOrNull(): MobileSegment.Wireframe? = when (this) {
    is CapturedWireframe.Shape -> toWireframe()
    is CapturedWireframe.Text -> toWireframe()
    is CapturedWireframe.Pixel -> toWireframeOrNull()
    is CapturedWireframe.PrivacyPlaceholder -> toWireframe()
    is CapturedWireframe.WebView -> toWireframe()
}

private fun CapturedWireframe.Shape.toWireframe() = MobileSegment.Wireframe.ShapeWireframe(
    id = identity.wireId,
    x = bounds.x,
    y = bounds.y,
    width = bounds.width,
    height = bounds.height,
    clip = clip?.toWireClip(),
    shapeStyle = style?.toWireShapeStyle(),
    border = border?.toWireBorder(),
    permanentId = permanentId
)

private fun CapturedWireframe.Text.toWireframe() = MobileSegment.Wireframe.TextWireframe(
    id = identity.wireId,
    x = bounds.x,
    y = bounds.y,
    width = bounds.width,
    height = bounds.height,
    clip = clip?.toWireClip(),
    shapeStyle = style?.toWireShapeStyle(),
    border = border?.toWireBorder(),
    text = text,
    textStyle = textStyle.toWireTextStyle(),
    textPosition = textPosition?.toWireTextPosition(),
    permanentId = permanentId
)

private fun CapturedWireframe.Pixel.toWireframeOrNull(): MobileSegment.Wireframe.ImageWireframe? =
    (resource as? PixelResource.Resolved)
        ?.takeIf { it.resourceId.isNotBlank() }
        ?.let { resolvedResource ->
            MobileSegment.Wireframe.ImageWireframe(
                id = identity.wireId,
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                clip = clip?.toWireClip(),
                shapeStyle = style?.toWireShapeStyle(),
                border = border?.toWireBorder(),
                resourceId = resolvedResource.resourceId,
                mimeType = resolvedResource.mimeType,
                isEmpty = isEmpty,
                permanentId = permanentId
            )
        }

private fun CapturedWireframe.PrivacyPlaceholder.toWireframe() =
    MobileSegment.Wireframe.PlaceholderWireframe(
        id = identity.wireId,
        x = bounds.x,
        y = bounds.y,
        width = bounds.width,
        height = bounds.height,
        clip = clip?.toWireClip(),
        label = label,
        permanentId = permanentId
    )

private fun CapturedWireframe.WebView.toWireframe() = MobileSegment.Wireframe.WebviewWireframe(
    id = identity.wireId,
    x = bounds.x,
    y = bounds.y,
    width = bounds.width,
    height = bounds.height,
    clip = clip?.toWireClip(),
    shapeStyle = style?.toWireShapeStyle(),
    border = border?.toWireBorder(),
    slotId = identity.wireId.toString(),
    isVisible = isVisible,
    permanentId = permanentId
)

private fun CapturedClip.toWireClip() = MobileSegment.WireframeClip(top, bottom, left, right)

private fun CapturedShapeStyle.toWireShapeStyle() = MobileSegment.ShapeStyle(
    backgroundColor = backgroundColor,
    backgroundGradient = backgroundGradient?.toWireGradient(),
    opacity = opacity,
    cornerRadius = cornerRadius
)

private fun CapturedBackgroundGradient.toWireGradient() = MobileSegment.BackgroundGradient(
    stops = stops.map { MobileSegment.ShapeGradientStop(it.color, it.offset) },
    startPoint = MobileSegment.StartPoint(startPoint.x, startPoint.y),
    endPoint = MobileSegment.StartPoint(endPoint.x, endPoint.y)
)

private fun CapturedShapeBorder.toWireBorder() = MobileSegment.ShapeBorder(color, width)

private fun CapturedTextStyle.toWireTextStyle() = MobileSegment.TextStyle(
    family = family,
    size = size,
    color = color,
    truncationMode = truncationMode?.toWireTruncationMode()
)

private fun CapturedTruncationMode.toWireTruncationMode() = when (this) {
    CapturedTruncationMode.CLIP -> MobileSegment.TruncationMode.CLIP
    CapturedTruncationMode.HEAD -> MobileSegment.TruncationMode.HEAD
    CapturedTruncationMode.TAIL -> MobileSegment.TruncationMode.TAIL
    CapturedTruncationMode.MIDDLE -> MobileSegment.TruncationMode.MIDDLE
}

private fun CapturedTextPosition.toWireTextPosition() = MobileSegment.TextPosition(
    padding = padding?.let { MobileSegment.Padding(it.top, it.bottom, it.left, it.right) },
    alignment = alignment?.let {
        MobileSegment.Alignment(
            horizontal = it.horizontal?.toWireHorizontal(),
            vertical = it.vertical?.toWireVertical()
        )
    }
)

private fun CapturedHorizontalAlignment.toWireHorizontal() = when (this) {
    CapturedHorizontalAlignment.LEFT -> MobileSegment.Horizontal.LEFT
    CapturedHorizontalAlignment.CENTER -> MobileSegment.Horizontal.CENTER
    CapturedHorizontalAlignment.RIGHT -> MobileSegment.Horizontal.RIGHT
}

private fun CapturedVerticalAlignment.toWireVertical() = when (this) {
    CapturedVerticalAlignment.TOP -> MobileSegment.Vertical.TOP
    CapturedVerticalAlignment.CENTER -> MobileSegment.Vertical.CENTER
    CapturedVerticalAlignment.BOTTOM -> MobileSegment.Vertical.BOTTOM
}

private fun CapturedChild.toWireChild(): MobileSegment.CompositionLayerChild = when (this) {
    is CapturedChild.Layer -> MobileSegment.CompositionLayerChild(
        type = MobileSegment.Type.LAYER,
        id = identity.wireId
    )

    is CapturedChild.Wireframe -> MobileSegment.CompositionLayerChild(
        type = MobileSegment.Type.WIREFRAME,
        id = identity.wireId
    )
}

private fun CapturedModifier.toWireModifier(): MobileSegment.CompositionLayerModifier = when (this) {
    is CapturedModifier.BrightnessBias ->
        MobileSegment.CompositionLayerModifier.CompositionLayerBrightnessBiasModifier(value)

    is CapturedModifier.Clip ->
        MobileSegment.CompositionLayerModifier.CompositionLayerClipModifier(
            path = path,
            fillRule = fillRule?.toWireFillRule()
        )

    is CapturedModifier.ColorMatrix ->
        MobileSegment.CompositionLayerModifier.CompositionLayerColorMatrixModifier(values)

    is CapturedModifier.GaussianBlur ->
        MobileSegment.CompositionLayerModifier.CompositionLayerGaussianBlurModifier(radius)

    is CapturedModifier.MaskImage ->
        MobileSegment.CompositionLayerModifier.CompositionLayerMaskImageModifier(resourceId)

    is CapturedModifier.Opacity ->
        MobileSegment.CompositionLayerModifier.CompositionLayerOpacityModifier(value)

    is CapturedModifier.Saturate ->
        MobileSegment.CompositionLayerModifier.CompositionLayerSaturateModifier(value)

    is CapturedModifier.Shadow ->
        MobileSegment.CompositionLayerModifier.CompositionLayerShadowModifier(
            color = color,
            offsetX = offsetX,
            offsetY = offsetY,
            radius = radius,
            path = path
        )
}

private fun CapturedFillRule.toWireFillRule(): MobileSegment.FillRule = when (this) {
    CapturedFillRule.EVEN_ODD -> MobileSegment.FillRule.EVENODD
    CapturedFillRule.NON_ZERO -> MobileSegment.FillRule.NONZERO
}

private fun CapturedCompositeOperation.toWireCompositeOperation(): MobileSegment.CompositeOperation =
    when (this) {
        CapturedCompositeOperation.DESTINATION_IN -> MobileSegment.CompositeOperation.DESTINATIONIN
        CapturedCompositeOperation.DESTINATION_OUT -> MobileSegment.CompositeOperation.DESTINATIONOUT
        CapturedCompositeOperation.PLUS_DARKER -> MobileSegment.CompositeOperation.PLUSDARKER
        CapturedCompositeOperation.SOURCE_OVER -> MobileSegment.CompositeOperation.SOURCEOVER
    }

private fun <T, R> CapturedChange<T>.mapOrNull(transform: (T) -> R): R? =
    when (this) {
        is CapturedChange.Set -> transform(value)
        CapturedChange.Unchanged -> null
    }

// The list contains exactly the single failure constructed here.
@Suppress("UnsafeThirdPartyFunctionCall")
private fun invalidMapping(
    code: CaptureValidationErrorCode,
    identity: CapturedIdentity? = null
): CaptureWireMappingResult.Invalid = CaptureWireMappingResult.Invalid(
    listOf(CaptureValidationFailure(code, identity))
)
