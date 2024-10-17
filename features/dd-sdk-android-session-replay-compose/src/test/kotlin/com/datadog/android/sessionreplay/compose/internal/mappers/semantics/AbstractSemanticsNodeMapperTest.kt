/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Density
import com.datadog.android.sessionreplay.compose.internal.data.ComposeWireframe
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.test.elmyr.SessionReplayComposeForgeConfigurator
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import kotlin.math.roundToInt

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(SessionReplayComposeForgeConfigurator::class)
internal open class AbstractCompositionGroupMapperTest {

    private lateinit var testedMapper: StubAbstractSemanticsNodeMapper

    @Forgery
    private lateinit var fakeWireframe: ComposeWireframe

    @IntForgery
    var fakeSemanticsId: Int = 0

    lateinit var fakeBounds: Rect

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockLayoutInfo: LayoutInfo

    @Mock
    lateinit var mockDensity: Density

    @FloatForgery
    var fakeDensity = 0f

    @BeforeEach
    open fun `set up`(forge: Forge) {
        fakeBounds = Rect(
            left = forge.aFloat(),
            top = forge.aFloat(),
            right = forge.aFloat(),
            bottom = forge.aFloat()
        )
        testedMapper = StubAbstractSemanticsNodeMapper(mockColorStringFormatter)
    }

    @Test
    fun `M return correct bound W resolveBounds`() {
        // Given
        testedMapper.mappedWireframe = fakeWireframe
        val mockNode = mockSemanticsNodeWithBound()

        // When
        val result = testedMapper.stubResolveBounds(mockNode)

        // Then
        assertThat(result.x).isEqualTo((fakeBounds.left / fakeDensity).toLong())
        assertThat(result.y).isEqualTo((fakeBounds.top / fakeDensity).toLong())
        assertThat(result.height).isEqualTo((fakeBounds.size.height / fakeDensity).toLong())
        assertThat(result.width).isEqualTo((fakeBounds.size.width / fakeDensity).toLong())
    }

    protected fun mockSemanticsNodeWithBound(additionalMock: SemanticsNode.() -> Unit = {}): SemanticsNode {
        return mock<SemanticsNode?>().apply {
            whenever(id) doReturn fakeSemanticsId
            whenever(boundsInRoot) doReturn fakeBounds
            whenever(layoutInfo) doReturn mockLayoutInfo
            whenever(mockDensity.density) doReturn fakeDensity
            whenever(layoutInfo.density) doReturn mockDensity
            additionalMock.invoke(this)
        }
    }

    protected fun mockColorStringFormatter(color: Long, colorHexStr: String) {
        val colorPair = convertColorIntAlpha(color)
        whenever(
            mockColorStringFormatter.formatColorAndAlphaAsHexString(
                colorPair.first,
                colorPair.second
            )
        ).thenReturn(colorHexStr)
    }

    private fun convertColorIntAlpha(color: Long): Pair<Int, Int> {
        val c = Color(color)
        return Pair(c.toArgb(), (c.alpha * MAX_ALPHA).roundToInt())
    }

    companion object {
        private const val MAX_ALPHA = 255
    }
}

internal class StubAbstractSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter
) : AbstractSemanticsNodeMapper(colorStringFormatter) {

    var mappedWireframe: ComposeWireframe? = null

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe? {
        return null
    }

    fun stubResolveBounds(semanticsNode: SemanticsNode): GlobalBounds {
        return super.resolveBounds(semanticsNode)
    }

    fun covertColor(color: Long): String? {
        return super.convertColor(color)
    }
}
