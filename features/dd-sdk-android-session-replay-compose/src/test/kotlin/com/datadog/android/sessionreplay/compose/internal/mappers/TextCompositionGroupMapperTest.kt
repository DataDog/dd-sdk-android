/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers

import androidx.compose.runtime.tooling.CompositionGroup
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.Box
import com.datadog.android.sessionreplay.compose.internal.mappers.TextCompositionGroupMapper.Companion.DEFAULT_FONT_FAMILY
import com.datadog.android.sessionreplay.compose.internal.mappers.TextCompositionGroupMapper.Companion.DEFAULT_FONT_SIZE
import com.datadog.android.sessionreplay.compose.internal.mappers.TextCompositionGroupMapper.Companion.FIXED_INPUT_MASK
import com.datadog.android.sessionreplay.compose.internal.stableId
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.DefaultColorStringFormatter
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class TextCompositionGroupMapperTest : AbstractCompositionGroupMapperTest() {

    private lateinit var textCompositionGroupMapper: TextCompositionGroupMapper

    private lateinit var mockCompositionGroup: CompositionGroup

    @BeforeEach
    fun `set up`(forge: Forge) {
        textCompositionGroupMapper = TextCompositionGroupMapper(colorStringFormatter = DefaultColorStringFormatter)
        mockCompositionGroup = mockGroupWithCoordinates(forge)
    }

    @RepeatedTest(8)
    fun `M return the correct wireframe W map`() {
        val actual = textCompositionGroupMapper.map(
            compositionGroup = mockCompositionGroup,
            composeContext = fakeComposeContext,
            uiContext = fakeUiContext
        )
        val expectedText = when (fakeUiContext.privacy) {
            SessionReplayPrivacy.ALLOW -> String()
            SessionReplayPrivacy.MASK,
            SessionReplayPrivacy.MASK_USER_INPUT -> if (fakeUiContext.isInUserInputLayout) {
                FIXED_INPUT_MASK
            } else {
                ""
            }
        }
        val expectedBox = requireNotNull(Box.from(compositionGroup = mockCompositionGroup))
        val boxWithDensity = expectedBox.withDensity(fakeUiContext.density)
        val expected = MobileSegment.Wireframe.TextWireframe(
            id = mockCompositionGroup.stableId(),
            x = boxWithDensity.x,
            y = boxWithDensity.y,
            width = boxWithDensity.width,
            height = boxWithDensity.height,
            text = expectedText,
            textStyle = MobileSegment.TextStyle(
                family = DEFAULT_FONT_FAMILY,
                size = DEFAULT_FONT_SIZE,
                color = fakeUiContext.parentContentColor ?: TextCompositionGroupMapper.DEFAULT_TEXT_COLOR
            ),
            textPosition = MobileSegment.TextPosition(
                alignment = MobileSegment.Alignment(horizontal = null, vertical = null)
            )
        )
        Assertions.assertThat(actual?.wireframe).isEqualTo(expected)
    }
}
