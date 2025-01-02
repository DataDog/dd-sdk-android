/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.BackgroundInfo
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
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
internal class ContainerSemanticsNodeMapperTest : AbstractSemanticsNodeMapperTest() {

    private lateinit var testedContainerSemanticsNodeMapper: ContainerSemanticsNodeMapper

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @LongForgery(min = 0xffffffff)
    var fakeBackgroundColor: Long = 0L

    @FloatForgery
    var fakeCornerRadius: Float = 0f

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBackgroundColorHexString: String

    @Forgery
    lateinit var fakeUiContext: UiContext

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockColorStringFormatter(fakeBackgroundColor, fakeBackgroundColorHexString)

        testedContainerSemanticsNodeMapper = ContainerSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return the correct wireframe W map`() {
        // Given
        val mockSemanticsNode = mockSemanticsNode()
        val fakeBackgroundInfo = BackgroundInfo(
            globalBounds = rectToBounds(fakeBounds, fakeDensity),
            color = fakeBackgroundColor,
            cornerRadius = fakeCornerRadius
        )
        whenever(mockSemanticsUtils.resolveBackgroundInfo(mockSemanticsNode)) doReturn listOf(
            fakeBackgroundInfo
        )
        whenever(mockSemanticsUtils.resolveBackgroundColor(mockSemanticsNode)) doReturn fakeBackgroundColor

        // When
        val actual = testedContainerSemanticsNodeMapper.map(
            mockSemanticsNode,
            fakeUiContext,
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
                backgroundColor = fakeBackgroundColorHexString,
                cornerRadius = fakeCornerRadius
            )
        )
        assertThat(actual.wireframes).contains(expected)
        assertThat(actual.uiContext).isEqualTo(
            fakeUiContext.copy(
                parentContentColor = fakeBackgroundColorHexString
            )
        )
    }

    @Test
    fun `M pass down the override privacy W map() { privacy is overridden }`(forge: Forge) {
        // Given
        val fakeImagePrivacy = forge.aValueFrom(ImagePrivacy::class.java)
        val fakeTextInputPrivacy = forge.aValueFrom(TextAndInputPrivacy::class.java)
        val mockSemanticsNode = mockSemanticsNode()
        whenever(mockSemanticsUtils.getImagePrivacyOverride(mockSemanticsNode)) doReturn fakeImagePrivacy
        whenever(mockSemanticsUtils.getTextAndInputPrivacyOverride(mockSemanticsNode)) doReturn fakeTextInputPrivacy

        // When
        val result = testedContainerSemanticsNodeMapper.map(
            mockSemanticsNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )

        // Then
        assertThat(result.uiContext?.imagePrivacy).isEqualTo(fakeImagePrivacy)
        assertThat(result.uiContext?.textAndInputPrivacy).isEqualTo(fakeTextInputPrivacy)
    }

    private fun mockSemanticsNode(): SemanticsNode {
        return mockSemanticsNodeWithBound {}
    }
}
