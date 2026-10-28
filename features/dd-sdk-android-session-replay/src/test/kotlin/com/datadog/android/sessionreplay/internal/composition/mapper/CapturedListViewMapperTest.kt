/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ListView
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCapture
import com.datadog.android.sessionreplay.internal.composition.PendingPixelCaptureSink
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class CapturedListViewMapperTest {
    private val stubList: ListView = mock()
    private val mockBackgroundMapper: CapturedViewMapper<View> = mock()
    private val testedMapper = CapturedListViewMapper(mockBackgroundMapper)
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("scope"))
    private val fakeOwner = fakeFactory.view(fakeFactory.window("window"), "list")
    private val fakeContext = CapturedMappingContext(
        fakeFactory,
        fakeOwner,
        2f,
        bounds = CapturedBounds(10, 20, 100, 200),
        imagePrivacy = ImagePrivacy.MASK_ALL
    )

    @Test
    fun `M preserve native rows W list has no drawable content`() {
        whenever(mockBackgroundMapper.map(stubList, fakeContext)).thenReturn(CapturedViewMapperResult.None)

        val fakeResult = testedMapper.map(stubList, fakeContext) as CapturedViewMapperResult.Wireframes

        assertThat(fakeResult.isTerminal).isFalse()
        assertThat(fakeResult.wireframes).isEmpty()
    }

    @Test
    fun `M capture selection chrome separately W input state is visible`() {
        val stubBounds: ViewBoundsResolver = mock()
        val stubPixels: Bitmap = mock()
        val stubSelector: ListSelectorRasterizer = mock()
        whenever(stubBounds.resolveViewGlobalBounds(stubList, 2f)).thenReturn(GlobalBounds(10, 20, 100, 200))
        whenever(stubSelector.rasterize(stubList)).thenReturn(RasterizedListSelector(stubPixels, Rect(0, 0, 200, 60)))
        val fakeCaptures = mutableListOf<PendingPixelCapture>()
        val fakeVisibleContext = fakeContext.copy(
            imagePrivacy = ImagePrivacy.MASK_NONE,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            pendingPixelCaptureSink = PendingPixelCaptureSink(fakeCaptures::add)
        )
        val testedSelectorMapper = CapturedListViewMapper(mockBackgroundMapper, stubBounds, stubSelector)

        val fakeResult = testedSelectorMapper.map(stubList, fakeVisibleContext) as CapturedViewMapperResult.Wireframes

        assertThat(fakeResult.isTerminal).isFalse()
        val fakeSelection = fakeResult.wireframes.single() as CapturedWireframe.Pixel
        assertThat(fakeSelection.bounds).isEqualTo(CapturedBounds(10, 20, 100, 30))
        assertThat(fakeCaptures.single().isTextFree).isTrue()
    }

    @Test
    fun `M hide selection chrome W input privacy masks state`() {
        val mockSelector: ListSelectorRasterizer = mock()
        val testedSelectorMapper = CapturedListViewMapper(mockBackgroundMapper, selectorRasterizer = mockSelector)

        listOf(TextAndInputPrivacy.MASK_ALL, TextAndInputPrivacy.MASK_ALL_INPUTS).forEach { privacy ->
            testedSelectorMapper.map(
                stubList,
                fakeContext.copy(imagePrivacy = ImagePrivacy.MASK_NONE, textAndInputPrivacy = privacy)
            )
        }

        verifyNoInteractions(mockSelector)
    }

    @Test
    fun `M capture dividers separately W list has multiple visible rows`() {
        val stubDivider: ColorDrawable = mock()
        val stubFirst: View = mock()
        val stubSecond: View = mock()
        whenever(mockBackgroundMapper.map(stubList, fakeContext)).thenReturn(CapturedViewMapperResult.None)
        whenever(stubList.divider).thenReturn(stubDivider)
        whenever(stubDivider.color).thenReturn(0xff123456.toInt())
        whenever(stubList.dividerHeight).thenReturn(2)
        whenever(stubList.width).thenReturn(200)
        whenever(stubList.paddingLeft).thenReturn(8)
        whenever(stubList.paddingRight).thenReturn(12)
        whenever(stubList.childCount).thenReturn(2)
        whenever(stubList.count).thenReturn(2)
        whenever(stubList.getChildAt(0)).thenReturn(stubFirst)
        whenever(stubList.getChildAt(1)).thenReturn(stubSecond)
        whenever(stubFirst.bottom).thenReturn(80)

        val fakeResult = testedMapper.map(stubList, fakeContext) as CapturedViewMapperResult.Wireframes

        assertThat(fakeResult.isTerminal).isFalse()
        val fakeDivider = fakeResult.wireframes.single() as CapturedWireframe.Shape
        assertThat(fakeDivider.bounds).isEqualTo(CapturedBounds(14, 60, 90, 1))
        assertThat(fakeDivider.style?.backgroundColor).isEqualTo("#123456ff")
    }
}
