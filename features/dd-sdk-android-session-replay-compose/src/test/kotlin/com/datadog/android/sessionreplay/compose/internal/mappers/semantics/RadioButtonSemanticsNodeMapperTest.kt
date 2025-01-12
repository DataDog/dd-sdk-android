/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.mappers.semantics.RadioButtonSemanticsNodeMapper.Companion.DEFAULT_COLOR_GRAY
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal class RadioButtonSemanticsNodeMapperTest : AbstractSemanticsNodeMapperTest() {

    private lateinit var testedRadioButtonSemanticsNodeMapper: RadioButtonSemanticsNodeMapper
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    private lateinit var mockSemanticsConfig: SemanticsConfiguration

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @LongForgery(min = 0xffffffff)
    var fakeBackgroundColor: Long = 0L

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBackgroundColorHexString: String

    @Mock
    lateinit var mockUiContext: UiContext

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockSemanticsNode = mockSemanticsNode()

        whenever(mockUiContext.textAndInputPrivacy) doReturn TextAndInputPrivacy.MASK_SENSITIVE_INPUTS
        mockColorStringFormatter(fakeBackgroundColor, fakeBackgroundColorHexString)
        whenever(mockSemanticsUtils.resolveRadioButtonColor(mockSemanticsNode))
            .doReturn(fakeBackgroundColor)

        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn rectToBounds(
            fakeBounds,
            fakeDensity
        )

        testedRadioButtonSemanticsNodeMapper = RadioButtonSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return the box wireframe W map {selected = false}`() {
        // Given
        whenever(mockSemanticsNode.config) doReturn mockSemanticsConfig
        whenever(mockSemanticsConfig.getOrNull(SemanticsProperties.Selected)) doReturn false

        // When
        val actual = testedRadioButtonSemanticsNodeMapper.map(
            mockSemanticsNode,
            mockUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val expected = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeSemanticsId.toLong() shl 32,
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                cornerRadius = (fakeBounds.size.width / fakeDensity).toLong() / 2
            ),
            border = MobileSegment.ShapeBorder(
                color = fakeBackgroundColorHexString,
                width = BOX_BORDER_WIDTH
            )
        )
        assertThat(actual.wireframes).containsExactly(expected)
    }

    @Test
    fun `M return the box wireframe W map {selected = true}`() {
        // Given
        whenever(mockSemanticsNode.config) doReturn mockSemanticsConfig
        whenever(mockSemanticsConfig.getOrNull(SemanticsProperties.Selected)) doReturn true

        // When
        val actual = testedRadioButtonSemanticsNodeMapper.map(
            mockSemanticsNode,
            mockUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val boxFrame = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32),
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                cornerRadius = (fakeBounds.size.width / fakeDensity).toLong() / 2
            ),
            border = MobileSegment.ShapeBorder(
                color = fakeBackgroundColorHexString,
                width = BOX_BORDER_WIDTH
            )
        )

        val dotFrame = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32) + 1,
            x = (fakeBounds.left / fakeDensity).toLong() + DOT_PADDING_DP,
            y = (fakeBounds.top / fakeDensity).toLong() + DOT_PADDING_DP,
            width = (fakeBounds.size.width / fakeDensity).toLong() - 2 * DOT_PADDING_DP,
            height = (fakeBounds.size.height / fakeDensity).toLong() - 2 * DOT_PADDING_DP,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeBackgroundColorHexString,
                cornerRadius = ((fakeBounds.size.width / fakeDensity).toLong() - 2 * DOT_PADDING_DP) / 2
            )
        )
        assertThat(actual.wireframes).containsAll(listOf(boxFrame, dotFrame))
    }

    @Test
    fun `M return the box wireframe W map { masked }`() {
        // Given
        whenever(mockUiContext.textAndInputPrivacy) doReturn TextAndInputPrivacy.MASK_ALL_INPUTS

        // When
        val actual = testedRadioButtonSemanticsNodeMapper.map(
            mockSemanticsNode,
            mockUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val expected = MobileSegment.Wireframe.ShapeWireframe(
            id = (fakeSemanticsId.toLong() shl 32),
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                cornerRadius = (fakeBounds.size.width / fakeDensity).toLong() / 2
            ),
            border = MobileSegment.ShapeBorder(
                color = DEFAULT_COLOR_GRAY,
                width = BOX_BORDER_WIDTH
            )
        )
        assertThat(actual.wireframes).containsExactly(expected)
    }

    private fun mockSemanticsNode(): SemanticsNode {
        return mockSemanticsNodeWithBound {}
    }

    companion object {
        private const val DOT_PADDING_DP = 4
        private const val BOX_BORDER_WIDTH = 1L
    }
}
