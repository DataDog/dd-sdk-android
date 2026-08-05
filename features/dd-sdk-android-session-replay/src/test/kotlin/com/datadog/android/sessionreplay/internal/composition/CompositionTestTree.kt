/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

internal data class CompositionTestTree(
    val scope: RumViewIdentityScope,
    val factory: DefaultCapturedIdentityFactory,
    val window: CapturedIdentity,
    val root: CapturedLayer,
    val layer: CapturedLayer,
    val wireframeIdentity: CapturedIdentity,
    val wireframe: CapturedWireframe,
    val snapshot: CapturedFullSnapshot
)

internal fun compositionTestTree(scopeValue: String = "rum-view"): CompositionTestTree {
    val scope = RumViewIdentityScope(scopeValue)
    val factory = DefaultCapturedIdentityFactory(scope)
    val rootIdentity = factory.screenRoot()
    val window = factory.window("window")
    val layerIdentity = factory.layer(window, "layer")
    val wireframeIdentity = factory.shapeWireframe(layerIdentity)
    val wireframe = CapturedWireframe.Shape(
        identity = wireframeIdentity,
        bounds = CapturedBounds(1, 2, 3, 4)
    )
    val layer = CapturedLayer(
        identity = layerIdentity,
        kind = CapturedLayerKind.COMPOSITION_LAYER,
        bounds = CapturedBounds(1, 2, 3, 4),
        children = listOf(CapturedChild.Wireframe(wireframeIdentity))
    )
    val root = CapturedLayer(
        identity = rootIdentity,
        kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
        bounds = CapturedBounds(0, 0, 100, 200),
        children = listOf(CapturedChild.Layer(layerIdentity))
    )
    return CompositionTestTree(
        scope = scope,
        factory = factory,
        window = window,
        root = root,
        layer = layer,
        wireframeIdentity = wireframeIdentity,
        wireframe = wireframe,
        snapshot = CapturedFullSnapshot(
            timestamp = 123,
            scope = scope,
            root = root,
            layers = listOf(layer),
            wireframes = listOf(wireframe)
        )
    )
}
