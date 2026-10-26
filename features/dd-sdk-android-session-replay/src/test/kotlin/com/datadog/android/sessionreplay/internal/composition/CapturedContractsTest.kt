/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CapturedContractsTest {

    @Forgery
    lateinit var fakeTree: CompositionTestTree

    @Test
    fun `M preserve child reference order W copy()`() {
        // Given
        val fakeSecondLayer = fakeTree.factory.layer(fakeTree.window, fakeTree.layer.identity.localId + "-second-layer")
        val fakeChildren = listOf(
            CapturedChild.Wireframe(fakeTree.wireframeIdentity),
            CapturedChild.Layer(fakeSecondLayer),
            CapturedChild.Layer(fakeTree.layer.identity)
        )

        // When
        val result = fakeTree.layer.copy(children = fakeChildren)

        // Then
        assertThat(result.children.map { it.identity })
            .containsExactly(fakeTree.wireframeIdentity, fakeSecondLayer, fakeTree.layer.identity)
    }

    @Test
    fun `M preserve modifier order W copy()`() {
        // Given
        val fakeModifiers = listOf(CapturedModifier.Opacity(0.5), CapturedModifier.GaussianBlur(2.0))

        // When
        val result = fakeTree.layer.copy(modifiers = fakeModifiers)

        // Then
        assertThat(result.modifiers).containsExactly(CapturedModifier.Opacity(0.5), CapturedModifier.GaussianBlur(2.0))
    }

    @Test
    fun `M leave omitted fields unchanged W CapturedLayerUpdate()`() {
        // When
        val result = CapturedLayerUpdate(identity = fakeTree.layer.identity)

        // Then
        assertThat(result.children).isEqualTo(CapturedChange.Unchanged)
        assertThat(result.modifiers).isEqualTo(CapturedChange.Unchanged)
    }

    @Test
    fun `M preserve explicit field clearing W CapturedLayerUpdate()`() {
        // When
        val result = CapturedLayerUpdate(
            identity = fakeTree.layer.identity,
            children = CapturedChange.Set(emptyList()),
            modifiers = CapturedChange.Set(emptyList()),
            compositeOperation = CapturedChange.Set(null)
        )

        // Then
        assertThat(result.children).isEqualTo(CapturedChange.Set(emptyList<CapturedChild>()))
        assertThat(result.modifiers).isEqualTo(CapturedChange.Set(emptyList<CapturedModifier>()))
        assertThat(result.compositeOperation).isEqualTo(CapturedChange.Set(null))
    }

    @Test
    fun `M preserve resolved resource ID W Resolved()`(@StringForgery fakeResourceId: String) {
        // When
        val result: PixelResource = PixelResource.Resolved(fakeResourceId)

        // Then
        assertThat(result).isEqualTo(PixelResource.Resolved(fakeResourceId))
    }

    @Test
    fun `M retain unresolved resource sentinel W Unresolved`() {
        // When
        val result: PixelResource = PixelResource.Unresolved

        // Then
        assertThat(result).isSameAs(PixelResource.Unresolved)
    }
}
