/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("TooManyFunctions") // Value-only conversion at the existing extension boundary.

package com.datadog.android.sessionreplay.internal.composition.mapper

import com.datadog.android.internal.sessionreplay.composition.CapturedAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedBackgroundGradient
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedGradientStop
import com.datadog.android.internal.sessionreplay.composition.CapturedHorizontalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedPadding
import com.datadog.android.internal.sessionreplay.composition.CapturedPoint
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeBorder
import com.datadog.android.internal.sessionreplay.composition.CapturedShapeStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTextPosition
import com.datadog.android.internal.sessionreplay.composition.CapturedTextStyle
import com.datadog.android.internal.sessionreplay.composition.CapturedTruncationMode
import com.datadog.android.internal.sessionreplay.composition.CapturedVerticalAlignment
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.sessionreplay.model.MobileSegment

/** Preserve mapper-local ids while keeping them scoped to their owning view and RUM view. */
internal fun MobileSegment.Wireframe.toCapturedWireframe(context: CapturedMappingContext): CapturedWireframe {
    val id = when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> id
        is MobileSegment.Wireframe.TextWireframe -> id
        is MobileSegment.Wireframe.ImageWireframe -> id
        is MobileSegment.Wireframe.PlaceholderWireframe -> id
        is MobileSegment.Wireframe.WebviewWireframe -> id
        is MobileSegment.Wireframe.EmbeddedContentWireframe -> id
    }
    val owner = context.identityFactory.layer(context.ownerIdentity, "mapped-$id")
    val factory = context.identityFactory
    return when (this) {
        is MobileSegment.Wireframe.ShapeWireframe -> CapturedWireframe.Shape(
            factory.shapeWireframe(owner),
            CapturedBounds(x, y, width, height),
            clip?.captured(),
            shapeStyle?.captured(),
            border?.captured(),
            permanentId
        )
        is MobileSegment.Wireframe.TextWireframe -> capturedText(context, owner)
        is MobileSegment.Wireframe.ImageWireframe -> capturedImage(context, owner)
        is MobileSegment.Wireframe.PlaceholderWireframe -> CapturedWireframe.PrivacyPlaceholder(
            factory.placeholderWireframe(owner),
            CapturedBounds(x, y, width, height),
            clip?.captured(),
            label,
            permanentId
        )
        is MobileSegment.Wireframe.WebviewWireframe -> CapturedWireframe.WebView(
            factory.webViewWireframe(owner, id),
            CapturedBounds(x, y, width, height),
            clip?.captured(),
            shapeStyle?.captured(),
            border?.captured(),
            isVisible,
            permanentId
        )
        is MobileSegment.Wireframe.EmbeddedContentWireframe -> CapturedWireframe.EmbeddedContent(
            factory.embeddedContentWireframe(owner),
            CapturedBounds(x, y, width, height),
            clip?.captured(),
            shapeStyle?.captured(),
            border?.captured(),
            slotId,
            isVisible,
            permanentId
        )
    }
}

private fun MobileSegment.WireframeClip.captured() = CapturedClip(top, bottom, left, right)
private fun MobileSegment.ShapeBorder.captured() = CapturedShapeBorder(color, width)
private fun MobileSegment.ShapeStyle.captured() = CapturedShapeStyle(
    backgroundColor,
    backgroundGradient?.let { gradient ->
        CapturedBackgroundGradient(
            gradient.stops.map { CapturedGradientStop(it.color, it.position.toDouble()) },
            CapturedPoint(gradient.startPoint.x.toDouble(), gradient.startPoint.y.toDouble()),
            CapturedPoint(gradient.endPoint.x.toDouble(), gradient.endPoint.y.toDouble())
        )
    },
    opacity,
    cornerRadius
)
private fun MobileSegment.TextPosition.captured() = CapturedTextPosition(
    padding?.let { CapturedPadding(it.top, it.bottom, it.left, it.right) },
    alignment?.let {
        CapturedAlignment(
            it.horizontal?.let { value -> CapturedHorizontalAlignment.valueOf(value.name) },
            it.vertical?.let { value -> CapturedVerticalAlignment.valueOf(value.name) }
        )
    }
)

private fun MobileSegment.Wireframe.ImageWireframe.capturedImage(
    context: CapturedMappingContext,
    owner: CapturedIdentity
): CapturedWireframe {
    val resource = resourceId?.takeIf { it.isNotBlank() }
    return if (resource == null) {
        CapturedWireframe.PrivacyPlaceholder(
            context.identityFactory.placeholderWireframe(owner),
            CapturedBounds(x, y, width, height),
            clip?.captured(),
            "Image",
            permanentId
        )
    } else {
        CapturedWireframe.Pixel(
            context.identityFactory.imageWireframe(owner),
            CapturedBounds(x, y, width, height),
            clip?.captured(),
            shapeStyle?.captured(),
            border?.captured(),
            PixelResource.Resolved(resource, mimeType),
            isEmpty,
            permanentId
        )
    }
}

private fun MobileSegment.Wireframe.TextWireframe.capturedText(
    context: CapturedMappingContext,
    owner: CapturedIdentity
): CapturedWireframe.Text = CapturedWireframe.Text(
    context.identityFactory.textWireframe(owner), CapturedBounds(x, y, width, height), clip?.captured(),
    shapeStyle?.captured(), border?.captured(), text,
    CapturedTextStyle(
        textStyle.family,
        textStyle.size,
        textStyle.color,
        textStyle.truncationMode?.let { CapturedTruncationMode.valueOf(it.name) }
    ),
    textPosition?.captured(), permanentId
)
