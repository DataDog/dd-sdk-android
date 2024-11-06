/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
internal class TabSemanticsNodeMapperTest : AbstractCompositionGroupMapperTest() {

    private lateinit var testedTabSemanticsNodeMapper: TabSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsNode: SemanticsNode

    @Mock
    private lateinit var mockParentNode: SemanticsNode

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @LongForgery(min = 0xffffffff)
    var fakeBackgroundColor: Long = 0L

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBackgroundColorHexString: String

    @Forgery
    lateinit var fakeUiContext: UiContext

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockColorStringFormatter(fakeBackgroundColor, fakeBackgroundColorHexString)

        testedTabSemanticsNodeMapper = TabSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return the correct wireframe W map`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode()
        whenever(mockSemanticsUtils.resolveInnerBounds(mockSemanticsNode)) doReturn rectToBounds(
            fakeBounds,
            fakeDensity
        )
        whenever(mockSemanticsUtils.resolveBackgroundColor(mockParentNode)) doReturn fakeBackgroundColor
        whenever(mockSemanticsNode.parent).doReturn(mockParentNode)
        // When
        val actual = testedTabSemanticsNodeMapper.map(
            mockSemanticsNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        val expected = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeSemanticsId.toLong(),
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeBackgroundColorHexString
            )
        )
        assertThat(actual.wireframes).contains(expected)
    }

    private fun mockSemanticsNode(): SemanticsNode {
        return mockSemanticsNodeWithBound {
            whenever(mockSemanticsNode.layoutInfo).doReturn(mockLayoutInfo)
        }
    }
}
