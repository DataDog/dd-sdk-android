/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import fr.xgouchet.elmyr.Forge

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

internal fun compositionTestTree(
    scopeValue: String = "rum-view",
    timestamp: Long = 123,
    windowName: String = "window",
    layerName: String = "layer",
    rootBounds: CapturedBounds = CapturedBounds(0, 0, 100, 200),
    layerBounds: CapturedBounds = CapturedBounds(1, 2, 3, 4)
): CompositionTestTree {
    val scope = RumViewIdentityScope(scopeValue)
    val factory = DefaultCapturedIdentityFactory(scope)
    val rootIdentity = factory.screenRoot()
    val window = factory.window(windowName)
    val layerIdentity = factory.layer(window, layerName)
    val wireframeIdentity = factory.shapeWireframe(layerIdentity)
    val wireframe = CapturedWireframe.Shape(
        identity = wireframeIdentity,
        bounds = layerBounds
    )
    val layer = CapturedLayer(
        identity = layerIdentity,
        kind = CapturedLayerKind.COMPOSITION_LAYER,
        bounds = layerBounds,
        children = listOf(CapturedChild.Wireframe(wireframeIdentity))
    )
    val root = CapturedLayer(
        identity = rootIdentity,
        kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
        bounds = rootBounds,
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
            timestamp = timestamp,
            scope = scope,
            root = root,
            layers = listOf(layer),
            wireframes = listOf(wireframe)
        )
    )
}

internal fun Forge.aCompositionTestTree(
    scopeValue: String = anAlphabeticalString()
): CompositionTestTree = compositionTestTree(
    scopeValue = scopeValue,
    timestamp = aLong(min = 1L),
    windowName = anAlphabeticalString(),
    layerName = anAlphabeticalString(),
    rootBounds = aCapturedBounds(),
    layerBounds = aCapturedBounds()
)

internal fun Forge.aCapturedBounds(): CapturedBounds = CapturedBounds(
    x = aLong(min = 0L, max = 1_000L),
    y = aLong(min = 0L, max = 1_000L),
    width = aLong(min = 0L, max = 1_000L),
    height = aLong(min = 0L, max = 1_000L)
)
