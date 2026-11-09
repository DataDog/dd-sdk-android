/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.view.View
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
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
@Suppress("PackageNameVisibility")
interface CompositionHostDecomposer {

    /** True if [view] is a host this decomposer knows how to decompose. */
    @InternalApi
    fun canDecompose(view: View): Boolean

    /**
     * Decomposes [view]'s content per [request]. Returns null when the host cannot be decomposed.
     * The SDK caller contains thrown failures, reports them through its SDK-bound logger, and
     * falls back to ordinary native-View capture. An implementation may disable itself after an
     * incompatible-runtime failure; it must let that first failure reach the caller for reporting.
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
 * @param hostBounds Copied host bounds in screen dp. The native snapshot builder always clips
 * composition output to this rectangle after subtree effects, even when the host does not clip
 * native children.
 * Null means that guarantee is unavailable and the decomposer must not cull against host bounds.
 * @param shouldContinue Cooperative deadline/cancellation check. Stop between nodes and return null
 * when it returns false; partial output must not be returned or treated as a compatibility failure.
 * @param nativeViewHandoff Copies an embedded native View for construction on the processing
 * executor. Returns true when the copy succeeds; the decomposer then references `childIdentity`
 * as a child layer in its result. Returns false when the View is filtered out or capture expires.
 * Native subtree construction is deferred. Copies are discarded if the
 * enclosing decomposition returns null, so ordinary native traversal can still provide fallback.
 */
@InternalApi
class CompositionHostDecomposeRequest(
    val identityFactory: CompositionIdentityFactory,
    val hostIdentity: CapturedIdentity,
    val screenDensity: Float,
    val hostBounds: CapturedBounds?,
    val shouldContinue: () -> Boolean,
    val nativeViewHandoff: (view: View, childIdentity: CapturedIdentity) -> Boolean
)

/**
 * Output of [CompositionHostDecomposer.decompose] - spliced by the caller into its own flat
 * layers/wireframes lists and the host view's own [CapturedLayer.children].
 *
 * @param rootChildren Direct children of the host view's own layer.
 * @param nodes Every further-nested [CapturedLayer] (kind `COMPOSE_NODE`) the decomposer built,
 * referenced from [rootChildren] or from each other's `children` - never embedded inline. Includes
 * only Compose-owned layers; native handoff layers are constructed separately by the caller.
 * @param wireframes Every Compose-owned leaf [CapturedWireframe] the decomposer produced,
 * referenced from [rootChildren]/[nodes] by identity. Native handoff output is added by the caller.
 */
@InternalApi
class CompositionHostDecomposeResult(
    val rootChildren: List<CapturedChild>,
    val nodes: List<CapturedLayer>,
    val wireframes: List<CapturedWireframe>
)
