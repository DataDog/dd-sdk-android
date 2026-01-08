/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.datadog.android.compose.internal.utils

import androidx.compose.foundation.ClickableElement
import androidx.compose.foundation.CombinedClickableElement
import androidx.compose.foundation.ScrollingLayoutElement
import androidx.compose.foundation.gestures.ScrollableElement
import androidx.compose.foundation.selection.SelectableElement
import androidx.compose.foundation.selection.ToggleableElement
import androidx.compose.foundation.selection.TriStateToggleableElement
import androidx.compose.ui.Modifier
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
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.stream.Stream

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

    // region Legacy Compose (SemanticsModifier)

    @Test
    fun `M return correct target node W call resolveLayoutNode {legacy compose}`(
        forge: Forge
    ) {
        // Given
        val fakeTagName = forge.aString()
        val isClickable = forge.aBool()
        val isScrollable = forge.aBool()
        val mockNode = mockLegacyLayoutNode(
            fakeTagName,
            isClickable,
            isScrollable
        )

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        assertThat(result).isEqualTo(
            LayoutNodeUtils.TargetNode(
                tag = fakeTagName,
                isScrollable = isScrollable,
                isClickable = isClickable
            )
        )
    }

    // endregion

    // region Clickable Modifier Elements

    @ParameterizedTest
    @MethodSource("clickableModifierElements")
    fun `M return clickable target node W resolveLayoutNode {clickable modifier element}`(
        testCase: ModifierTestCase,
        @StringForgery fakeTagName: String
    ) {
        // Given
        val mockNode = mockLayoutNodeWithModifiers(fakeTagName, testCase.modifier)

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        checkNotNull(result)
        assertThat(result.tag).isEqualTo(fakeTagName)
        assertThat(result.isClickable).isTrue()
        assertThat(result.isScrollable).isFalse()
    }

    // endregion

    // region Scrollable Modifier Elements

    @ParameterizedTest
    @MethodSource("scrollableModifierElements")
    fun `M return scrollable target node W resolveLayoutNode {scrollable modifier element}`(
        testCase: ModifierTestCase,
        @StringForgery fakeTagName: String
    ) {
        // Given
        val mockNode = mockLayoutNodeWithModifiers(fakeTagName, testCase.modifier)

        // When
        val result = testedLayoutNodeUtils.resolveLayoutNode(mockNode)

        // Then
        checkNotNull(result)
        assertThat(result.tag).isEqualTo(fakeTagName)
        assertThat(result.isClickable).isFalse()
        assertThat(result.isScrollable).isTrue()
    }

    // endregion

    // region Private

    private fun mockLayoutNodeWithModifiers(
        tagName: String,
        vararg modifiers: Modifier.Element
    ): LayoutNode {
        val mockLayoutCoordinates = mock<LayoutCoordinates>()
        val mockSemanticsConfiguration = mock<SemanticsConfiguration> {
            whenever(it.getOrNull(DatadogSemanticsPropertyKey)).thenReturn(tagName)
        }

        val mockSemanticsModifier = mock<SemanticsModifier> {
            whenever(it.semanticsConfiguration).thenReturn(mockSemanticsConfiguration)
        }

        val modifierInfoList = mutableListOf(
            ModifierInfo(mockSemanticsModifier, mockLayoutCoordinates)
        )
        modifiers.forEach { modifier ->
            modifierInfoList.add(ModifierInfo(modifier, mockLayoutCoordinates))
        }

        return mock<LayoutNode> {
            whenever(it.getModifierInfo()) doReturn modifierInfoList
        }
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

    // endregion

    data class ModifierTestCase(
        val name: String,
        val modifier: Modifier.Element
    ) {
        override fun toString(): String = name
    }

    companion object {
        @JvmStatic
        fun clickableModifierElements(): Stream<ModifierTestCase> = Stream.of(
            ModifierTestCase("TriStateToggleableElement", TriStateToggleableElement()),
            ModifierTestCase("ToggleableElement", ToggleableElement()),
            ModifierTestCase("ClickableElement", ClickableElement()),
            ModifierTestCase("CombinedClickableElement", CombinedClickableElement()),
            ModifierTestCase("SelectableElement", SelectableElement())
        )

        @JvmStatic
        fun scrollableModifierElements(): Stream<ModifierTestCase> = Stream.of(
            ModifierTestCase("ScrollingLayoutElement", ScrollingLayoutElement()),
            ModifierTestCase("ScrollableElement", ScrollableElement())
        )
    }
}
