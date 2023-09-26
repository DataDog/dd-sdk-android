/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageWireframeHelper.Companion.DRAWABLE_CHILD_NAME
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ImageWireframeHelperTest {
    private lateinit var testedHelper: ImageWireframeHelper

    @Mock
    lateinit var mockBase64Serializer: Base64Serializer

    @Mock
    lateinit var mockUniqueIdentifierGenerator: UniqueIdentifierGenerator

    @Mock
    lateinit var mockImageCompression: ImageCompression

    @Mock
    lateinit var mockCallback: ImageWireframeHelperCallback

    @Mock
    lateinit var mockImageTypeResolver: ImageTypeResolver

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @Mock
    lateinit var mockTextView: TextView

    @Mock
    lateinit var mockMappingContext: MappingContext

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockBounds: GlobalBounds

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockContext: Context

    @LongForgery(min = 1)
    var fakeGeneratedIdentifier: Long = 0L

    @LongForgery(min = 1, max = 300)
    var fakeDrawableWidth: Long = 0L

    @LongForgery(min = 1, max = 300)
    var fakeDrawableHeight: Long = 0L

    private lateinit var fakeDrawableXY: Pair<Long, Long>

    @StringForgery
    var fakeMimeType: String = ""

    @IntForgery(min = 1)
    var fakePadding: Int = 0

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeScreenWidth = 1000
        val fakeScreenHeight = 1000

        val randomXLocation = forge.aLong(min = 1, max = fakeScreenWidth - fakeDrawableWidth)
        val randomYLocation = forge.aLong(min = 1, max = fakeScreenHeight - fakeDrawableHeight)
        fakeDrawableXY = Pair(randomXLocation, randomYLocation)
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSystemInformation.screenDensity).thenReturn(0f)
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(mockView, "drawable"))
            .thenReturn(fakeGeneratedIdentifier)
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth.toInt())
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight.toInt())
        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockView.resources).thenReturn(mockResources)
        whenever(mockView.context).thenReturn(mockContext)
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockImageCompression.getMimeType()).thenReturn(fakeMimeType)
        whenever(mockTextView.resources).thenReturn(mockResources)
        whenever(mockTextView.context).thenReturn(mockContext)
        whenever(mockViewUtilsInternal.resolveDrawableBounds(any(), any(), any()))
            .thenReturn(mockBounds)
        whenever(mockTextView.width).thenReturn(fakeDrawableWidth.toInt())
        whenever(mockTextView.height).thenReturn(fakeDrawableHeight.toInt())
        whenever(mockTextView.paddingStart).thenReturn(fakePadding)
        whenever(mockTextView.paddingEnd).thenReturn(fakePadding)
        whenever(mockTextView.paddingTop).thenReturn(fakePadding)
        whenever(mockTextView.paddingBottom).thenReturn(fakePadding)
        whenever(
            mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(
                mockTextView,
                DRAWABLE_CHILD_NAME + 1
            )
        ).thenReturn(fakeGeneratedIdentifier)
        whenever(mockBounds.width).thenReturn(fakeDrawableWidth)
        whenever(mockBounds.height).thenReturn(fakeDrawableHeight)
        whenever(mockBounds.x).thenReturn(0L)
        whenever(mockBounds.y).thenReturn(0L)

        testedHelper = ImageWireframeHelper(
            imageCompression = mockImageCompression,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator,
            base64Serializer = mockBase64Serializer,
            viewUtilsInternal = mockViewUtilsInternal,
            imageTypeResolver = mockImageTypeResolver
        )
    }

    // region createImageWireframe

    @Test
    fun `M return null W createImageWireframe() { drawable is null }`() {
        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            callback = mockCallback
        )

        // Then
        assertThat(wireframe).isNull()
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M return null W createImageWireframe() { id is null }`() {
        // Given
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            callback = mockCallback
        )

        // Then
        assertThat(wireframe).isNull()
        verifyNoInteractions(mockCallback)
    }

    @Test
    fun `M return null W createImageWireframe() { drawable has no intrinsic width }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(0)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            callback = mockCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return null W createImageWireframe() { drawable has no intrinsic height }`() {
        // Given
        whenever(mockDrawable.intrinsicHeight).thenReturn(0)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            callback = mockCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return wireframe W createImageWireframe()`(
        @Mock mockShapeStyle: MobileSegment.ShapeStyle,
        @Mock mockBorder: MobileSegment.ShapeBorder
    ) {
        // Given
        whenever(
            mockUniqueIdentifierGenerator
                .resolveChildUniqueIdentifier(any(), any())
        )
            .thenReturn(fakeGeneratedIdentifier)

        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeDrawableWidth,
            height = fakeDrawableHeight,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeDrawableWidth,
            height = fakeDrawableHeight,
            drawable = mockDrawable,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            callback = mockCallback
        )

        // Then
        val argumentCaptor = argumentCaptor<Base64SerializerCallback>()
        verify(mockBase64Serializer).handleBitmap(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onReady()
        }
        verify(mockCallback).onStart()
        verify(mockCallback).onFinished()
        verifyNoMoreInteractions(mockCallback)
        assertThat(wireframe).isEqualTo(expectedWireframe)
    }

    // endregion

    // region createCompoundDrawableWireframes

    @Test
    fun `M return empty list W createCompoundDrawableWireframes() { no compound drawables }`() {
        // Given
        whenever(mockTextView.compoundDrawables)
            .thenReturn(arrayOf(null, null, null, null))

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            mockTextView,
            mockMappingContext,
            0,
            callback = mockCallback
        )

        // Then
        verifyNoInteractions(mockCallback)
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M return wireframe W createCompoundDrawableWireframes()`() {
        // Given
        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                any(),
                any(),
                any(),
                any()
            )
        ).thenReturn(mockBounds)
        val fakeDrawables = arrayOf(null, mockDrawable, null, null)
        whenever(mockTextView.compoundDrawables)
            .thenReturn(fakeDrawables)

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            mockTextView,
            mockMappingContext,
            0,
            callback = mockCallback
        )
        wireframes[0] as MobileSegment.Wireframe.ImageWireframe

        // Then
        val argumentCaptor = argumentCaptor<Base64SerializerCallback>()
        verify(mockBase64Serializer).handleBitmap(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onReady()
        }
        verify(mockCallback).onStart()
        verify(mockCallback).onFinished()
        assertThat(wireframes.size).isEqualTo(1)
    }

    @Test
    fun `M return multiple wireframes W createCompoundDrawableWireframes() { multiple drawables }`() {
        // Given
        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                any(),
                any(),
                any(),
                any()
            )
        )
            .thenReturn(mockBounds)
        val fakeDrawables = arrayOf(null, mockDrawable, null, mockDrawable)
        whenever(mockTextView.compoundDrawables)
            .thenReturn(fakeDrawables)

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            mockTextView,
            mockMappingContext,
            0,
            callback = mockCallback
        )
        wireframes[0] as MobileSegment.Wireframe.ImageWireframe

        // Then
        val argumentCaptor = argumentCaptor<Base64SerializerCallback>()
        verify(mockBase64Serializer, times(2)).handleBitmap(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onReady()
        }
        verify(mockCallback, times(2)).onStart()
        verify(mockCallback, times(2)).onFinished()
        assertThat(wireframes.size).isEqualTo(2)
    }

    @Test
    fun `M skip invalid elements W createCompoundDrawableWireframes() { invalid indices }`() {
        // Given
        whenever(mockTextView.compoundDrawables)
            .thenReturn(arrayOf(null, null, null, null, null, null))

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            mockTextView,
            mockMappingContext,
            0,
            callback = mockCallback
        )

        // Then
        verifyNoInteractions(mockCallback)
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M resolve view width and height W createImageWireframe() { RippleDrawable }`(
        @Mock mockDrawable: RippleDrawable,
        @Mock mockInsetDrawable: InsetDrawable,
        @Mock mockGradientDrawable: GradientDrawable,
        @IntForgery(min = 1) fakeViewWidth: Int,
        @IntForgery(min = 1) fakeViewHeight: Int
    ) {
        // Given
        whenever(mockView.width).thenReturn(fakeViewWidth)
        whenever(mockView.height).thenReturn(fakeViewHeight)
        whenever(mockDrawable.numberOfLayers).thenReturn(1)
        whenever(mockDrawable.getDrawable(0)).thenReturn(mockInsetDrawable)
        whenever(mockInsetDrawable.drawable).thenReturn(mockGradientDrawable)

        // When
        testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockBase64Serializer).handleBitmap(
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = captor.capture(),
            drawableHeight = captor.capture(),
            imageWireframe = any(),
            callback = any()
        )
        assertThat(captor.allValues).containsExactly(fakeViewWidth, fakeViewHeight)
    }

    @Test
    fun `M resolve drawable width and height W createImageWireframe() { TextView }`() {
        // When
        testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockBase64Serializer).handleBitmap(
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = captor.capture(),
            drawableHeight = captor.capture(),
            imageWireframe = any(),
            callback = any()

        )
        assertThat(captor.allValues).containsExactly(fakeDrawableWidth.toInt(), fakeDrawableHeight.toInt())
    }

    @Test
    fun `M not try to resolve bitmap W createImageWireframe() { PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any(), any())).thenReturn(true)

        // When
        testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        verify(mockBase64Serializer, never()).handleBitmap(
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            imageWireframe = any(),
            callback = any()
        )
    }

    @Test
    fun `M try to resolve bitmap W createImageWireframe() { non-PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any(), any())).thenReturn(false)

        // When
        testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        verify(mockBase64Serializer, atLeastOnce()).handleBitmap(
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            imageWireframe = any(),
            callback = any()
        )
    }

    @Test
    fun `M return content placeholder W createImageWireframe() { PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any(), any())).thenReturn(true)

        // When
        val actualWireframe = testedHelper.createImageWireframe(
            view = mockView,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        assertThat(actualWireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
        assertThat((actualWireframe as MobileSegment.Wireframe.PlaceholderWireframe).label)
            .isEqualTo(ImageWireframeHelper.PLACEHOLDER_CONTENT_LABEL)
    }

    // endregion
}
