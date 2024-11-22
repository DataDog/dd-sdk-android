/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.obfuscator.StringObfuscator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
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
internal class TextSemanticsNodeMapperTest : AbstractSemanticsNodeMapperTest() {

    private lateinit var testedTextSemanticsNodeMapper: StubTextSemanticsNodeMapper

    @Mock
    private lateinit var mockTextLayoutInput: TextLayoutInput

    @Mock
    private lateinit var mockTextLayoutResult: TextLayoutResult

    @Mock
    private lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeTextColorHexString: String

    @LongForgery(min = 0xffffffff)
    var fakeTextColor: Long = 0x12346778L

    @Forgery
    lateinit var fakeUiContext: UiContext

    @Forgery
    lateinit var fakeTextLayoutInfo: TextLayoutInfo

    @IntForgery(min = 0, max = 100)
    private var fakeFontSize = 0

    @BeforeEach
    override fun `set up`(forge: Forge) {
        super.`set up`(forge)
        mockColorStringFormatter(fakeTextColor, fakeTextColorHexString)

        whenever(mockTextLayoutInput.style) doReturn TextStyle(
            color = Color(fakeTextColor shr 32),
            fontFamily = FontFamily.Default,
            fontSize = fakeFontSize.sp
        )
        whenever(mockTextLayoutResult.layoutInput) doReturn mockTextLayoutInput
        testedTextSemanticsNodeMapper = StubTextSemanticsNodeMapper(
            colorStringFormatter = mockColorStringFormatter,
            semanticsUtils = mockSemanticsUtils
        )
    }

    @Test
    fun `M return the correct wireframe W map`() {
        // Given
        val mockNode = mockSemanticsNodeWithBound {}

        whenever(mockSemanticsUtils.resolveTextLayoutInfo(mockNode)) doReturn fakeTextLayoutInfo
        whenever(mockSemanticsUtils.resolveInnerBounds(mockNode)) doReturn rectToBounds(
            fakeBounds,
            fakeDensity
        )
        val actual = testedTextSemanticsNodeMapper.map(
            mockNode,
            fakeUiContext,
            mockAsyncJobStatusCallback
        )
        val expectedText = if (fakeUiContext.textAndInputPrivacy == TextAndInputPrivacy.MASK_ALL) {
            StringObfuscator.getStringObfuscator().obfuscate(fakeTextLayoutInfo.text)
        } else {
            fakeTextLayoutInfo.text
        }
        val expected = MobileSegment.Wireframe.TextWireframe(
            id = fakeSemanticsId.toLong(),
            x = (fakeBounds.left / fakeDensity).toLong(),
            y = (fakeBounds.top / fakeDensity).toLong(),
            width = (fakeBounds.size.width / fakeDensity).toLong(),
            height = (fakeBounds.size.height / fakeDensity).toLong(),
            text = expectedText,
            textStyle = testedTextSemanticsNodeMapper.stubResolveTextStyle(
                fakeUiContext,
                fakeTextLayoutInfo
            ),
            textPosition = testedTextSemanticsNodeMapper.stubResolveTextAlign(fakeTextLayoutInfo)
        )

        assertThat(actual.wireframes).contains(expected)
    }

    class StubTextSemanticsNodeMapper(
        colorStringFormatter: ColorStringFormatter,
        semanticsUtils: SemanticsUtils = SemanticsUtils()
    ) : TextSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {

        fun stubResolveTextAlign(textLayoutInfo: TextLayoutInfo): MobileSegment.TextPosition {
            return super.resolveTextAlign(textLayoutInfo = textLayoutInfo)
        }

        fun stubResolveTextStyle(
            parentContext: UiContext,
            textLayoutInfo: TextLayoutInfo?
        ): MobileSegment.TextStyle {
            return super.resolveTextStyle(parentContext, textLayoutInfo)
        }
    }
}
