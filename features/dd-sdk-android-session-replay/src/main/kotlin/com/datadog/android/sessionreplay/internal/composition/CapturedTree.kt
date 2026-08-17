/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

/**
 * Discovers roots and synchronously inspects Android/Compose state on the main thread. Concrete
 * walkers must use [CaptureGenerationContext.shouldContinue] between bounded operations and may
 * return a contract-provided placeholder or cached resource before the deadline. [changeset]
 * identifies what triggered this generation so a walker may skip untouched subtrees; an empty
 * changeset means the trigger carried no such information and everything should be considered
 * changed.
 */
internal fun interface CapturedSnapshotProducer {
    fun capture(context: CaptureGenerationContext, changeset: CaptureChangeset): CapturedFullSnapshot?
}

/**
 * What changed since the previous generation drained this changeset. Implementations merge with
 * [mergedWith] so signals arriving while a generation is active, or while a scheduled capture is
 * denied admission, accumulate instead of being dropped.
 */
internal interface CaptureChangeset {
    fun isEmpty(): Boolean
    fun mergedWith(other: CaptureChangeset): CaptureChangeset

    companion object {
        val EMPTY: CaptureChangeset = EmptyCaptureChangeset
    }
}

private object EmptyCaptureChangeset : CaptureChangeset {
    override fun isEmpty(): Boolean = true
    override fun mergedWith(other: CaptureChangeset): CaptureChangeset = other
}

internal data class CapturedBounds(
    val x: Long,
    val y: Long,
    val width: Long,
    val height: Long
)

internal enum class CapturedLayerKind {
    SYNTHETIC_SCREEN_ROOT,
    WINDOW_ROOT,
    NATIVE_VIEW,
    COMPOSE_HOST,
    COMPOSE_NODE,
    COMPOSITION_LAYER
}

internal data class CapturedLayer(
    val identity: CapturedIdentity,
    val kind: CapturedLayerKind,
    val bounds: CapturedBounds,
    val children: List<CapturedChild>,
    val modifiers: List<CapturedModifier> = emptyList(),
    val compositeOperation: CapturedCompositeOperation? = null
)

internal sealed interface CapturedChild {
    val identity: CapturedIdentity

    data class Layer(
        override val identity: CapturedIdentity
    ) : CapturedChild

    data class Wireframe(
        override val identity: CapturedIdentity
    ) : CapturedChild
}

internal sealed interface CapturedWireframe {
    val identity: CapturedIdentity
    val bounds: CapturedBounds
    val clip: CapturedClip?
    val permanentId: String?

    data class Shape(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    data class Text(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        val text: String,
        val textStyle: CapturedTextStyle,
        val textPosition: CapturedTextPosition? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    // No separate slotId field: the identity produced by CapturedIdentityFactory.webViewWireframe
    // already carries the slot id verbatim as its wire id, so the wire mapper derives slotId from
    // `identity.wireId` directly. A separate field would let the two silently drift apart, breaking
    // the id == slotId JS-bridge correlation contract (see CapturedWireframeKind.WEB_VIEW).
    data class WebView(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        val isVisible: Boolean? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    data class Pixel(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        val resource: PixelResource,
        val isEmpty: Boolean? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    data class PrivacyPlaceholder(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val label: String? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe
}

internal sealed interface PixelResource {
    data class Resolved(
        val resourceId: String,
        val mimeType: String? = null
    ) : PixelResource

    object Unresolved : PixelResource
}

internal data class CapturedClip(
    val top: Long? = null,
    val bottom: Long? = null,
    val left: Long? = null,
    val right: Long? = null
)

internal data class CapturedShapeStyle(
    val backgroundColor: String? = null,
    val backgroundGradient: CapturedBackgroundGradient? = null,
    val opacity: Number? = null,
    val cornerRadius: Number? = null
)

internal data class CapturedBackgroundGradient(
    val stops: List<CapturedGradientStop>,
    val startPoint: CapturedPoint,
    val endPoint: CapturedPoint
)

internal data class CapturedGradientStop(
    val color: String,
    val offset: Double
)

internal data class CapturedPoint(
    val x: Double,
    val y: Double
)

internal data class CapturedShapeBorder(
    val color: String,
    val width: Long
)

internal data class CapturedTextStyle(
    val family: String,
    val size: Long,
    val color: String,
    val truncationMode: CapturedTruncationMode? = null
)

internal enum class CapturedTruncationMode { CLIP, HEAD, TAIL, MIDDLE }

internal data class CapturedTextPosition(
    val padding: CapturedPadding? = null,
    val alignment: CapturedAlignment? = null
)

internal data class CapturedPadding(
    val top: Long? = null,
    val bottom: Long? = null,
    val left: Long? = null,
    val right: Long? = null
)

internal data class CapturedAlignment(
    val horizontal: CapturedHorizontalAlignment? = null,
    val vertical: CapturedVerticalAlignment? = null
)

internal enum class CapturedHorizontalAlignment { LEFT, CENTER, RIGHT }

internal enum class CapturedVerticalAlignment { TOP, CENTER, BOTTOM }

internal sealed interface CapturedModifier {
    data class Clip(
        val path: String,
        val fillRule: CapturedFillRule? = null
    ) : CapturedModifier

    data class Opacity(val value: Double) : CapturedModifier

    data class ColorMatrix(val values: List<Double>) : CapturedModifier

    data class GaussianBlur(val radius: Double) : CapturedModifier

    data class Shadow(
        val color: String,
        val offsetX: Double,
        val offsetY: Double,
        val radius: Double,
        val path: String? = null
    ) : CapturedModifier

    data class BrightnessBias(val value: Double) : CapturedModifier

    data class Saturate(val value: Double) : CapturedModifier

    data class MaskImage(val resourceId: String) : CapturedModifier
}

internal enum class CapturedFillRule {
    NON_ZERO,
    EVEN_ODD
}

internal enum class CapturedCompositeOperation {
    SOURCE_OVER,
    DESTINATION_IN,
    DESTINATION_OUT,
    PLUS_DARKER
}

internal data class CapturedFullSnapshot(
    val timestamp: Long,
    val scope: RumViewIdentityScope,
    val root: CapturedLayer?,
    val layers: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)
