/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose.internal.utils

import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsModifier
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.compose.DatadogSemanticsPropertyKey
import com.datadog.tools.unit.forge.BaseConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(
        MockitoExtension::class,
        ForgeExtension::class
    )
)
@ForgeConfiguration(value = BaseConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LayoutNodeUtilsTest {

    private val testedLayoutNodeUtils = LayoutNodeUtils()

    @Test
    fun `M return correct target node W call resolveLayoutNode {legacy compose}`(
        forge: Forge
    ) {
        val fakeTagName = forge.aString()
        val isClickable = forge.aBool()
        val isScrollable = forge.aBool()
        val mockNode = mockLegacyLayoutNode(
            fakeTagName,
            isClickable,
            isScrollable
        )
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        assertThat(result).isEqualTo(
            LayoutNodeUtils.TargetNode(
                tag = fakeTagName,
                isScrollable = isScrollable,
                isClickable = isClickable
            )
        )
    }

    private fun mockLegacyLayoutNode(
        tagName: String,
        isClickable: Boolean = false,
        isScrollable: Boolean = false
    ): LayoutNode {
        val mockLayoutCoordinates = mock<LayoutCoordinates>()
        val mockSemanticsConfiguration = mock<SemanticsConfiguration> {
            whenever(it.contains(SemanticsActions.OnClick)).thenReturn(isClickable)
            whenever(it.contains(SemanticsActions.ScrollBy)).thenReturn(isScrollable)
            whenever(it.getOrNull(DatadogSemanticsPropertyKey)).thenReturn(tagName)
        }

        val mockSemanticsModifier = mock<SemanticsModifier> {
            whenever(it.semanticsConfiguration).thenReturn(mockSemanticsConfiguration)
        }

        val node = mock<LayoutNode> {
            whenever(it.getModifierInfo()) doReturn listOf(
                ModifierInfo(mockSemanticsModifier, mockLayoutCoordinates)
            )
        }
        return node
    }
}
