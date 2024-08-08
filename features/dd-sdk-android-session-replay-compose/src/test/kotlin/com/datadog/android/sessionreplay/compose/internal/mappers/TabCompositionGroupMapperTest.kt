/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.data.ComposableParameter
import com.datadog.android.sessionreplay.compose.internal.stableId
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class TabCompositionGroupMapperTest : AbstractCompositionGroupMapperTest() {

    private lateinit var tabCompositionGroupMapper: TabCompositionGroupMapper

    private lateinit var mockCompositionGroup: CompositionGroup

    private lateinit var fakeBoxWithDensity: Box

    @BeforeEach
    fun `set up`(forge: Forge) {
        tabCompositionGroupMapper = TabCompositionGroupMapper(colorStringFormatter = mockColorStringFormatter)
        mockCompositionGroup = mockGroupWithCoordinates(forge)
        fakeBoxWithDensity = Box(
            left = forge.aLong(),
            top = forge.aLong(),
            right = forge.aLong(),
            bottom = forge.aLong()
        )
    }

    @Test
    fun `M return the correct context color W tab is selected()`(forge: Forge) {
        // Given
        val selectedColor = forge.aLong()
        val unSelectedColor = forge.aLong()
        val selectedColorHexStr = forge.aString(size = 6)
        val unSelectedColorHexStr = forge.aString(size = 6)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                convertColorIntAlpha(selectedColor).first,
                convertColorIntAlpha(selectedColor).second
            )
        ).thenReturn(selectedColorHexStr)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                convertColorIntAlpha(unSelectedColor).first,
                convertColorIntAlpha(unSelectedColor).second
            )
        ).thenReturn(unSelectedColorHexStr)

        // When
        val actual = tabCompositionGroupMapper.map(
            stableGroupId = fakeGroupId,
            parameters = listOf(
                ComposableParameter(
                    name = "selected",
                    value = true
                ),
                ComposableParameter(
                    name = "selectedContentColor",
                    value = selectedColor
                ),
                ComposableParameter(
                    name = "unselectedContentColor",
                    value = unSelectedColor
                )
            ).asSequence(),
            boxWithDensity = fakeBoxWithDensity,
            uiContext = fakeUiContext
        )

        // Then
        Assertions.assertThat(actual.uiContext)
            .isEqualTo(fakeUiContext.copy(parentContentColor = selectedColorHexStr))
    }

    @Test
    fun `M return the correct context color W tab is unselected()`(forge: Forge) {
        // Given
        val selectedColor = forge.aLong()
        val unSelectedColor = forge.aLong()
        val selectedColorHexStr = forge.aString(size = 6)
        val unSelectedColorHexStr = forge.aString(size = 6)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                convertColorIntAlpha(selectedColor).first,
                convertColorIntAlpha(selectedColor).second
            )
        ).thenReturn(selectedColorHexStr)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                convertColorIntAlpha(unSelectedColor).first,
                convertColorIntAlpha(unSelectedColor).second
            )
        ).thenReturn(unSelectedColorHexStr)

        // When
        val actual = tabCompositionGroupMapper.map(
            stableGroupId = fakeGroupId,
            parameters = listOf(
                ComposableParameter(
                    name = "selected",
                    value = false
                ),
                ComposableParameter(
                    name = "selectedContentColor",
                    value = selectedColor
                ),
                ComposableParameter(
                    name = "unselectedContentColor",
                    value = unSelectedColor
                )
            ).asSequence(),
            boxWithDensity = fakeBoxWithDensity,
            uiContext = fakeUiContext
        )

        // Then
        Assertions.assertThat(actual.uiContext)
            .isEqualTo(fakeUiContext.copy(parentContentColor = unSelectedColorHexStr))
    }

    @RepeatedTest(8)
    fun `M return the correct wireframe W map`() {
        // Given
        val actual = tabCompositionGroupMapper.map(
            compositionGroup = mockCompositionGroup,
            composeContext = fakeComposeContext,
            uiContext = fakeUiContext
        )

        // When
        val expectedBox = requireNotNull(Box.from(compositionGroup = mockCompositionGroup))
        val boxWithDensity = expectedBox.withDensity(fakeUiContext.density)
        val expected = MobileSegment.Wireframe.ShapeWireframe(
            id = mockCompositionGroup.stableId(),
            x = boxWithDensity.x,
            y = boxWithDensity.y,
            width = boxWithDensity.width,
            height = boxWithDensity.height
        )

        // Then
        Assertions.assertThat(actual?.wireframe).isEqualTo(expected)
    }

    private fun convertColorIntAlpha(color: Long): Pair<Int, Int> {
        val c = Color(color shr COMPOSE_COLOR_SHIFT)
        return Pair(c.toArgb(), (c.alpha * MAX_ALPHA).roundToInt())
    }

    companion object {
        /** As defined in Compose's ColorSpaces. */
        private const val COMPOSE_COLOR_SHIFT = 32
        private const val MAX_ALPHA = 255
    }
}
