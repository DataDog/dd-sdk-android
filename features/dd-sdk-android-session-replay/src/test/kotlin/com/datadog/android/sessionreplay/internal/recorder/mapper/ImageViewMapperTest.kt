/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.util.DisplayMetrics
import android.widget.ImageView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageCompression
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.resources.ImageWireframeHelperCallback
import com.datadog.android.sessionreplay.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ImageViewMapperTest {
    private lateinit var testedMapper: ImageViewMapper

    @Mock
    lateinit var mockImageView: ImageView

    @Mock
    lateinit var stubImageViewUtils: ImageViewUtils

    @Mock
    lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @Mock
    lateinit var mockMappingContext: MappingContext

    @Mock
    lateinit var mockWebPImageCompression: ImageCompression

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockUniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Mock
    lateinit var mockGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockBackground: Drawable

    @Mock
    lateinit var mockConstantState: ConstantState

    @Mock
    lateinit var stubClipping: MobileSegment.WireframeClip

    @Mock
    lateinit var stubParentRect: Rect

    @Mock
    lateinit var stubContentRect: Rect

    @Mock
    lateinit var mockContext: Context

    private val fakeId = Forge().aLong()

    private val fakeMimeType = Forge().aString()

    private lateinit var expectedWireframe: MobileSegment.Wireframe.ImageWireframe

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockImageView.background).thenReturn(null)

        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeId)

        whenever(mockConstantState.newDrawable(any())).thenReturn(mockDrawable)
        whenever(mockDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockImageView.drawable).thenReturn(mockDrawable)
        whenever(mockImageView.drawable.current).thenReturn(mockDrawable)

        whenever(mockDrawable.intrinsicWidth).thenReturn(forge.aPositiveInt())
        whenever(mockDrawable.intrinsicHeight).thenReturn(forge.aPositiveInt())

        whenever(mockWebPImageCompression.getMimeType()).thenReturn(fakeMimeType)

        whenever(mockSystemInformation.screenDensity).thenReturn(forge.aFloat())
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)

        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockImageView.resources).thenReturn(mockResources)
        mockDisplayMetrics.density = 1f

        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockImageView.context).thenReturn(mockContext)
        whenever(mockBackground.current).thenReturn(mockBackground)

        whenever(stubImageViewUtils.resolveParentRectAbsPosition(any())).thenReturn(stubParentRect)
        whenever(stubImageViewUtils.resolveContentRectWithScaling(any(), any())).thenReturn(stubContentRect)
        whenever(stubImageViewUtils.calculateClipping(any(), any(), any())).thenReturn(stubClipping)
        stubContentRect.left = forge.aPositiveInt()
        stubContentRect.top = forge.aPositiveInt()
        whenever(stubContentRect.width()).thenReturn(forge.aPositiveInt())
        whenever(stubContentRect.height()).thenReturn(forge.aPositiveInt())

        whenever(mockViewUtils.resolveViewGlobalBounds(any(), any())).thenReturn(mockGlobalBounds)

        expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeId,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageView.width.toLong(),
            height = mockImageView.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        testedMapper = ImageViewMapper(
            imageWireframeHelper = mockImageWireframeHelper,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator,
            imageViewUtils = stubImageViewUtils
        )
    }

    @Test
    fun `M return foreground wireframe W map() { no background }`() {
        // Given
        whenever(mockImageView.background).thenReturn(null)
        mockCreateImageWireframe(null, expectedWireframe)

        // When
        val wireframes = testedMapper.map(mockImageView, mockMappingContext)

        // Then
        assertThat(wireframes.size).isEqualTo(1)
        assertThat(wireframes[0]).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve background images W map() { with background }`(
        @LongForgery id: Long
    ) {
        // Given
        val expectedBackgroundWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageView.width.toLong(),
            height = mockImageView.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        whenever(mockImageView.background).thenReturn(mockBackground)
        mockCreateImageWireframe(expectedBackgroundWireframe, expectedWireframe)

        // When
        val wireframes = testedMapper.map(mockImageView, mockMappingContext)

        // Then
        assertThat(wireframes.size).isEqualTo(2)
        assertThat(wireframes[0]).isEqualTo(expectedBackgroundWireframe)
        assertThat(wireframes[1]).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M call async callback W map() { }`() {
        // Given
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                view = any(),
                currentWireframeIndex = any(),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = any(),
                drawable = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                clipping = anyOrNull(),
                prefix = anyOrNull(),
                imageWireframeHelperCallback = anyOrNull()
            )
        ).thenReturn(expectedWireframe)

        // When
        val wireframes = testedMapper.map(mockImageView, mockMappingContext, mockCallback)

        // Then
        assertThat(wireframes.size).isEqualTo(2)
        assertThat(wireframes[0]).isEqualTo(expectedWireframe)

        val argumentCaptor = argumentCaptor<ImageWireframeHelperCallback>()
        verify(mockImageWireframeHelper, times(2))
            .createImageWireframe(
                view = any(),
                currentWireframeIndex = any(),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = any(),
                drawable = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                clipping = anyOrNull(),
                imageWireframeHelperCallback = argumentCaptor.capture(),
                prefix = anyOrNull()
            )

        argumentCaptor.allValues.forEach {
            it.onStart()
            it.onFinished()
        }
        verify(mockCallback, times(2)).jobFinished()
        verify(mockCallback, times(2)).jobStarted()
        verifyNoMoreInteractions(mockCallback)
    }

    @Test
    fun `M set index to 1 W map() { has background wireframe }`(
        @LongForgery id: Long
    ) {
        // Given
        val expectedBackgroundWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageView.width.toLong(),
            height = mockImageView.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )
        whenever(mockImageView.background).thenReturn(mockBackground)

        mockCreateImageWireframe(
            expectedBackgroundWireframe,
            expectedWireframe
        )

        // When
        testedMapper.map(mockImageView, mockMappingContext)

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockImageWireframeHelper, times(2)).createImageWireframe(
            view = any(),
            currentWireframeIndex = captor.capture(),
            x = any(),
            y = any(),
            width = any(),
            height = any(),
            usePIIPlaceholder = any(),
            drawable = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull(),
            clipping = anyOrNull(),
            prefix = anyOrNull(),
            imageWireframeHelperCallback = anyOrNull()
        )
        val allValues = captor.allValues
        assertThat(allValues[0]).isEqualTo(0)
        assertThat(allValues[1]).isEqualTo(1)
    }

    @Test
    fun `M set index to 0 W map() { no background wireframe }`() {
        // Given
        whenever(mockImageView.background).thenReturn(mockBackground)

        mockCreateImageWireframe(
            null,
            expectedWireframe
        )

        // When
        testedMapper.map(mockImageView, mockMappingContext)

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockImageWireframeHelper, times(2)).createImageWireframe(
            view = any(),
            currentWireframeIndex = captor.capture(),
            x = any(),
            y = any(),
            width = any(),
            height = any(),
            usePIIPlaceholder = any(),
            drawable = anyOrNull(),
            shapeStyle = anyOrNull(),
            border = anyOrNull(),
            clipping = anyOrNull(),
            prefix = anyOrNull(),
            imageWireframeHelperCallback = anyOrNull()
        )
        val allValues = captor.allValues
        assertThat(allValues[0]).isEqualTo(0)
        assertThat(allValues[1]).isEqualTo(0)
    }

    @Test
    fun `M return background of type ImageWireframe W map() { no shapestyle or border }`(
        @LongForgery id: Long
    ) {
        // Given
        whenever(mockImageView.background).thenReturn(mockBackground)

        val expectedBackgroundWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageView.width.toLong(),
            height = mockImageView.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        mockCreateImageWireframe(
            expectedBackgroundWireframe,
            expectedWireframe
        )

        // When
        val wireframes = testedMapper.map(mockImageView, mockMappingContext)

        // Then
        assertThat(wireframes[0]::class.java).isEqualTo(MobileSegment.Wireframe.ImageWireframe::class.java)
    }

    @Test
    fun `M return background of type ShapeWireframe W map() { has shapestyle or border }`(
        @Mock mockColorDrawable: ColorDrawable
    ) {
        // Given
        whenever(mockImageView.background).thenReturn(mockColorDrawable)

        // When
        val wireframes = testedMapper.map(mockImageView, mockMappingContext)

        // Then
        assertThat(wireframes[0]::class.java).isEqualTo(MobileSegment.Wireframe.ShapeWireframe::class.java)
    }

    @Test
    fun `M return no background W map() { cant resolve id for shapeDrawable }`(
        @Mock mockColorDrawable: ColorDrawable
    ) {
        // Given
        whenever(mockImageView.background).thenReturn(mockColorDrawable)

        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        mockCreateImageWireframe(
            expectedWireframe,
            null
        )

        // When
        val wireframes = testedMapper.map(mockImageView, mockMappingContext)

        // Then
        assertThat(wireframes.size).isEqualTo(1)
    }

    private fun mockCreateImageWireframe(
        expectedFirstWireframe: MobileSegment.Wireframe.ImageWireframe?,
        expectedSecondWireframe: MobileSegment.Wireframe.ImageWireframe?
    ) {
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                view = any(),
                currentWireframeIndex = any(),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = any(),
                drawable = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                clipping = anyOrNull(),
                prefix = anyOrNull(),
                imageWireframeHelperCallback = anyOrNull()
            )
        )
            .thenReturn(expectedFirstWireframe)
            .thenReturn(expectedSecondWireframe)
    }
}
