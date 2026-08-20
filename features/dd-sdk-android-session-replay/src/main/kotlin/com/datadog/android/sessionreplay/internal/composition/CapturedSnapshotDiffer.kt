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
 * The wire mutation model (block 1) can only express layer-level structural changes (a layer's own
 * bounds/children/modifiers/composite operation) - it has no operation for a wireframe's own
 * content changing (text, its independently-absolute bounds, an image resource, a style), nor for
 * delivering a brand-new wireframe's definition out of band. [diff] therefore requires every
 * wireframe referenced by [current] to already have existed, unchanged, in [previous] before it
 * will attempt a layer diff at all; a wireframe simply disappearing needs no such check, since it
 * becomes unreachable exactly when its owning layer is removed or stops referencing it, which the
 * layer diff below already produces correctly by construction.
 */
internal object CapturedSnapshotDiffer {

    @Suppress("ReturnCount")
    fun diff(previous: CapturedFullSnapshot, current: CapturedFullSnapshot): CapturedMutationSet? {
        if (previous.scope != current.scope) return null
        val currentRoot = current.root ?: return null
        if (!isWireframeContentStable(previous, current)) return null

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

    /**
     * True only if every wireframe [current] references already existed, with identical content,
     * in [previous]. A brand-new wireframe id has no delivery mechanism on the wire today, and a
     * persisting wireframe with different content has no update mechanism either - both force a
     * full snapshot. Wireframes present only in [previous] are deliberately not examined here.
     */
    private fun isWireframeContentStable(previous: CapturedFullSnapshot, current: CapturedFullSnapshot): Boolean {
        val previousWireframesById = previous.wireframes.associateBy { it.identity.wireId }
        return current.wireframes.all { wireframe ->
            previousWireframesById[wireframe.identity.wireId] == wireframe
        }
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
