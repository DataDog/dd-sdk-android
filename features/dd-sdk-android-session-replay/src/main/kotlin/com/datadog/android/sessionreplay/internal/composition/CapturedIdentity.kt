/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal data class RumViewIdentityScope(val value: String)

internal enum class CapturedIdentityKind {
    SCREEN_ROOT,
    WINDOW,
    VIEW,
    COMPOSE_HOST,
    COMPOSE_NODE,
    LAYER,
    WIREFRAME
}

private const val WEB_VIEW_WIREFRAME_NAMESPACE = 0L
private const val SHAPE_WIREFRAME_NAMESPACE = 1L
private const val TEXT_WIREFRAME_NAMESPACE = 2L
private const val IMAGE_WIREFRAME_NAMESPACE = 3L
private const val PLACEHOLDER_WIREFRAME_NAMESPACE = 4L

/** Matches the wireframe identifier namespaces used by the iOS Core Animation pipeline. */
internal enum class CapturedWireframeKind(internal val wireIdNamespace: Long) {
    /**
     * Deliberately namespace `0`, i.e. unshifted: the wire id for a web-view wireframe must equal
     * its `slotId` verbatim (not `slotId` shifted into a namespace), matching the pre-existing
     * `id == slotId` contract the legacy recorder and iOS both rely on for WebView/JS-bridge
     * correlation (see [CapturedIdentityFactory.webViewWireframe]). `slotId` is caller-supplied and,
     * once wired to the real view-identity resolver, may be any `Int`-range value (including
     * negative), so this is the one identity kind not structurally guaranteed collision-free
     * against the rest of the tree. [DefaultCapturedIdentityFactory] offsets every non-wireframe
     * layer id by [LAYER_ID_OFFSET] specifically so that raw layer ids can never land in that same
     * `Int` range; a residual, validation-caught collision remains possible only between two
     * web-view wireframes with a colliding `slotId` in the same view scope.
     */
    WEB_VIEW(WEB_VIEW_WIREFRAME_NAMESPACE),
    SHAPE(SHAPE_WIREFRAME_NAMESPACE),
    TEXT(TEXT_WIREFRAME_NAMESPACE),
    IMAGE(IMAGE_WIREFRAME_NAMESPACE),
    PLACEHOLDER(PLACEHOLDER_WIREFRAME_NAMESPACE)
}

internal data class CapturedIdentity(
    val scope: RumViewIdentityScope,
    val kind: CapturedIdentityKind,
    val wireframeKind: CapturedWireframeKind?,
    val namespace: List<String>,
    val localId: String,
    val wireId: Long
) {
    fun path(): List<String> = namespace + kind.name + localId
}
