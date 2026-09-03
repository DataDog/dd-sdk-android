/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sessionreplay.composition

/**
 * The platform-neutral composition-tree model shared between the Session Replay core module
 * (`dd-sdk-android-session-replay`, which owns identity minting, validation, diffing, and wire
 * mapping) and the optional `dd-sdk-android-session-replay-compose` artifact, which builds
 * [CapturedLayer]/[CapturedWireframe] subtrees for Compose content via
 * `com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer`. Lives here,
 * rather than in the core module itself, because Kotlin's `internal` visibility doesn't cross
 * Gradle module boundaries and both modules need to construct these directly - not a stable public
 * API, and not meant for direct third-party use.
 *
 * @property x left edge, in density-independent pixels, relative to the screen.
 * @property y top edge, in density-independent pixels, relative to the screen.
 * @property width in density-independent pixels.
 * @property height in density-independent pixels.
 */
data class CapturedBounds(
    val x: Long,
    val y: Long,
    val width: Long,
    val height: Long
)

/** The role a [CapturedLayer] plays in the tree. See [CapturedBounds]. */
enum class CapturedLayerKind {
    /** The single synthetic root grouping every captured window. */
    SYNTHETIC_SCREEN_ROOT,

    /** One app-owned window's own root layer. */
    WINDOW_ROOT,

    /** An ordinary Android `View`. */
    NATIVE_VIEW,

    /** A Compose host (`ComposeView`/`AndroidComposeView`) embedded in the native View tree. */
    COMPOSE_HOST,

    /** One node inside a Compose host's own semantics/layout tree. */
    COMPOSE_NODE,

    /** A grouping layer with no View/Compose node of its own (e.g. an opacity wrapper). */
    COMPOSITION_LAYER
}

/**
 * A non-drawing grouping node in the composition tree - its own visual content, if any, is
 * described separately by [CapturedWireframe]s referenced from [children]. See [CapturedBounds].
 *
 * @property identity this layer's stable identity.
 * @property kind what this layer represents.
 * @property bounds absolute screen-space bounds.
 * @property children ordered child layers/wireframes, in paint order.
 * @property modifiers rendering effects applied to this layer's whole subtree, in apply order.
 * @property compositeOperation how this layer's output blends with what's already been painted.
 */
data class CapturedLayer(
    val identity: CapturedIdentity,
    val kind: CapturedLayerKind,
    val bounds: CapturedBounds,
    val children: List<CapturedChild>,
    val modifiers: List<CapturedModifier> = emptyList(),
    val compositeOperation: CapturedCompositeOperation? = null
)

/** A reference to one child of a [CapturedLayer], resolved by identity. See [CapturedBounds]. */
sealed interface CapturedChild {
    /** The identity of the referenced layer or wireframe. */
    val identity: CapturedIdentity

    /** References a nested [CapturedLayer]. */
    data class Layer(
        override val identity: CapturedIdentity
    ) : CapturedChild

    /** References a leaf [CapturedWireframe]. */
    data class Wireframe(
        override val identity: CapturedIdentity
    ) : CapturedChild
}

/** A leaf, drawable unit of the composition tree. See [CapturedBounds]. */
sealed interface CapturedWireframe {
    /** This wireframe's stable identity. */
    val identity: CapturedIdentity

    /** Absolute screen-space bounds. */
    val bounds: CapturedBounds

    /** Inset applied per edge where an ancestor clips this wireframe's content, if any. */
    val clip: CapturedClip?

    /** A cross-generation identifier used to detect this same logical element across snapshots. */
    val permanentId: String?

