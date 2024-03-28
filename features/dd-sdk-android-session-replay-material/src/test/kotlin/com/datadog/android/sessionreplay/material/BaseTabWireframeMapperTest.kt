/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.material.internal.densityNormalized
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.google.android.material.tabs.TabLayout.TabView
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal abstract class BaseTabWireframeMapperTest {

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockTextWireframeMapper: TextViewMapper

    lateinit var testedTabWireframeMapper: TabWireframeMapper

    @Mock
    lateinit var mockTabLabelView: TextView

    lateinit var mockTabView: TabView

    lateinit var fakeTextWireframes: List<MobileSegment.Wireframe.TextWireframe>

    @LongForgery
    var fakeTabIndicatorUniqueId: Long = 0

    @IntForgery(min = 0, max = 10)
    var fakePaddingStart: Int = 0

    @IntForgery(min = 0, max = 10)
    var fakePaddingEnd: Int = 0

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTextWireframes = forge.aList(size = 1) { getForgery() }
        mockTabView = forge.mockTabView()
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockTabView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeGlobalBounds)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockTabView,
                TabWireframeMapper.SELECTED_TAB_INDICATOR_KEY_NAME
            )
        )
            .thenReturn(fakeTabIndicatorUniqueId)
        whenever(mockTextWireframeMapper.map(eq(mockTabLabelView), eq(fakeMappingContext), any()))
            .thenReturn(fakeTextWireframes)
        testedTabWireframeMapper = provideTestInstance()
    }

    abstract fun provideTestInstance(): TabWireframeMapper

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has label, tab selected }`() {
        // Given
        whenever(mockTabView.isSelected).thenReturn(true)
        val density = fakeMappingContext.systemInformation.screenDensity
        val expectedTabIndicatorStartPadding = fakePaddingStart.toLong().densityNormalized(density)
        val expectedTabIndicatorEndPadding = fakePaddingEnd.toLong().densityNormalized(density)
        val expectedTabIndicatorXPos = fakeGlobalBounds.x + expectedTabIndicatorStartPadding
        val expectedTabIndicatorHeight = TabWireframeMapper.SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(density)
        val expectedTabIndicatorYPos = fakeGlobalBounds.y + fakeGlobalBounds.height -
            expectedTabIndicatorHeight
        val expectedTabIndicatorWidth = fakeGlobalBounds.width - expectedTabIndicatorStartPadding -
            expectedTabIndicatorEndPadding
        val expectedTabIndicatorColor = fakeTextWireframes.first().textStyle.color
        val expectedTabIndicatorWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTabIndicatorUniqueId,
            x = expectedTabIndicatorXPos,
            y = expectedTabIndicatorYPos,
            width = expectedTabIndicatorWidth,
            height = expectedTabIndicatorHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedTabIndicatorColor,
                opacity = mockTabView.alpha
            )
        )
        val expectedMappedWireframes = fakeTextWireframes + expectedTabIndicatorWireframe

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has label, tab not selected }`() {
        // Given
        whenever(mockTabView.isSelected).thenReturn(false)
        val expectedMappedWireframes = fakeTextWireframes

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no label, tab selected }`() {
        // Given
        for (i in 0 until mockTabView.childCount) {
            whenever(mockTabView.getChildAt(i)).thenReturn(mock())
        }
        whenever(mockTabView.isSelected).thenReturn(true)
        val density = fakeMappingContext.systemInformation.screenDensity
        val expectedTabIndicatorStartPadding = fakePaddingStart.toLong().densityNormalized(density)
        val expectedTabIndicatorEndPadding = fakePaddingEnd.toLong().densityNormalized(density)
        val expectedTabIndicatorXPos = fakeGlobalBounds.x + expectedTabIndicatorStartPadding
        val expectedTabIndicatorHeight = TabWireframeMapper.SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(density)
        val expectedTabIndicatorYPos = fakeGlobalBounds.y + fakeGlobalBounds.height -
            expectedTabIndicatorHeight
        val expectedTabIndicatorWidth = fakeGlobalBounds.width - expectedTabIndicatorStartPadding -
            expectedTabIndicatorEndPadding
        val expectedTabIndicatorColor = TabWireframeMapper.SELECTED_TAB_INDICATOR_DEFAULT_COLOR
        val expectedTabIndicatorWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTabIndicatorUniqueId,
            x = expectedTabIndicatorXPos,
            y = expectedTabIndicatorYPos,
            width = expectedTabIndicatorWidth,
            height = expectedTabIndicatorHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedTabIndicatorColor,
                opacity = mockTabView.alpha
            )
        )
        val expectedMappedWireframes = listOf(expectedTabIndicatorWireframe)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no label, tab not selected }`() {
        // Given
        for (i in 0 until mockTabView.childCount) {
            whenever(mockTabView.getChildAt(i)).thenReturn(mock())
        }
        whenever(mockTabView.isSelected).thenReturn(false)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEmpty()
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no children, tab selected }`() {
        // Given
        whenever(mockTabView.childCount).thenReturn(0)
        whenever(mockTabView.isSelected).thenReturn(true)
        val density = fakeMappingContext.systemInformation.screenDensity
        val expectedTabIndicatorStartPadding = fakePaddingStart.toLong().densityNormalized(density)
        val expectedTabIndicatorEndPadding = fakePaddingEnd.toLong().densityNormalized(density)
        val expectedTabIndicatorXPos = fakeGlobalBounds.x + expectedTabIndicatorStartPadding
        val expectedTabIndicatorHeight = TabWireframeMapper.SELECTED_TAB_INDICATOR_HEIGHT_IN_PX
            .densityNormalized(density)
        val expectedTabIndicatorYPos = fakeGlobalBounds.y + fakeGlobalBounds.height -
            expectedTabIndicatorHeight
        val expectedTabIndicatorWidth = fakeGlobalBounds.width - expectedTabIndicatorStartPadding -
            expectedTabIndicatorEndPadding
        val expectedTabIndicatorColor = TabWireframeMapper.SELECTED_TAB_INDICATOR_DEFAULT_COLOR
        val expectedTabIndicatorWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeTabIndicatorUniqueId,
            x = expectedTabIndicatorXPos,
            y = expectedTabIndicatorYPos,
            width = expectedTabIndicatorWidth,
            height = expectedTabIndicatorHeight,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = expectedTabIndicatorColor,
                opacity = mockTabView.alpha
            )
        )
        val expectedMappedWireframes = listOf(expectedTabIndicatorWireframe)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab has no children, tab not selected }`() {
        // Given
        whenever(mockTabView.childCount).thenReturn(0)
        whenever(mockTabView.isSelected).thenReturn(false)

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEmpty()
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab not selected, id generate failed }`() {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)
        whenever(mockTabView.isSelected).thenReturn(false)
        val expectedMappedWireframes = fakeTextWireframes

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab selected, id generate failed }`() {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)
        whenever(mockTabView.isSelected).thenReturn(true)
        val expectedMappedWireframes = fakeTextWireframes

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    private fun Forge.mockTabView(): TabView {
        return mock {
            whenever(it.alpha).thenReturn(aFloat(min = 0f, max = 1f))
            whenever(it.paddingStart).thenReturn(fakePaddingStart)
            whenever(it.paddingEnd).thenReturn(fakePaddingEnd)
            val fakeChildCount = anInt(min = 1, max = 10)
            whenever(it.childCount).thenReturn(fakeChildCount)
            val randIndex = anInt(min = 0, max = fakeChildCount)
            for (i in 0 until fakeChildCount) {
                if (i == randIndex) {
                    whenever(it.getChildAt(randIndex)).thenReturn(mockTabLabelView)
                } else {
                    whenever(it.getChildAt(i)).thenReturn(mock())
                }
            }
        }
    }
}
