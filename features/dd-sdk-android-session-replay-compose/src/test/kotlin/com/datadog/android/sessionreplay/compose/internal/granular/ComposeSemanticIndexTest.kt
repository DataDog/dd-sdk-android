/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.granular

import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class ComposeSemanticIndexTest {
    @Test
    fun `M index actual semantics boundaries W capture starts`() {
        val fakeChild = node()
        val fakeRoot = node(listOf(fakeChild))

        val fakeIndex = ComposeSemanticIndex.build(fakeRoot) { true }

        assertThat(fakeIndex?.get(fakeRoot.layoutInfo)).isSameAs(fakeRoot)
        assertThat(fakeIndex?.get(fakeChild.layoutInfo)).isSameAs(fakeChild)
        verify(fakeRoot).children
        verify(fakeChild).children
    }

    @Test
    fun `M retain real parent semantics W fake child shares its layout`() {
        val stubSharedLayout: LayoutInfo = mock()
        val fakeChild = node(layout = stubSharedLayout)
        val fakeRoot = node(listOf(fakeChild), stubSharedLayout)

        val fakeIndex = ComposeSemanticIndex.build(fakeRoot) { true }

        assertThat(fakeIndex?.size).isEqualTo(1)
        assertThat(fakeIndex?.get(stubSharedLayout)).isSameAs(fakeRoot)
        verify(fakeChild, never()).children
    }

    @Test
    fun `M discard partial semantics index W generation expires`() {
        val fakeRoot = node(listOf(node()))
        var fakeChecks = 0

        val fakeIndex = ComposeSemanticIndex.build(fakeRoot) { ++fakeChecks < 2 }

        assertThat(fakeIndex).isNull()
    }

    private fun node(children: List<SemanticsNode> = emptyList(), layout: LayoutInfo = mock()): SemanticsNode {
        val stubNode: SemanticsNode = mock()
        whenever(stubNode.layoutInfo).thenReturn(layout)
        whenever(stubNode.children).thenReturn(children)
        return stubNode
    }
}
