/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class CapturedContractsTest {

    @Test
    fun `M preserve ordered references W construct captured layer`() {
        // Given
        val tree = compositionTestTree()
        val secondLayer = tree.factory.layer(tree.window, "second-layer")

        // When
        val layer = tree.layer.copy(
            children = listOf(
                CapturedChild.Wireframe(tree.wireframeIdentity),
                CapturedChild.Layer(secondLayer),
                CapturedChild.Layer(tree.layer.identity)
            ),
            modifiers = listOf(
                CapturedModifier.Opacity(0.5),
                CapturedModifier.GaussianBlur(2.0)
            )
        )

        // Then
        assertThat(layer.children.map { it.identity })
            .containsExactly(tree.wireframeIdentity, secondLayer, tree.layer.identity)
        assertThat(layer.modifiers)
            .containsExactly(CapturedModifier.Opacity(0.5), CapturedModifier.GaussianBlur(2.0))
    }

    @Test
    fun `M distinguish unchanged from cleared W construct mutation`() {
        // Given
        val tree = compositionTestTree()

        // When
        val unchanged = CapturedLayerUpdate(identity = tree.layer.identity)
        val cleared = CapturedLayerUpdate(
            identity = tree.layer.identity,
            children = CapturedChange.Set(emptyList()),
            modifiers = CapturedChange.Set(emptyList()),
            compositeOperation = CapturedChange.Set(null)
        )

        // Then
        assertThat(unchanged.children).isEqualTo(CapturedChange.Unchanged)
        assertThat(unchanged.modifiers).isEqualTo(CapturedChange.Unchanged)
        assertThat(cleared.children).isEqualTo(CapturedChange.Set(emptyList<CapturedChild>()))
        assertThat(cleared.modifiers).isEqualTo(CapturedChange.Set(emptyList<CapturedModifier>()))
        assertThat(cleared.compositeOperation).isEqualTo(CapturedChange.Set(null))
    }

    @Test
    fun `M represent resolved and unresolved resources W construct pixel resource`() {
        // Given + When
        val resolved: PixelResource = PixelResource.Resolved("resource")
        val unresolved: PixelResource = PixelResource.Unresolved

        // Then
        assertThat(resolved).isEqualTo(PixelResource.Resolved("resource"))
        assertThat(unresolved).isSameAs(PixelResource.Unresolved)
    }
}
