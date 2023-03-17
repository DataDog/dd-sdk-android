/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.mapper.TextWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.google.android.material.tabs.TabLayout.TabView
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

internal abstract class BaseTabWireframeMapperTest {

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Mock
    lateinit var mockUniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Mock
    lateinit var mockTextWireframeMapper: TextWireframeMapper

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

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeTextWireframes = forge.aList(size = 1) { getForgery() }
        mockTabView = forge.mockTabView()
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockTabView,
                fakeSystemInformation.screenDensity
            )
        ).thenReturn(fakeGlobalBounds)
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockTabView,
                TabWireframeMapper.SELECTED_TAB_INDICATOR_KEY_NAME
            )
        )
            .thenReturn(fakeTabIndicatorUniqueId)
        whenever(mockTextWireframeMapper.map(mockTabLabelView, fakeSystemInformation))
            .thenReturn(fakeTextWireframes)
        testedTabWireframeMapper = provideTestInstance()
    }

    abstract fun provideTestInstance(): TabWireframeMapper

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab not selected, id generate failed }`() {
        // Given
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)
        whenever(mockTabView.isSelected).thenReturn(false)
        val expectedMappedWireframes = fakeTextWireframes

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

        // Then
        assertThat(mappedWireframes).isEqualTo(expectedMappedWireframes)
    }

    @Test
    fun `M map the Tab to a list of wireframes W map() { tab selected, id generate failed }`() {
        // Given
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)
        whenever(mockTabView.isSelected).thenReturn(true)
        val expectedMappedWireframes = fakeTextWireframes

        // When
        val mappedWireframes = testedTabWireframeMapper.map(mockTabView, fakeSystemInformation)

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
