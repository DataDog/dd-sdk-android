/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedLayer

/**
 * Diffs two accepted [CapturedFullSnapshot]s of the same [com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope]
 * into a [CapturedMutationSet], or returns null when the change can't be expressed as a mutation
 * under the current wire contract - the caller must fall back to a full snapshot in that case.
 *
 * This only diffs layer-level structure (a layer's own bounds/children/modifiers/composite
 * operation) - it is deliberately blind to a wireframe's own content (text, its
 * independently-absolute bounds, an image resource, a style) and to whether a wireframe referenced
 * by a layer's children has actually been delivered yet. That is intentional, not an omission: a
 * wireframe's own content is diffed and delivered completely independently, by
 * [DefaultCapturedTreeWireMapper.mapWireframeMutation] against the same flat [CapturedFullSnapshot.wireframes]
 * lists this differ ignores - mirroring how the composition tree and the flat wireframe list are
 * two separately-diffed, separately-delivered records on the wire. A layer referencing a wireframe
 * whose definition arrives only via that other record is expected and valid; see
 * `CapturedSnapshotValidation`'s `validateWireframeDefinitions` for the corresponding validation-side
 * accommodation.
 */
internal object CapturedSnapshotDiffer {

    @Suppress("ReturnCount")
    fun diff(previous: CapturedFullSnapshot, current: CapturedFullSnapshot): CapturedMutationSet? {
        if (previous.scope != current.scope) return null
        val currentRoot = current.root ?: return null

        val previousLayers = previous.layers.associateBy { it.identity.wireId }
        val currentLayers = current.layers.associateBy { it.identity.wireId }

        val adds = currentLayers.filterKeys { it !in previousLayers }.values.toList()
        val removes = previousLayers.filterKeys { it !in currentLayers }.values.map { it.identity }
        val updates = currentLayers.mapNotNull { (wireId, layer) ->
            previousLayers[wireId]?.let { diffLayer(it, layer) }
        }

        return CapturedMutationSet(
            timestamp = current.timestamp,
            scope = current.scope,
            root = if (previous.root != currentRoot) CapturedChange.Set(currentRoot) else CapturedChange.Unchanged,
            adds = adds.toChange(),
            removes = removes.toChange(),
            updates = updates.toChange()
        )
    }

    /** Null if nothing about [current] differs from [previous]; otherwise a sparse per-field update. */
    private fun diffLayer(previous: CapturedLayer, current: CapturedLayer): CapturedLayerUpdate? {
        val update = CapturedLayerUpdate(
            identity = current.identity,
            x = changeIfDiffers(previous.bounds.x, current.bounds.x),
            y = changeIfDiffers(previous.bounds.y, current.bounds.y),
            width = changeIfDiffers(previous.bounds.width, current.bounds.width),
            height = changeIfDiffers(previous.bounds.height, current.bounds.height),
            children = changeIfDiffers(previous.children, current.children),
            modifiers = changeIfDiffers(previous.modifiers, current.modifiers),
            compositeOperation = changeIfDiffers(previous.compositeOperation, current.compositeOperation)
        )
        val isUnchanged = update.x == CapturedChange.Unchanged &&
            update.y == CapturedChange.Unchanged &&
            update.width == CapturedChange.Unchanged &&
            update.height == CapturedChange.Unchanged &&
            update.children == CapturedChange.Unchanged &&
            update.modifiers == CapturedChange.Unchanged &&
            update.compositeOperation == CapturedChange.Unchanged
        return update.takeUnless { isUnchanged }
    }

    private fun <T> changeIfDiffers(previous: T, current: T): CapturedChange<T> =
        if (previous == current) CapturedChange.Unchanged else CapturedChange.Set(current)

    private fun <T> List<T>.toChange(): CapturedChange<List<T>> =
        takeIf { it.isNotEmpty() }?.let { CapturedChange.Set(it) } ?: CapturedChange.Unchanged
}
