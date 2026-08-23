/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.composition

import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.CompositionIdentityFactory
import com.datadog.android.lint.InternalApi

/**
 * SDK-internal extension point: decomposes a Compose host view (`ComposeView`/`AndroidComposeView`)
 * into a [CapturedLayer]/[CapturedWireframe] subtree for the experimental composition-tree pipeline
 * (`com.datadog.android.sessionreplay.internal.composition.AndroidWindowTraversal`), instead of the
 * host falling through to the generic native-View fallback mapper.
 *
 * Deliberately framework-agnostic despite Compose being its only current consumer - it doesn't
 * reference any Compose type. Not meant for direct third-party implementation - the only real
 * implementation lives in the optional `dd-sdk-android-session-replay-compose` artifact, wired in
 * via `com.datadog.android.sessionreplay.compose.ComposeExtensionSupport`. This has to be a public
 * type only because Kotlin's `internal` visibility doesn't cross Gradle module boundaries, and this
 * module deliberately has no compile-time dependency on Jetpack Compose. Every member is marked
 * [InternalApi] rather than the interface itself, since that annotation's own contract warns
 * against applying it to interfaces (implementers would be flagged as internal as a whole).
 */
interface CompositionHostDecomposer {

    /** True if [view] is a host this decomposer knows how to decompose. */
    @InternalApi
    fun canDecompose(view: View): Boolean

    /**
     * Decomposes [view]'s content per [request]. Returns null if decomposition fails for any
     * reason (e.g. the expected entry point on [view] can't be reached, or the running Compose
     * runtime is incompatible) - the caller falls back to its ordinary native-View mapping of
     * [view], exactly as if this decomposer didn't exist.
     */
    @InternalApi
    fun decompose(view: View, request: CompositionHostDecomposeRequest): CompositionHostDecomposeResult?
}

/**
 * Inputs [CompositionHostDecomposer.decompose] needs from the calling native traversal.
 *
 * @param identityFactory Mints every identity the decomposer's output must use - narrowed to only
 * the identity kinds a Compose decomposer may legitimately produce.
 * @param hostIdentity The host view's own identity (already minted via
 * [CompositionIdentityFactory.composeHost] by the caller), the owner every `composeNode`/wireframe
 * identity in this decomposition must be minted against.
 * @param screenDensity The screen density, for pixel-to-dp conversion.
 * @param nativeViewHandoff Bound back into the caller's own native-View traversal - used when the
 * decomposer encounters a real interop `View` embedded inside the host (e.g. Compose's
 * `AndroidView { }`), so that region is mapped through the ordinary View-based path (becoming
 * normal [CapturedLayerKind.NATIVE_VIEW] layers/wireframes) instead of being left undescribed.
 * [childIdentity] is an identity already minted for this interop view via
 * [CompositionIdentityFactory.composeNode] - the caller mints it before invoking the handoff so a
 * consistent identity is used whether or not the handoff succeeds. Returns null if the interop view
 * itself is filtered out (e.g. not visible).
 * @param shouldContinue Cheap cooperative checkpoint the decomposer should poll periodically during
 * its own walk - the caller's generation deadline isn't otherwise visible across this module
 * boundary. Returning false means the deadline has passed; the decomposer must stop and report
 * failure (see [CompositionHostDecomposer.decompose]) rather than return a partial result. Defaults
 * to always-continue for callers that don't need bounded main-thread work.
 */
@InternalApi
class CompositionHostDecomposeRequest(
    val identityFactory: CompositionIdentityFactory,
    val hostIdentity: CapturedIdentity,
    val screenDensity: Float,
    val nativeViewHandoff: (view: View, childIdentity: CapturedIdentity) -> CompositionNativeSubtree?,
    val shouldContinue: () -> Boolean = { true }
)

/**
 * One embedded native `View`'s subtree, mapped by the caller's own native-View traversal in
 * response to [CompositionHostDecomposeRequest.nativeViewHandoff].
 *
 * @param rootLayer The interop view's own layer - already present in [layers] as well, exposed
 * separately so the decomposer can reference it as a child in its own output without searching.
 * @param layers Every layer this subtree contains, [rootLayer] included.
 * @param wireframes Every wireframe this subtree contains.
 */
@InternalApi
class CompositionNativeSubtree(
    val rootLayer: CapturedLayer,
    val layers: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)

/**
 * Output of [CompositionHostDecomposer.decompose] - spliced by the caller into its own flat
 * layers/wireframes lists and the host view's own [CapturedLayer.children].
 *
 * @param rootChildren Direct children of the host view's own layer.
 * @param nodes Every further-nested [CapturedLayer] (kind `COMPOSE_NODE`) the decomposer built,
 * referenced from [rootChildren] or from each other's `children` - never embedded inline. Includes
 * every [CompositionNativeSubtree.layers] entry the decomposer chose to splice in.
 * @param wireframes Every leaf [CapturedWireframe] the decomposer produced (text, shape, or
 * an embedded native subtree's own wireframes), referenced from [rootChildren]/[nodes] by identity.
 */
@InternalApi
class CompositionHostDecomposeResult(
    val rootChildren: List<CapturedChild>,
    val nodes: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)
