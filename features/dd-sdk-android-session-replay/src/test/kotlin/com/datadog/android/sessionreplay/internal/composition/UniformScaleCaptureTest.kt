/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.TextView
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedPixelFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedTextViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.ViewRasterizer
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class UniformScaleCaptureTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("scope"))
    private val fakeGeneration = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val mockRasterizer: ViewRasterizer = mock()
    private val mockIdentifiers: ViewIdentifierResolver = mock()
    private val stubRoot: ViewGroup = mock()
    private val stubText: TextView = mock()

    @ParameterizedTest
    @EnumSource(value = TextAndInputPrivacy::class, names = ["MASK_ALL", "MASK_SENSITIVE_INPUTS"])
    fun `M preserve scaled semantic text under image masking W traverseWindow`(fakeTextPrivacy: TextAndInputPrivacy) {
        // Given
        defaults()
        whenever(stubText.getTag(R.id.datadog_text_and_input_privacy)).thenReturn(fakeTextPrivacy.name)

        // When
        val fakeCopy = capture()
        val fakeResult = checkNotNull(CapturedNativeSnapshotBuilder().buildWindow(fakeCopy.window, fakeGeneration))

        // Then
        assertThat(fakeCopy.window.screenDensity).isEqualTo(1f)
        assertThat(fakeCopy.window.views).hasSize(2)
        assertThat(fakeCopy.window.views.map { it.viewScale }).containsExactly(2f, 2f)
        assertThat(fakeCopy.window.pendingPixelCaptures).isEmpty()
        val fakeWireframe = fakeResult.wireframes.single() as CapturedWireframe.Text
        assertThat(fakeWireframe.bounds).isEqualTo(CapturedBounds(20, 40, 80, 40))
        assertThat(fakeWireframe.textStyle.size).isEqualTo(32L)
        assertThat(fakeWireframe.textPosition?.padding?.left).isEqualTo(8L)
        if (fakeTextPrivacy == TextAndInputPrivacy.MASK_ALL) {
            assertThat(fakeWireframe.text).isNotEqualTo("Hello")
        } else {
            assertThat(fakeWireframe.text).isEqualTo("Hello")
        }
        verifyNoInteractions(mockRasterizer)
    }

    @ParameterizedTest
    @CsvSource("0.5, 1", "1.5, 3")
    fun `M combine inherited and own uniform scales W traverseWindow`(fakeOwnScale: Float, fakeCombinedScale: Float) {
        // Given
        defaults()
        val fakeMatrix = matrix(fakeOwnScale)
        whenever(stubText.matrix).thenReturn(fakeMatrix)

        // When
        val fakeCopy = capture()
        val fakeResult = checkNotNull(CapturedNativeSnapshotBuilder().buildWindow(fakeCopy.window, fakeGeneration))

        // Then
        val fakeWireframe = fakeResult.wireframes.single() as CapturedWireframe.Text
        assertThat(fakeCopy.window.views.last().viewScale).isEqualTo(fakeCombinedScale)
        assertThat(fakeWireframe.bounds).isEqualTo(
            CapturedBounds(
                20,
                40,
                (40 * fakeCombinedScale).toLong(),
                (20 * fakeCombinedScale).toLong()
            )
        )
        assertThat(fakeWireframe.textStyle.size).isEqualTo((16f * fakeCombinedScale).toLong())
        assertThat(fakeCopy.window.pendingPixelCaptures).isEmpty()
        verifyNoInteractions(mockRasterizer)
    }

    @ParameterizedTest
    @CsvSource("false, 4", "true, 20")
    fun `M transform explicit and padding clips W traverseWindow`(fakePadding: Boolean, fakeLeftClip: Long) {
        // Given
        defaults()
        whenever(stubRoot.clipBounds).thenReturn(Rect(12, 0, 100, 100))
        if (fakePadding) {
            whenever(stubRoot.paddingLeft).thenReturn(20)
            whenever(stubRoot.clipToPadding).thenReturn(true)
        }

        // When
        val fakeCopy = capture()
        val fakeResult = checkNotNull(CapturedNativeSnapshotBuilder().buildWindow(fakeCopy.window, fakeGeneration))

        // Then
        assertThat(fakeCopy.window.views.first().explicitClip).isEqualTo(CapturedBounds(24, 0, 176, 200))
        assertThat(fakeResult.wireframes.single().clip).isEqualTo(CapturedClip(left = fakeLeftClip))
    }

    @Test
    fun `M keep the device density while scaling text metrics W traverseWindow`() {
        // Given
        defaults(fakeDensity = 2f)

        // When
        val fakeCopy = capture()
        val fakeResult = checkNotNull(CapturedNativeSnapshotBuilder().buildWindow(fakeCopy.window, fakeGeneration))

        // Then
        assertThat(fakeCopy.window.screenDensity).isEqualTo(2f)
        val fakeWireframe = fakeResult.wireframes.single() as CapturedWireframe.Text
        assertThat(fakeWireframe.bounds).isEqualTo(CapturedBounds(10, 20, 40, 20))
        assertThat(fakeWireframe.textStyle.size).isEqualTo(16L)
        assertThat(fakeWireframe.textPosition?.padding?.left).isEqualTo(4L)
    }

    private fun capture(): WindowCopyResult.Present {
        val fakePixelCapture = ViewPixelCapture(mock(), rasterizer = mockRasterizer)
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(
                listOf(
                    CapturedMapperTypeWrapper(
                        TextView::class.java,
                        CapturedTextViewMapper<TextView>(internalLogger = mock())
                    )
                ),
                CapturedPixelFallbackMapper(
                    CapturedViewGroupFallbackMapper(internalLogger = mock()),
                    mock(),
                    pixelCapture = fakePixelCapture
                ),
                mock()
            ),
            internalLogger = mock(),
            viewIdentifierResolver = mockIdentifiers,
            rootImagePrivacy = ImagePrivacy.MASK_ALL,
            pixelCapture = fakePixelCapture
        )
        return testedTraversal.traverseWindow(stubRoot, fakeFactory.window("window"), fakeFactory, fakeGeneration)
            as WindowCopyResult.Present
    }

    private fun defaults(fakeDensity: Float = 1f) {
        val stubResources: Resources = mock()
        whenever(stubResources.displayMetrics).thenReturn(
            DisplayMetrics().apply {
                density = fakeDensity
                widthPixels = 1000
                heightPixels = 1000
            }
        )
        for (view in listOf(stubRoot, stubText)) {
            whenever(view.resources).thenReturn(stubResources)
            whenever(view.isShown).thenReturn(true)
            whenever(view.alpha).thenReturn(1f)
            whenever(view.scaleX).thenReturn(1f)
            whenever(view.scaleY).thenReturn(1f)
        }
        whenever(stubRoot.width).thenReturn(100)
        whenever(stubRoot.height).thenReturn(100)
        val fakeMatrix = matrix(2f)
        whenever(stubRoot.matrix).thenReturn(fakeMatrix)
        whenever(stubRoot.clipChildren).thenReturn(true)
        whenever(stubRoot.willNotDraw()).thenReturn(true)
        whenever(stubRoot.childCount).thenReturn(1)
        whenever(stubRoot.getChildAt(0)).thenReturn(stubText)
        whenever(stubText.parent).thenReturn(stubRoot)
        whenever(mockIdentifiers.resolveViewId(stubText)).thenReturn(1L)
        whenever(stubText.left).thenReturn(10)
        whenever(stubText.top).thenReturn(20)
        whenever(stubText.width).thenReturn(40)
        whenever(stubText.height).thenReturn(20)
        whenever(stubText.text).thenReturn("Hello")
        whenever(stubText.currentTextColor).thenReturn(0xff112233.toInt())
        whenever(stubText.textSize).thenReturn(16f)
        whenever(stubText.paddingLeft).thenReturn(4)
        whenever(stubText.compoundDrawables).thenReturn(arrayOfNulls<Drawable>(4))
    }

    private fun matrix(scale: Float): Matrix = mock<Matrix>().also { matrix ->
        whenever(matrix.getValues(any())).thenAnswer {
            floatArrayOf(scale, 0f, 0f, 0f, scale, 0f, 0f, 0f, 1f).copyInto(it.getArgument(0))
            null
        }
    }
}
