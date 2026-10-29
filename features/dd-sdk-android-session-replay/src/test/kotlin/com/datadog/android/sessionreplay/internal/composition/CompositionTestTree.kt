/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
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

private fun compositionTestTree(
    scopeValue: String,
    timestamp: Long,
    windowName: String,
    layerName: String,
    rootBounds: CapturedBounds,
    layerBounds: CapturedBounds
): CompositionTestTree {
    val fakeScope = RumViewIdentityScope(scopeValue)
    val fakeFactory = DefaultCapturedIdentityFactory(fakeScope)
    val fakeRootIdentity = fakeFactory.screenRoot()
    val fakeWindow = fakeFactory.window(windowName)
    val fakeLayerIdentity = fakeFactory.layer(fakeWindow, layerName)
    val fakeWireframeIdentity = fakeFactory.shapeWireframe(fakeLayerIdentity)
    val fakeWireframe = CapturedWireframe.Shape(
        identity = fakeWireframeIdentity,
        bounds = layerBounds
    )
    val fakeLayer = CapturedLayer(
        identity = fakeLayerIdentity,
        kind = CapturedLayerKind.COMPOSITION_LAYER,
        bounds = layerBounds,
        children = listOf(CapturedChild.Wireframe(fakeWireframeIdentity))
    )
    val fakeRoot = CapturedLayer(
        identity = fakeRootIdentity,
        kind = CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
        bounds = rootBounds,
        children = listOf(CapturedChild.Layer(fakeLayerIdentity))
    )
    return CompositionTestTree(
        scope = fakeScope,
        factory = fakeFactory,
        window = fakeWindow,
        root = fakeRoot,
        layer = fakeLayer,
        wireframeIdentity = fakeWireframeIdentity,
        wireframe = fakeWireframe,
        snapshot = CapturedFullSnapshot(
            timestamp = timestamp,
            scope = fakeScope,
            root = fakeRoot,
            layers = listOf(fakeLayer),
            wireframes = listOf(fakeWireframe)
        )
    )
}

internal fun Forge.aCompositionTestTree(
    scopeValue: String = anAlphabeticalString()
): CompositionTestTree = compositionTestTree(
    scopeValue = scopeValue,
    timestamp = aLong(min = 1L, max = Long.MAX_VALUE - 1L),
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
