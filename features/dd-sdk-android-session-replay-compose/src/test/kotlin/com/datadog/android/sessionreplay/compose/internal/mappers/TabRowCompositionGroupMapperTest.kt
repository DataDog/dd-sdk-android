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
internal class TabRowCompositionGroupMapperTest : AbstractCompositionGroupMapperTest() {

    private lateinit var tabRowCompositionGroupMapper: TabRowCompositionGroupMapper

    private lateinit var mockCompositionGroup: CompositionGroup

    private lateinit var fakeBoxWithDensity: Box

    @BeforeEach
    fun `set up`(forge: Forge) {
        tabRowCompositionGroupMapper = TabRowCompositionGroupMapper(colorStringFormatter = mockColorStringFormatter)
        mockCompositionGroup = mockGroupWithCoordinates(forge)
        fakeBoxWithDensity = Box(
            left = forge.aLong(),
            top = forge.aLong(),
            right = forge.aLong(),
            bottom = forge.aLong()
        )
    }

    @Test
    fun `M return the correct ui context W map`(forge: Forge) {
        // Given
        val contentColor = forge.aLong()
        val contentColorHexStr = forge.aString(size = 6)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                convertColorIntAlpha(contentColor).first,
                convertColorIntAlpha(contentColor).second
            )
        ).thenReturn(contentColorHexStr)

        // When
        val actual = tabRowCompositionGroupMapper.map(
            stableGroupId = fakeGroupId,
            parameters = listOf(
                ComposableParameter(
                    name = "contentColor",
                    value = contentColor
                )
            ).asSequence(),
            boxWithDensity = fakeBoxWithDensity,
            uiContext = fakeUiContext
        )

        // Then
        Assertions.assertThat(actual.uiContext).isEqualTo(
            fakeUiContext.copy(
                parentContentColor = contentColorHexStr
            )
        )
    }

    @RepeatedTest(8)
    fun `M return the correct wireframe W map`(forge: Forge) {
        // Given
        val backgroundColor = forge.aLong()
        val backgroundColorHexStr = forge.aString(size = 6)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                convertColorIntAlpha(backgroundColor).first,
                convertColorIntAlpha(backgroundColor).second
            )
        ).thenReturn(backgroundColorHexStr)
        val actual = tabRowCompositionGroupMapper.map(
            stableGroupId = fakeGroupId,
            parameters = listOf(
                ComposableParameter(
                    name = "backgroundColor",
                    value = backgroundColor
                )
            ).asSequence(),
            boxWithDensity = fakeBoxWithDensity,
            uiContext = fakeUiContext
        )

        // When
        val expected = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeGroupId,
            x = fakeBoxWithDensity.x,
            y = fakeBoxWithDensity.y,
            width = fakeBoxWithDensity.width,
            height = fakeBoxWithDensity.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = backgroundColorHexStr
            )
        )

        // Then
        Assertions.assertThat(actual.wireframe).isEqualTo(expected)
    }

    private fun convertColorIntAlpha(color: Long): Pair<Int, Int> {
        val c = Color(color shr COMPOSE_COLOR_SHIFT)
        return Pair(c.toArgb(), (c.alpha * MAX_ALPHA).roundToInt())
    }

    companion object {
        private const val COMPOSE_COLOR_SHIFT = 32
        private const val MAX_ALPHA = 255
    }
}
