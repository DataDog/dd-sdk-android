/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder

import android.graphics.Rect
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback

/**
 * SDK-internal extension point: decomposes a container host view (currently only Compose's
 * `ComposeView`/`AndroidComposeView`, see `CompositionTreeBuilder.isComposeHostView`) into
 * individual finer-grained regions for the experimental pixel-capture composition-tree pipeline
 * (`com.datadog.android.sessionreplay.internal.recorder.CompositionTreeBuilder`), instead of
 * capturing the whole host view as one flattened bitmap.
 *
 * Deliberately framework-agnostic despite Compose being its only current consumer — it doesn't
 * reference any Compose type. Not meant for direct third-party implementation — the only real
 * implementation lives in the optional `dd-sdk-android-session-replay-compose` artifact
 * (`DefaultComposeCompositionWalker`), wired in via
 * `com.datadog.android.sessionreplay.compose.ComposeExtensionSupport`. This has to be a public
 * type only because Kotlin's `internal` visibility doesn't cross Gradle module boundaries, and
 * this module deliberately has no compile-time dependency on Jetpack Compose.
 */
interface HostViewDecomposer {

    /** True if [view] is a host this decomposer knows how to decompose. */
    fun canDecompose(view: View): Boolean

    /**
     * Decomposes [view]'s content per [request]. Returns null if decomposition fails for any
     * reason (e.g. the expected entry point on [view] can't be reached) — the caller falls back
     * to capturing [view] as one whole region, exactly as it did before this extension point
     * existed.
     */
    fun decompose(view: View, request: HostViewDecomposeRequest): HostViewDecomposeResult?
}

/**
 * Inputs [HostViewDecomposer.decompose] needs from the calling composition-tree traversal.
 *
 * @param nativeViewHandoff Bound back into the traversal's own native-View recursion — used when
 * the decomposer encounters a real interop `View` embedded inside the host (e.g. Compose's
 * `AndroidView { }`), so that region is mapped through the ordinary View-based path instead of
 * being pixel-captured as opaque content.
 * @param hostVisibleRectPx The decomposed host view's own currently-visible rect (screen pixels,
 * from [View.getGlobalVisibleRect]) — null if it couldn't be resolved (e.g. the view isn't
 * attached). A scrolling ancestor outside the host — a native `NestedScrollView`/`ScrollView`
 * wrapping a `ComposeView`, say — clips the host itself at the View layer, which the decomposer's
 * own traversal of the host's internal layout tree has no way to observe on its own: every
 * descendant node's position comes from Compose's layout coordinates, which know nothing about
 * View-level ancestor clipping. Comparing each produced wireframe's own screen bounds against this
 * rect (the same [MobileSegment.WireframeClip] mechanism
 * [com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCaptureFallbackMapper] already
 * uses) is what lets scrolled-past content stay correctly cropped instead of visually bleeding
 * into whatever is drawn below the scrolling container.
 */
class HostViewDecomposeRequest(
    val mappingContext: MappingContext,
    val asyncJobStatusCallback: AsyncJobStatusCallback,
    val internalLogger: InternalLogger,
    val pixelCaptureCallback: PixelCaptureCallback?,
    val nativeViewHandoff: (View) -> List<MobileSegment.CompositionLayerChild>,
    val hostVisibleRectPx: Rect? = null
)

/**
 * Output of [HostViewDecomposer.decompose] — spliced by the caller into its own flat
 * `layers`/`wireframes` lists and the host view's own [MobileSegment.CompositionLayer.children].
 *
 * @param rootChildren Direct children of the host view's own layer.
 * @param nestedLayers Any further-nested [MobileSegment.CompositionLayer]s the decomposer built,
 * referenced from [rootChildren] or from each other — never embedded inline.
 * @param wireframes Every leaf [MobileSegment.Wireframe] the decomposer produced (text, shape, or
 * pixel-captured), referenced from [rootChildren]/[nestedLayers] by id.
 */
class HostViewDecomposeResult(
    val rootChildren: List<MobileSegment.CompositionLayerChild>,
    val nestedLayers: List<MobileSegment.CompositionLayer>,
    val wireframes: List<MobileSegment.Wireframe>
)
