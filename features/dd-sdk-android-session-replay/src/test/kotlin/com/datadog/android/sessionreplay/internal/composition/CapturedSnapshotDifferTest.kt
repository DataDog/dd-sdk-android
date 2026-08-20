/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CapturedSnapshotDifferTest {

    @Test
    fun `M produce an all-unchanged mutation W diff { nothing changed }`() {
        // Given
        val tree = compositionTestTree()

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, tree.snapshot)

        // Then
        assertThat(mutation).isEqualTo(
            CapturedMutationSet(timestamp = tree.snapshot.timestamp, scope = tree.scope)
        )
        assertValidMutation(mutation, tree.snapshot)
    }

    @Test
    fun `M return null W diff { scope differs }`() {
        // Given
        val previous = compositionTestTree()
        val current = compositionTestTree(scopeValue = "other-view")

        // When
        val mutation = CapturedSnapshotDiffer.diff(previous.snapshot, current.snapshot)

        // Then
        assertThat(mutation).isNull()
    }

    @Test
    fun `M add a new layer W diff { layer added }`() {
        // Given
        val tree = compositionTestTree()
        val newLayerIdentity = tree.factory.layer(tree.window, "new-layer")
        val newLayer = CapturedLayer(
            identity = newLayerIdentity,
            kind = CapturedLayerKind.COMPOSITION_LAYER,
            bounds = CapturedBounds(9, 9, 9, 9),
            children = emptyList()
        )
        val rootWithNewChild = tree.root.copy(
            children = tree.root.children + CapturedChild.Layer(newLayerIdentity)
        )
        val current = tree.snapshot.copy(
            root = rootWithNewChild,
            layers = tree.snapshot.layers + newLayer
        )

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        checkNotNull(mutation)
        assertThat((mutation.adds as CapturedChange.Set).value).containsExactly(newLayer)
        assertThat(mutation.root).isEqualTo(CapturedChange.Set(rootWithNewChild))
        assertValidMutation(mutation, tree.snapshot)
    }

    @Test
    fun `M remove a layer and its wireframe together W diff { subtree removed }`() {
        // Given
        val tree = compositionTestTree()
        val rootWithoutLayer = tree.root.copy(children = emptyList())
        val current = tree.snapshot.copy(
            root = rootWithoutLayer,
            layers = emptyList(),
            wireframes = emptyList()
        )

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        checkNotNull(mutation)
        assertThat((mutation.removes as CapturedChange.Set).value).containsExactly(tree.layer.identity)
        assertThat(mutation.root).isEqualTo(CapturedChange.Set(rootWithoutLayer))
        assertValidMutation(mutation, tree.snapshot)
    }

    @Test
    fun `M emit a sparse bounds update W diff { layer resized }`() {
        // Given
        val tree = compositionTestTree()
        val resizedLayer = tree.layer.copy(bounds = CapturedBounds(1, 2, 30, 40))
        val current = tree.snapshot.copy(layers = listOf(resizedLayer))

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        checkNotNull(mutation)
        val update = (mutation.updates as CapturedChange.Set).value.single()
        assertThat(update.identity).isEqualTo(tree.layer.identity)
        assertThat(update.x).isEqualTo(CapturedChange.Unchanged)
        assertThat(update.y).isEqualTo(CapturedChange.Unchanged)
        assertThat(update.width).isEqualTo(CapturedChange.Set(30L))
        assertThat(update.height).isEqualTo(CapturedChange.Set(40L))
        assertThat(update.children).isEqualTo(CapturedChange.Unchanged)
        assertValidMutation(mutation, tree.snapshot)
    }

    @Test
    fun `M emit a children update W diff { layer reparented }`() {
        // Given
        val tree = compositionTestTree()
        val movedLayer = tree.layer.copy(children = emptyList())
        val current = tree.snapshot.copy(
            layers = listOf(movedLayer),
            wireframes = emptyList()
        )

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        checkNotNull(mutation)
        val update = (mutation.updates as CapturedChange.Set).value.single()
        assertThat(update.children).isEqualTo(CapturedChange.Set(emptyList<CapturedChild>()))
        assertValidMutation(mutation, tree.snapshot)
    }

    @Test
    fun `M emit a modifiers update W diff { layer modifiers changed }`() {
        // Given
        val tree = compositionTestTree()
        val modifiedLayer = tree.layer.copy(modifiers = listOf(CapturedModifier.Opacity(0.5)))
        val current = tree.snapshot.copy(layers = listOf(modifiedLayer))

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        checkNotNull(mutation)
        val update = (mutation.updates as CapturedChange.Set).value.single()
        assertThat(update.modifiers).isEqualTo(CapturedChange.Set(listOf(CapturedModifier.Opacity(0.5))))
        assertValidMutation(mutation, tree.snapshot)
    }

    @Test
    fun `M return null W diff { a persisting wireframe's content changed }`() {
        // Given
        val tree = compositionTestTree()
        val changedWireframe = (tree.wireframe as CapturedWireframe.Shape).copy(
            bounds = CapturedBounds(9, 9, 9, 9)
        )
        val current = tree.snapshot.copy(wireframes = listOf(changedWireframe))

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        assertThat(mutation).isNull()
    }

    @Test
    fun `M return null W diff { a brand new wireframe appears }`() {
        // Given
        val tree = compositionTestTree()
        val newWireframeIdentity = tree.factory.placeholderWireframe(tree.layer.identity)
        val newWireframe = CapturedWireframe.PrivacyPlaceholder(
            identity = newWireframeIdentity,
            bounds = CapturedBounds(9, 9, 9, 9)
        )
        val layerWithNewChild = tree.layer.copy(
            children = tree.layer.children + CapturedChild.Wireframe(newWireframeIdentity)
        )
        val current = tree.snapshot.copy(
            layers = listOf(layerWithNewChild),
            wireframes = tree.snapshot.wireframes + newWireframe
        )

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        assertThat(mutation).isNull()
    }

    @Test
    fun `M return null W diff { current has no root }`() {
        // Given
        val tree = compositionTestTree()
        val current = tree.snapshot.copy(root = null)

        // When
        val mutation = CapturedSnapshotDiffer.diff(tree.snapshot, current)

        // Then
        assertThat(mutation).isNull()
    }

    private fun assertValidMutation(mutation: CapturedMutationSet?, base: CapturedFullSnapshot) {
        checkNotNull(mutation)
        val validation = CapturedMutationValidation(mutation, base).validate()
        assertThat(validation).isEqualTo(CaptureValidationResult.Valid)
    }
}
