/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import com.datadog.android.sessionreplay.model.MobileSegment

/**
 * Recorder-side representation of a composition tree layer, instantiated for every container
 * encountered during traversal (e.g. a `ViewGroup`, or the Compose equivalent).
 *
 * A [CompositionLayer] groups its [children] — which may be other layers or wireframes,
 * referenced by id via [MobileSegment.CompositionLayerChild] — so that container-level rendering
 * effects (currently just [alpha]) can be applied once to the whole group at playback time,
 * instead of having to be duplicated onto every descendant wireframe.
 *
 * This mirrors the composition tree schema (`composition-layer-schema.json`, generated as
 * [MobileSegment.CompositionLayer]) and the equivalent `SRCompositionLayer` built by iOS's
 * `CompositionTreeBuilder` (see https://github.com/DataDog/dd-sdk-ios/pull/3014). It is
 * intentionally simpler than the wire format for now: [alpha] stands in for what will eventually
 * be translated into a `CompositionLayerOpacityModifier` (only emitted when `alpha < 1`, same as
 * iOS) once this is wired into the wire-format conversion — additional group effects (clipping,
 * blur, shadow, etc.) are expected to be added the same way as this feature matures.
 *
 * @param id Stable identifier for this layer — shares the same id space as wireframe ids.
 * @param x Horizontal position of the layer, in density-normalized pixels.
 * @param y Vertical position of the layer, in density-normalized pixels.
 * @param width Width of the layer, in density-normalized pixels.
 * @param height Height of the layer, in density-normalized pixels.
 * @param alpha Opacity applied to the whole group of [children] at once, from 0 (fully
 * transparent) to 1 (fully opaque, the default).
 * @param children The layer's children, in rendering order — each either a nested
 * [CompositionLayer] or a wireframe, referenced by id.
 */
internal data class CompositionLayer(
    val id: Long,
    val x: Long,
    val y: Long,
    val width: Long,
    val height: Long,
    val alpha: Float = 1f,
    val children: List<MobileSegment.CompositionLayerChild> = emptyList()
)
