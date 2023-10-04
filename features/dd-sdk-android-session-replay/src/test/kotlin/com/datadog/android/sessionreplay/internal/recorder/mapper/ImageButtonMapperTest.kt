/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.util.DisplayMetrics
import android.widget.ImageButton
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageCompression
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelperCallback
import com.datadog.android.sessionreplay.internal.utils.DrawableDimensions
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isA
import org.mockito.kotlin.isNull
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
internal class ImageButtonMapperTest {

    private lateinit var testedMapper: ImageButtonMapper

    @Mock
    lateinit var mockImageButton: ImageButton

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
    lateinit var mockBase64Serializer: Base64Serializer

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
    lateinit var mockContext: Context

    private val fakeId = Forge().aLong()

    private val fakeMimeType = Forge().aString()

    lateinit var expectedWireframe: MobileSegment.Wireframe.ImageWireframe

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockImageButton.background).thenReturn(null)

        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeId)

        whenever(mockConstantState.newDrawable(any())).thenReturn(mockDrawable)
        whenever(mockDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockImageButton.drawable).thenReturn(mockDrawable)
        whenever(mockImageButton.drawable.current).thenReturn(mockDrawable)

        whenever(mockDrawable.intrinsicWidth).thenReturn(forge.aPositiveInt())
        whenever(mockDrawable.intrinsicHeight).thenReturn(forge.aPositiveInt())

        whenever(mockWebPImageCompression.getMimeType()).thenReturn(fakeMimeType)

        whenever(mockSystemInformation.screenDensity).thenReturn(forge.aFloat())
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)

        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockImageButton.resources).thenReturn(mockResources)

        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockImageButton.context).thenReturn(mockContext)
        whenever(mockBackground.current).thenReturn(mockBackground)

        whenever(mockViewUtils.resolveViewGlobalBounds(any(), any())).thenReturn(mockGlobalBounds)

        expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeId,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageButton.width.toLong(),
            height = mockImageButton.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        whenever(mockBase64Serializer.getDrawableScaledDimensions(any(), any(), any()))
            .thenReturn(DrawableDimensions(0, 0))

        testedMapper = ImageButtonMapper(
            base64Serializer = mockBase64Serializer,
            imageWireframeHelper = mockImageWireframeHelper,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator
        )
    }

    @Test
    fun `M return foreground wireframe W map() { no background }`() {
        // Given
        whenever(mockImageButton.background).thenReturn(null)
        val fakeViewDrawable = mockDrawable.constantState?.newDrawable(mockResources)
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                eq(mockImageButton),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(fakeViewDrawable),
                isNull(),
                isNull(),
                eq(ImageWireframeHelper.DRAWABLE_CHILD_NAME),
                isA()
            )
        ).thenReturn(expectedWireframe)

        // When
        val wireframes = testedMapper.map(mockImageButton, mockMappingContext)

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
            width = mockImageButton.width.toLong(),
            height = mockImageButton.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        whenever(mockImageButton.background).thenReturn(mockBackground)
        mockCreateImageWireframe(
            expectedBackgroundWireframe,
            expectedWireframe
        )

        // When
        val wireframes = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        assertThat(wireframes.size).isEqualTo(2)
        assertThat(wireframes[0]).isEqualTo(expectedBackgroundWireframe)
        assertThat(wireframes[1]).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M call async callback W map() { }`() {
        // Given
        whenever(mockImageButton.background).thenReturn(mockBackground)

        val argumentCaptor = argumentCaptor<ImageWireframeHelperCallback>()
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                any()
            )
        ).thenReturn(expectedWireframe)

        // When
        val wireframes = testedMapper.map(mockImageButton, mockMappingContext, mockCallback)

        // Then
        assertThat(wireframes.size).isEqualTo(2)
        assertThat(wireframes[0]).isEqualTo(expectedWireframe)
        verify(mockImageWireframeHelper, times(2)).createImageWireframe(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            argumentCaptor.capture()
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
            width = mockImageButton.width.toLong(),
            height = mockImageButton.height.toLong(),
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )
        whenever(mockImageButton.background).thenReturn(mockBackground)

        mockCreateImageWireframe(
            expectedBackgroundWireframe,
            expectedWireframe
        )

        // When
        testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockImageWireframeHelper, times(2)).createImageWireframe(
            any(),
            captor.capture(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
        val allValues = captor.allValues
        assertThat(allValues[0]).isEqualTo(0)
        assertThat(allValues[1]).isEqualTo(1)
    }

    @Test
    fun `M set index to 0 W map() { no background wireframe }`() {
        // Given
        whenever(mockImageButton.background).thenReturn(mockBackground)

        mockCreateImageWireframe(
            null,
            expectedWireframe
        )

        // When
        testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockImageWireframeHelper, times(2)).createImageWireframe(
            any(),
            captor.capture(),
            any(),
            any(),
            any(),
            any(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
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
        whenever(mockImageButton.background).thenReturn(mockBackground)

        val expectedBackgroundWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageButton.width.toLong(),
            height = mockImageButton.height.toLong(),
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
        val wireframes = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        assertThat(wireframes[0]::class.java).isEqualTo(MobileSegment.Wireframe.ImageWireframe::class.java)
    }

    @Test
    fun `M return background of type ShapeWireframe W map() { has shapestyle or border }`(
        @Mock mockColorDrawable: ColorDrawable
    ) {
        // Given
        whenever(mockImageButton.background).thenReturn(mockColorDrawable)

        // When
        val wireframes = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        assertThat(wireframes[0]::class.java).isEqualTo(MobileSegment.Wireframe.ShapeWireframe::class.java)
    }

    @Test
    fun `M return no background W map() { cant resolve id for shapeDrawable }`(
        @Mock mockColorDrawable: ColorDrawable
    ) {
        // Given
        whenever(mockImageButton.background).thenReturn(mockColorDrawable)

        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        mockCreateImageWireframe(
            expectedWireframe,
            null
        )

        // When
        val wireframes = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        assertThat(wireframes.size).isEqualTo(1)
    }

    private fun mockCreateImageWireframe(
        expectedFirstWireframe: MobileSegment.Wireframe.ImageWireframe?,
        expectedSecondWireframe: MobileSegment.Wireframe.ImageWireframe?
    ) {
        whenever(
            mockImageWireframeHelper.createImageWireframe(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull(),
                anyOrNull()
            )
        )
            .thenReturn(expectedFirstWireframe)
            .thenReturn(expectedSecondWireframe)
    }
}
