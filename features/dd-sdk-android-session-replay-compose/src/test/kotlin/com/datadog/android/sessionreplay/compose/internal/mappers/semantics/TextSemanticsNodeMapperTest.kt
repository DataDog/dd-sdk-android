/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.mappers.TextCompositionGroupMapper.Companion.DEFAULT_FONT_FAMILY
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
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
internal class TextSemanticsNodeMapperTest : AbstractCompositionGroupMapperTest() {

    private lateinit var testedTextSemanticsNodeMapper: TextSemanticsNodeMapper

    @Mock
    private lateinit var mockSemanticsConfiguration: SemanticsConfiguration

    @Mock
    private lateinit var mockTextLayoutInput: TextLayoutInput

    @Mock
    private lateinit var mockTextLayoutResult: TextLayoutResult

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeTextColorHexString: String

    private var stubTextLayoutResultAction: ((MutableList<TextLayoutResult>) -> Boolean) = { list ->
        list.add(mockTextLayoutResult)
        true
    }

    var fakeTextAlign: TextAlign = TextAlign.Left

    @LongForgery(min = 0xffffffff)
    var fakeTextColor: Long = 0x12346778L

    @Forgery
    lateinit var fakeUiContext: UiContext

    @StringForgery
    lateinit var fakeText: String

    @IntForgery(min = 0, max = 100)
    private var fakeFontSize = 0

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        fakeTextAlign = generateFakeTextAlign(forge = forge)
        mockColorStringFormatter(fakeTextColor, fakeTextColorHexString)

        whenever(mockTextLayoutInput.style) doReturn TextStyle(
            color = Color(fakeTextColor shr 32),
            fontFamily = FontFamily.Default,
            fontSize = fakeFontSize.sp,
            textAlign = fakeTextAlign
        )
        whenever(mockTextLayoutResult.layoutInput) doReturn mockTextLayoutInput
        testedTextSemanticsNodeMapper = TextSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return the correct wireframe W map`() {
        // Given
        val map: Map<SemanticsPropertyKey<*>, Any?> = mapOf(SemanticsPropertyKey<String>(name = "Text") to fakeText)
        val mockNode = mockSemanticsNodeWithBound {
            whenever(mockSemanticsConfiguration.iterator()) doReturn map.iterator()
            whenever(mockSemanticsConfiguration.getOrNull(SemanticsActions.GetTextLayoutResult)) doReturn
                AccessibilityAction("", stubTextLayoutResultAction)
            whenever(config) doReturn mockSemanticsConfiguration
        }
        whenever(mockSemanticsUtils.resolveInnerBounds(mockNode)) doReturn rectToBounds(
            fakeBounds,
            fakeDensity
        )
        val actual = testedTextSemanticsNodeMapper.map(
            mockNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )

        val expected = MobileSegment.Wireframe.TextWireframe(
            id = fakeSemanticsId.toLong(),
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            text = fakeText,
            textStyle = MobileSegment.TextStyle(
                family = DEFAULT_FONT_FAMILY,
                size = fakeFontSize.toLong(),
                color = fakeTextColorHexString
            ),
            textPosition = MobileSegment.TextPosition(
                alignment = resolveTextAlign(fakeTextAlign)
            )
        )

        assertThat(actual.wireframes).contains(expected)
    }

    private fun resolveTextAlign(textAlign: TextAlign): MobileSegment.Alignment {
        val align = when (textAlign) {
            TextAlign.Start,
            TextAlign.Left -> MobileSegment.Horizontal.LEFT

            TextAlign.End,
            TextAlign.Right -> MobileSegment.Horizontal.RIGHT

            TextAlign.Justify,
            TextAlign.Center -> MobileSegment.Horizontal.CENTER

            else -> MobileSegment.Horizontal.CENTER
        }
        return MobileSegment.Alignment(
            horizontal = align
        )
    }

    private fun generateFakeTextAlign(forge: Forge): TextAlign {
        val index = forge.anInt(0, TextAlign.values().size)
        return TextAlign.values()[index]
    }
}
