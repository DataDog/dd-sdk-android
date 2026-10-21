/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.utils

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.ModifierInfo
import androidx.compose.ui.semantics.SemanticsNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ImagePainterMetadataTest {
    private val testedReflectionUtils = ReflectionUtils()

    @Test
    fun `M retain crop and alignment W resolve painter metadata { actual Coil element }`() {
        // Given
        val type = Class.forName("coil.compose.ContentPainterElement")
        val constructor = type.declaredConstructors.single { it.parameterCount == 5 }
        constructor.isAccessible = true
        val modifier = constructor.newInstance(
            mock<Painter>(),
            Alignment.BottomEnd,
            ContentScale.Crop,
            1f,
            null
        ) as Modifier
        val stubNode = nodeWithModifier(modifier)

        // When + Then
        assertThat(testedReflectionUtils.getContentScale(stubNode)).isSameAs(ContentScale.Crop)
        assertThat(testedReflectionUtils.getAlignment(stubNode)).isSameAs(Alignment.BottomEnd)
    }

    @Test
    fun `M retain crop and alignment W resolve painter metadata { actual Compose painter }`() {
        // Given
        val modifier = Modifier.paint(mock(), alignment = Alignment.TopEnd, contentScale = ContentScale.Crop)
        val stubNode = nodeWithModifier(modifier)

        // When + Then
        assertThat(testedReflectionUtils.getContentScale(stubNode)).isSameAs(ContentScale.Crop)
        assertThat(testedReflectionUtils.getAlignment(stubNode)).isSameAs(Alignment.TopEnd)
    }

    private fun nodeWithModifier(modifier: Modifier): SemanticsNode {
        val stubNode: SemanticsNode = mock()
        val stubLayout: LayoutInfo = mock()
        val modifiers = modifier.foldIn(mutableListOf<ModifierInfo>()) { list, element ->
            val stubInfo: ModifierInfo = mock()
            whenever(stubInfo.modifier).thenReturn(element)
            list.apply { add(stubInfo) }
        }
        whenever(stubNode.layoutInfo).thenReturn(stubLayout)
        whenever(stubLayout.getModifierInfo()).thenReturn(modifiers)
        return stubNode
    }
}