    /**
     * A plain shape - background color/gradient and/or border, with no text content.
     *
     * @property identity this wireframe's stable identity.
     * @property bounds absolute screen-space bounds.
     * @property clip inset applied per edge where an ancestor clips this wireframe, if any.
     * @property style background fill, if any.
     * @property border stroke, if any.
     * @property permanentId a cross-generation identifier for this same logical element, if known.
     */
    data class Shape(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    /**
     * Real, unmasked text content - text/input privacy masking is applied by a later stage, not
     * by capture.
     *
     * @property identity this wireframe's stable identity.
     * @property bounds absolute screen-space bounds.
     * @property clip inset applied per edge where an ancestor clips this wireframe, if any.
     * @property style background fill behind the text, if any.
     * @property border stroke, if any.
     * @property text the literal text content.
     * @property textStyle font/color styling.
     * @property textPosition padding/alignment of the text within [bounds], if not default.
     * @property permanentId a cross-generation identifier for this same logical element, if known.
     */
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
    /**
     * An opaque placeholder rect for a `WebView` - its content is recorded separately via an
     * out-of-band JS bridge correlated by `identity.wireId == slotId`.
     *
     * @property identity this wireframe's stable identity.
     * @property bounds absolute screen-space bounds.
     * @property clip inset applied per edge where an ancestor clips this wireframe, if any.
     * @property style background fill shown before the web content loads, if any.
     * @property border stroke, if any.
     * @property isVisible whether the WebView is currently visible.
     * @property permanentId a cross-generation identifier for this same logical element, if known.
     */
    data class WebView(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        val isVisible: Boolean? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    /**
     * A rasterized image resource. Only ever constructed by the native View traversal in
     * `dd-sdk-android-session-replay` (never by the Compose decomposer - see [CapturedBounds]). A
     * snapshot containing one whose [resource] is [PixelResource.Unresolved] fails validation
     * entirely, not just for this wireframe, so any future producer of this type must resolve
     * [resource] (or drop the wireframe) before the snapshot is emitted.
     *
     * @property identity this wireframe's stable identity.
     * @property bounds absolute screen-space bounds.
     * @property clip inset applied per edge where an ancestor clips this wireframe, if any.
     * @property style background fill shown behind transparent pixels, if any.
     * @property border stroke, if any.
     * @property resource the backing image resource.
     * @property isEmpty whether the captured image is fully transparent/blank.
     * @property permanentId a cross-generation identifier for this same logical element, if known.
     */
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

    /**
     * Stands in for content that was hidden or otherwise excluded from capture.
     *
     * @property identity this wireframe's stable identity.
     * @property bounds absolute screen-space bounds.
     * @property clip inset applied per edge where an ancestor clips this wireframe, if any.
     * @property label short text shown in place of the excluded content, if any.
     * @property permanentId a cross-generation identifier for this same logical element, if known.
     */
    data class PrivacyPlaceholder(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val label: String? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe

    /**
     * An out-of-band content placeholder correlated by [slotId] - content for this slot is
     * recorded independently by the embedding SDK and stitched in by the player using [slotId],
     * the same slot-correlation idea as [WebView] but generalized to any embeddable content type.
     *
     * @property identity this wireframe's stable identity.
     * @property bounds absolute screen-space bounds.
     * @property clip inset applied per edge where an ancestor clips this wireframe, if any.
     * @property style background fill shown behind/around the embedded content, if any.
     * @property border stroke, if any.
     * @property slotId the identifier of the slot this wireframe hosts, supplied by the embedding SDK.
     * @property isVisible whether this slot's placeholder is currently visible.
     * @property permanentId a cross-generation identifier for this same logical element, if known.
     */
    data class EmbeddedContent(
        override val identity: CapturedIdentity,
        override val bounds: CapturedBounds,
        override val clip: CapturedClip? = null,
        val style: CapturedShapeStyle? = null,
        val border: CapturedShapeBorder? = null,
        val slotId: String,
        val isVisible: Boolean? = null,
        override val permanentId: String? = null
    ) : CapturedWireframe
}

/** The backing resource of a [CapturedWireframe.Pixel]. */
sealed interface PixelResource {
    /**
     * A resource that has already been hashed, encoded, and registered for upload.
     *
     * @property resourceId the registered resource's stable identifier.
     * @property mimeType the encoded image's MIME type, if known.
     */
    data class Resolved(
        val resourceId: String,
        val mimeType: String? = null
    ) : PixelResource

    /** Not yet resolved - must never reach an emitted snapshot. See [CapturedWireframe.Pixel]. */
    object Unresolved : PixelResource
}

/**
 * How much of a [CapturedWireframe] is clipped away by an ancestor, per edge, in
 * density-independent pixels. See [CapturedBounds].
 *
 * @property top amount clipped from the top edge, if any.
 * @property bottom amount clipped from the bottom edge, if any.
 * @property left amount clipped from the left edge, if any.
 * @property right amount clipped from the right edge, if any.
 */
data class CapturedClip(
    val top: Long? = null,
    val bottom: Long? = null,
    val left: Long? = null,
    val right: Long? = null
)

/**
 * A shape's fill and rounding. See [CapturedBounds].
 *
 * @property backgroundColor solid fill color, as `#RRGGBBAA`, if any.
 * @property backgroundGradient gradient fill, if any (mutually exclusive with [backgroundColor]).
 * @property opacity overall opacity, `0.0`-`1.0`.
 * @property cornerRadius corner rounding, in density-independent pixels.
 */
data class CapturedShapeStyle(
    val backgroundColor: String? = null,
    val backgroundGradient: CapturedBackgroundGradient? = null,
    val opacity: Number? = null,
    val cornerRadius: Number? = null
)

/**
 * A linear gradient background fill. See [CapturedBounds].
 *
 * @property stops ordered color stops.
 * @property startPoint gradient start, in unit coordinates relative to the fill area.
 * @property endPoint gradient end, in unit coordinates relative to the fill area.
 */
data class CapturedBackgroundGradient(
    val stops: List<CapturedGradientStop>,
    val startPoint: CapturedPoint,
    val endPoint: CapturedPoint
)

/**
 * One color stop of a [CapturedBackgroundGradient].
 *
 * @property color as `#RRGGBBAA`.
 * @property offset position along the gradient, `0.0`-`1.0`.
 */
data class CapturedGradientStop(
    val color: String,
    val offset: Double
)

/**
 * A point in unit coordinates. See [CapturedBounds].
 *
 * @property x horizontal position.
 * @property y vertical position.
 */
data class CapturedPoint(
    val x: Double,
    val y: Double
)

/**
 * A uniform stroke around a shape. See [CapturedBounds].
 *
 * @property color as `#RRGGBBAA`.
 * @property width in density-independent pixels.
 */
data class CapturedShapeBorder(
    val color: String,
    val width: Long
)

/**
 * Text rendering style. See [CapturedBounds].
 *
 * @property family font family name.
 * @property size in density-independent pixels.
 * @property color as `#RRGGBBAA`.
 * @property truncationMode how overflowing text is truncated, if constrained.
 */
data class CapturedTextStyle(
    val family: String,
    val size: Long,
    val color: String,
    val truncationMode: CapturedTruncationMode? = null
)

/** How overflowing text is truncated. See [CapturedBounds]. */
enum class CapturedTruncationMode {
    /** Hard-clipped with no ellipsis. */
    CLIP,

    /** Ellipsis at the start. */
    HEAD,

    /** Ellipsis at the end. */
    TAIL,

    /** Ellipsis in the middle. */
    MIDDLE
}

/**
 * A text wireframe's padding/alignment within its bounds. See [CapturedBounds].
 *
 * @property padding inset applied per edge, if not default.
 * @property alignment text alignment, if not default.
 */
data class CapturedTextPosition(
    val padding: CapturedPadding? = null,
    val alignment: CapturedAlignment? = null
)

/**
 * An inset applied per edge, in density-independent pixels. See [CapturedBounds].
 *
 * @property top inset from the top edge, if any.
 * @property bottom inset from the bottom edge, if any.
 * @property left inset from the left edge, if any.
 * @property right inset from the right edge, if any.
 */
data class CapturedPadding(
    val top: Long? = null,
    val bottom: Long? = null,
    val left: Long? = null,
    val right: Long? = null
)

/**
 * Text alignment. See [CapturedBounds].
 *
 * @property horizontal horizontal alignment, if not default.
 * @property vertical vertical alignment, if not default.
 */
data class CapturedAlignment(
    val horizontal: CapturedHorizontalAlignment? = null,
    val vertical: CapturedVerticalAlignment? = null
)

/** Horizontal text alignment. See [CapturedBounds]. */
enum class CapturedHorizontalAlignment {
    /** Aligned to the left edge. */
    LEFT,

    /** Centered. */
    CENTER,

    /** Aligned to the right edge. */
    RIGHT
}

/** Vertical text alignment. See [CapturedBounds]. */
enum class CapturedVerticalAlignment {
    /** Aligned to the top edge. */
    TOP,

    /** Centered. */
    CENTER,

    /** Aligned to the bottom edge. */
    BOTTOM
}

/** A rendering effect applied to a whole [CapturedLayer] subtree. See [CapturedBounds]. */
sealed interface CapturedModifier {
    /**
     * Clips the layer to an arbitrary shape.
     *
     * @property path an SVG-style path describing the clip shape.
     * @property fillRule how self-intersecting regions of [path] are filled, if not the default.
     */
    data class Clip(
        val path: String,
        val fillRule: CapturedFillRule? = null
    ) : CapturedModifier

    /** @property value overall opacity, `0.0`-`1.0`. */
    data class Opacity(val value: Double) : CapturedModifier

    /** @property values a 4x5 RGBA color transform matrix, row-major. */
    data class ColorMatrix(val values: List<Double>) : CapturedModifier

    /** @property radius blur radius, in density-independent pixels. */
    data class GaussianBlur(val radius: Double) : CapturedModifier

    /**
     * A drop shadow.
     *
     * @property color as `#RRGGBBAA`.
     * @property offsetX horizontal offset, in density-independent pixels.
     * @property offsetY vertical offset, in density-independent pixels.
     * @property radius blur radius, in density-independent pixels.
     * @property path an SVG-style path describing the shadow's shape, if not the layer's own bounds.
     */
    data class Shadow(
        val color: String,
        val offsetX: Double,
        val offsetY: Double,
        val radius: Double,
        val path: String? = null
    ) : CapturedModifier

    /** @property value brightness adjustment, positive to lighten and negative to darken, `-1.0`-`1.0`. */
    data class BrightnessBias(val value: Double) : CapturedModifier

    /** @property value saturation multiplier, `0.0` for grayscale, `1.0` for unchanged. */
    data class Saturate(val value: Double) : CapturedModifier

    /** @property resourceId masks the layer using this image resource's alpha channel. */
    data class MaskImage(val resourceId: String) : CapturedModifier
}

/** How self-intersecting regions of a [CapturedModifier.Clip] path are filled. */
enum class CapturedFillRule {
    /** Non-zero winding rule. */
    NON_ZERO,

    /** Even-odd winding rule. */
    EVEN_ODD
}

/** How a [CapturedLayer]'s output blends with what's already been painted. */
enum class CapturedCompositeOperation {
    /** Normal alpha blending. */
    SOURCE_OVER,

    /** Keeps only the overlapping region of the destination. */
    DESTINATION_IN,

    /** Keeps only the non-overlapping region of the destination. */
    DESTINATION_OUT,

    /** Adds color channels together, clamped. */
    PLUS_DARKER
}
