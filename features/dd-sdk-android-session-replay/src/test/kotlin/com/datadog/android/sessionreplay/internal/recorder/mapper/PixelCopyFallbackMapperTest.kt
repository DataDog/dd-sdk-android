/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.Rect
import android.view.View
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.aMockView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.PixelCropCallback
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class PixelCopyFallbackMapperTest : LegacyBaseWireframeMapperTest() {

    @Mock
    lateinit var mockFallbackMapper: ViewWireframeMapper

    @Mock
    lateinit var mockPixelCropCallback: PixelCropCallback

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    private lateinit var fakeFallbackWireframes: List<MobileSegment.Wireframe>

    private lateinit var mockView: View

    private lateinit var testedMapper: PixelCopyFallbackMapper

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMappingContext = fakeMappingContext.copy(
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            pixelCropCallback = mockPixelCropCallback
        )

        mockView = forge.aMockView()
        // fully on-screen: getGlobalVisibleRect fills in a rect matching the view's own size
        doAnswer {
            val rect = it.arguments[0] as Rect
            rect.set(0, 0, mockView.width, mockView.height)
            true
        }.whenever(mockView).getGlobalVisibleRect(any())
        whenever(mockView.getLocationInWindow(any())).then { }

        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeMappingContext.systemInformation.screenDensity)
        )
            .thenReturn(fakeGlobalBounds)

        fakeFallbackWireframes = forge.aList { getForgery<MobileSegment.Wireframe.ShapeWireframe>() }
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        testedMapper = PixelCopyFallbackMapper(
            fallbackMapper = mockFallbackMapper,
            viewIdentifierResolver = mockViewIdentifierResolver,
            colorStringFormatter = mockColorStringFormatter,
            viewBoundsResolver = mockViewBoundsResolver,
            drawableToColorMapper = mockDrawableToColorMapper
        )
    }

    @Test
    fun `M delegate to fallbackMapper W map() {no pixelCropCallback}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(pixelCropCallback = null)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCropCallback)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {textAndInputPrivacy stricter than MASK_SENSITIVE_INPUTS}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCropCallback)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {imagePrivacy is MASK_ALL}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(imagePrivacy = ImagePrivacy.MASK_ALL)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCropCallback)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {view not fully visible}`() {
        // Given
        doAnswer {
            val rect = it.arguments[0] as Rect
            rect.set(0, 0, mockView.width - 1, mockView.height)
            true
        }.whenever(mockView).getGlobalVisibleRect(any())

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCropCallback)
    }

    @Test
    fun `M delegate to fallbackMapper W map() {imagePrivacy is MASK_LARGE_ONLY, view too large}`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY)
        val fakeLargeBounds = fakeGlobalBounds.copy(
            width = com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP.toLong(),
            height = com.datadog.android.sessionreplay.IMAGE_DIMEN_CONSIDERED_PII_IN_DP.toLong()
        )
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeMappingContext.systemInformation.screenDensity)
        )
            .thenReturn(fakeLargeBounds)
        whenever(
            mockFallbackMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        ).thenReturn(fakeFallbackWireframes)

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).isEqualTo(fakeFallbackWireframes)
        verifyNoInteractions(mockPixelCropCallback)
    }

    @Test
    fun `M register a pending crop W map() {eligible}`() {
        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).hasSize(1)
        val wireframe = result.first() as MobileSegment.Wireframe.ImageWireframe
        assertThat(wireframe.x).isEqualTo(fakeGlobalBounds.x)
        assertThat(wireframe.y).isEqualTo(fakeGlobalBounds.y)
        assertThat(wireframe.width).isEqualTo(fakeGlobalBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeGlobalBounds.height)
        assertThat(wireframe.isEmpty).isTrue()

        val expectedIsolationClipRect = Rect(0, 0, mockView.width, mockView.height)
        verify(mockPixelCropCallback).registerPendingCrop(
            nodeId = eq(wireframe.id),
            windowRect = any(),
            dpBounds = eq(fakeGlobalBounds),
            isolationView = eq(mockView),
            isolationClipRect = eq(expectedIsolationClipRect),
            wireframe = eq(wireframe),
            wireframeSlot = any(),
            asyncJobStatusCallback = eq(mockAsyncJobStatusCallback)
        )
    }

    @Test
    fun `M swap the returned wireframe W map() {eligible} and wireframeSlot replace() is called`() {
        // Given
        var capturedSlot: WireframeSlot? = null
        doAnswer {
            capturedSlot = it.getArgument(6)
            Unit
        }.whenever(mockPixelCropCallback).registerPendingCrop(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )

        // When
        val result = testedMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)
        val placeholder = MobileSegment.Wireframe.PlaceholderWireframe(
            id = (result.first() as MobileSegment.Wireframe.ImageWireframe).id,
            x = fakeGlobalBounds.x,
            y = fakeGlobalBounds.y,
            width = fakeGlobalBounds.width,
            height = fakeGlobalBounds.height,
            label = "Content"
        )
        capturedSlot?.replace(placeholder)

        // Then — the slot writes back into the exact same list the mapper returned
        assertThat(result).containsExactly(placeholder)
    }
}
