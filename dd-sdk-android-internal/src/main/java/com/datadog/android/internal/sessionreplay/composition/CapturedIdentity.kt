/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.sessionreplay.composition

/**
 * A RUM view's identity scope, used to namespace every [CapturedIdentity] minted while that view
 * is active. See [CapturedBounds] - shared between `dd-sdk-android-session-replay` and its Compose
 * artifact.
 *
 * @property value the RUM view id.
 */
data class RumViewIdentityScope(val value: String)

/** What a [CapturedIdentity] identifies. See [CapturedBounds]. */
enum class CapturedIdentityKind {
    /** The synthetic screen root. */
    SCREEN_ROOT,

    /** An app-owned window. */
    WINDOW,

    /** A native `View`, including a Compose host `View`. */
    VIEW,

    /** A Compose host. */
    COMPOSE_HOST,

    /** A node inside a Compose host's own tree. */
    COMPOSE_NODE,

    /** A grouping [CapturedLayer] with no View/Compose node of its own. */
    LAYER,

    /** A leaf [CapturedWireframe]. */
    WIREFRAME
}

private const val WEB_VIEW_WIREFRAME_NAMESPACE = 0L
private const val SHAPE_WIREFRAME_NAMESPACE = 1L
private const val TEXT_WIREFRAME_NAMESPACE = 2L
private const val IMAGE_WIREFRAME_NAMESPACE = 3L
private const val PLACEHOLDER_WIREFRAME_NAMESPACE = 4L

/**
 * Matches the wireframe identifier namespaces used by the iOS Core Animation pipeline.
 *
 * @property wireIdNamespace this wireframe kind's namespace, shifted into the high bits of a
 * namespaced wireframe's wire id so wireframes of different kinds owned by the same layer never
 * collide.
 */
enum class CapturedWireframeKind(val wireIdNamespace: Long) {
    /**
     * Deliberately namespace `0`, i.e. unshifted: the wire id for a web-view wireframe must equal
     * its `slotId` verbatim (not `slotId` shifted into a namespace), matching the pre-existing
     * `id == slotId` contract the legacy recorder and iOS both rely on for WebView/JS-bridge
     * correlation. `slotId` is caller-supplied and may be any `Int`-range value (including
     * negative), so this is the one identity kind not structurally guaranteed collision-free
     * against the rest of the tree - `dd-sdk-android-session-replay`'s identity factory offsets
     * every non-wireframe layer id so raw layer ids can never land in that same `Int` range; a
     * residual, validation-caught collision remains possible only between two web-view wireframes
     * with a colliding `slotId` in the same view scope.
     */
    WEB_VIEW(WEB_VIEW_WIREFRAME_NAMESPACE),

    /** A [CapturedWireframe.Shape]. */
    SHAPE(SHAPE_WIREFRAME_NAMESPACE),

    /** A [CapturedWireframe.Text]. */
    TEXT(TEXT_WIREFRAME_NAMESPACE),

    /** A [CapturedWireframe.Pixel]. */
    IMAGE(IMAGE_WIREFRAME_NAMESPACE),

    /** A [CapturedWireframe.PrivacyPlaceholder]. */
    PLACEHOLDER(PLACEHOLDER_WIREFRAME_NAMESPACE)
}

/**
 * See [CapturedBounds]. Only ever constructed by `dd-sdk-android-session-replay`'s identity
 * factory - external code (including the Compose decomposer) should only ever hold instances
 * minted by it, never construct one directly, to keep every uniqueness/scope invariant the factory
 * enforces intact.
 *
 * @property scope the RUM view scope this identity belongs to.
 * @property kind what this identity identifies.
 * @property wireframeKind which kind of wireframe this is, if [kind] is [CapturedIdentityKind.WIREFRAME].
 * @property namespace the path segments of every owner above this identity.
 * @property localId this identity's own segment, unique within [namespace].
 * @property wireId the flat numeric id used on the wire and for cross-referencing within a snapshot.
 */
data class CapturedIdentity(
    val scope: RumViewIdentityScope,
    val kind: CapturedIdentityKind,
    val wireframeKind: CapturedWireframeKind?,
    val namespace: List<String>,
    val localId: String,
    val wireId: Long
) {
    /** This identity's full path: [namespace] plus its own kind and [localId]. */
    fun path(): List<String> = namespace + kind.name + localId
}

/**
 * The identity-minting surface the Compose decomposer needs (see
 * `com.datadog.android.sessionreplay.recorder.composition.CompositionHostDecomposer`) - using the
 * same factory instance the native View walker uses, so every identity stays collision-free and
 * correctly scoped without a separate id space. Narrowed to only the identity kinds a Compose
 * decomposer may legitimately mint - notably no `imageWireframe`/`webViewWireframe`, since it must
 * never construct a [CapturedWireframe.Pixel] or [CapturedWireframe.WebView].
 * `dd-sdk-android-session-replay`'s internal `CapturedIdentityFactory` extends this with the rest
 * (`screenRoot`/`window`/`view`/`layer`/`webViewWireframe`/`imageWireframe`).
 */
interface CompositionIdentityFactory {
    /** Mints the identity for a Compose host owned by [window]. */
    fun composeHost(window: CapturedIdentity, hostId: String): CapturedIdentity

    /** Mints the identity for a node owned by the Compose [host]. */
    fun composeNode(host: CapturedIdentity, nodeId: String): CapturedIdentity

    /** Mints the identity for a [CapturedWireframe.Shape] owned by [owner]. */
    fun shapeWireframe(owner: CapturedIdentity): CapturedIdentity

    /** Mints the identity for a [CapturedWireframe.Text] owned by [owner]. */
    fun textWireframe(owner: CapturedIdentity): CapturedIdentity

    /** Mints the identity for a [CapturedWireframe.PrivacyPlaceholder] owned by [owner]. */
    fun placeholderWireframe(owner: CapturedIdentity): CapturedIdentity
}
