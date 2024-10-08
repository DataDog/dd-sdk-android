/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.DisplayMetrics
import android.view.View
import android.widget.TextView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper.Companion.APPLICATION_CONTEXT_NULL_ERROR
import com.datadog.android.sessionreplay.internal.recorder.resources.DefaultImageWireframeHelper.Companion.RESOURCES_NULL_ERROR
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper.Companion.DRAWABLE_CHILD_NAME
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import com.datadog.android.utils.isCloseTo
import com.datadog.android.utils.verifyLog
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultImageWireframeHelperTest {
    private lateinit var testedHelper: ImageWireframeHelper

    @Mock
    lateinit var mockResourceResolver: ResourceResolver

    @Mock
    lateinit var mockLogger: InternalLogger

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockImageTypeResolver: ImageTypeResolver

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @Mock
    lateinit var mockTextView: TextView

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock // TODO RUM-000 use forgery instead of mock !
    lateinit var mockMappingContext: MappingContext

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockDrawable: Drawable

    @Mock // TODO RUM-000 use forgery instead of mock !
    lateinit var mockBounds: GlobalBounds

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockContext: Context

    @LongForgery(min = 1)
    var fakeGeneratedIdentifier: Long = 0L

    @IntForgery(min = 1, max = 300)
    var fakeDrawableWidth: Int = 0

    @IntForgery(min = 1, max = 300)
    var fakeDrawableHeight: Int = 0

    private lateinit var fakeDrawableXY: Pair<Long, Long>

    @IntForgery(min = 1)
    var fakePadding: Int = 0

    @StringForgery
    lateinit var fakeResourceId: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeScreenWidth = 1000
        val fakeScreenHeight = 1000

        val randomXLocation = forge.aLong(min = 1, max = (fakeScreenWidth - fakeDrawableWidth).toLong())
        val randomYLocation = forge.aLong(min = 1, max = (fakeScreenHeight - fakeDrawableHeight).toLong())
        fakeDrawableXY = Pair(randomXLocation, randomYLocation)
        whenever(mockMappingContext.imagePrivacy).thenReturn(ImagePrivacy.MASK_LARGE_ONLY)
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSystemInformation.screenDensity).thenReturn(0f)
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(mockView, "drawable"))
            .thenReturn(fakeGeneratedIdentifier)
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth)
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight)
        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockView.resources).thenReturn(mockResources)
        whenever(mockView.context).thenReturn(mockContext)
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockTextView.resources).thenReturn(mockResources)
        whenever(mockTextView.context).thenReturn(mockContext)
        whenever(mockViewUtilsInternal.resolveDrawableBounds(any(), any(), any()))
            .thenReturn(mockBounds)
        whenever(mockTextView.width).thenReturn(fakeDrawableWidth)
        whenever(mockTextView.height).thenReturn(fakeDrawableHeight)
        whenever(mockTextView.paddingStart).thenReturn(fakePadding)
        whenever(mockTextView.paddingEnd).thenReturn(fakePadding)
        whenever(mockTextView.paddingTop).thenReturn(fakePadding)
        whenever(mockTextView.paddingBottom).thenReturn(fakePadding)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockTextView,
                DRAWABLE_CHILD_NAME + 1
            )
        ).thenReturn(fakeGeneratedIdentifier)
        whenever(mockBounds.width).thenReturn(fakeDrawableWidth.toLong())
        whenever(mockBounds.height).thenReturn(fakeDrawableHeight.toLong())
        whenever(mockBounds.x).thenReturn(0L)
        whenever(mockBounds.y).thenReturn(0L)

        testedHelper = DefaultImageWireframeHelper(
            logger = mockLogger,
            resourceResolver = mockResourceResolver,
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewUtilsInternal = mockViewUtilsInternal,
            imageTypeResolver = mockImageTypeResolver
        )
    }

    // region createImageWireframe

    @Test
    fun `M return content placeholder W createImageWireframe() { ImagePrivacy MASK_ALL }`() {
        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M return image wireframe W createImageWireframe() { ImagePrivacy MASK_NONE }`() {
        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)
    }

    @Test
    fun `M return null W createImageWireframe() { application context is null }`() {
        // Given
        whenever(mockView.context.applicationContext).thenReturn(null)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M send telemetry W createImageWireframe() { application context is null }`() {
        // Given
        whenever(mockView.context.applicationContext).thenReturn(null)

        // When
        testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            APPLICATION_CONTEXT_NULL_ERROR.format(Locale.US, "android.view.View")
        )
    }

    @Test
    fun `M log error W createImageWireframe() { resources is null }`() {
        // Given
        whenever(mockView.resources).thenReturn(null)

        // When
        testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        mockLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            RESOURCES_NULL_ERROR.format(Locale.US, "android.view.View")
        )
    }

    @Test
    fun `M return null W createImageWireframe() { id is null }`() {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
        verifyNoInteractions(mockAsyncJobStatusCallback)
    }

    @Test
    fun `M return null W createImageWireframe() { drawable has no width }`() {
        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return null W createImageWireframe() { drawable has no height }`() {
        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return wireframe W createImageWireframe()`(
        @Mock mockShapeStyle: MobileSegment.ShapeStyle,
        @Mock mockBorder: MobileSegment.ShapeBorder,
        @Mock stubWireframeClip: MobileSegment.WireframeClip
    ) {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeGeneratedIdentifier)
        whenever(
            mockResourceResolver.resolveResourceId(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        ).thenAnswer {
            val callback = it.arguments[6] as ResourceResolverCallback
            callback.onSuccess(fakeResourceId)
        }

        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeDrawableWidth.toLong(),
            height = fakeDrawableHeight.toLong(),
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            resourceId = fakeResourceId,
            clip = stubWireframeClip,
            isEmpty = false
        )

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeDrawableWidth,
            height = fakeDrawableHeight,
            drawable = mockDrawable,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            asyncJobStatusCallback = mockAsyncJobStatusCallback,
            usePIIPlaceholder = true,
            clipping = stubWireframeClip
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            resourceResolverCallback = any()
        )
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
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
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verifyNoInteractions(mockAsyncJobStatusCallback)
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
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )
        wireframes[0] as MobileSegment.Wireframe.ImageWireframe

        // Then
        val argumentCaptor = argumentCaptor<ResourceResolverCallback>()

        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            resourceResolverCallback = argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onSuccess(fakeResourceId)
        }
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
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
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )
        wireframes[0] as MobileSegment.Wireframe.ImageWireframe

        // Then
        val argumentCaptor = argumentCaptor<ResourceResolverCallback>()
        verify(mockResourceResolver, times(2)).resolveResourceId(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onSuccess(fakeResourceId)
        }
        verify(mockAsyncJobStatusCallback, times(2)).jobStarted()
        verify(mockAsyncJobStatusCallback, times(2)).jobFinished()
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
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verifyNoInteractions(mockAsyncJobStatusCallback)
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
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = fakeViewWidth,
            height = fakeViewHeight,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = captor.capture(),
            drawableHeight = captor.capture(),
            resourceResolverCallback = any()
        )
        assertThat(captor.allValues).containsExactly(fakeViewWidth, fakeViewHeight)
    }

    @Test
    fun `M resolve drawable width and height W createImageWireframe() { TextView }`() {
        // When
        testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = fakeDrawableWidth,
            height = fakeDrawableHeight,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = captor.capture(),
            drawableHeight = captor.capture(),
            resourceResolverCallback = any()

        )
        assertThat(captor.allValues).containsExactly(fakeDrawableWidth, fakeDrawableHeight)
    }

    @Test
    fun `M not try to resolve bitmap W createImageWireframe() { PII image }`(
        forge: Forge,
        @Mock mockResources: Resources,
        @Mock mockDisplayMetrics: DisplayMetrics,
        @Mock mockContext: Context
    ) {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any())).thenReturn(true)

        val fakeGlobalX = forge.aPositiveInt()
        val fakeGlobalY = forge.aPositiveInt()
        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        mockDisplayMetrics.density = 1f
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        val mockView: View = mock {
            whenever(it.resources).thenReturn(mockResources)
            whenever(it.context).thenReturn(mockContext)
        }

        // When
        val result = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = forge.aPositiveInt(),
            x = fakeGlobalX.toLong(),
            y = fakeGlobalY.toLong(),
            width = forge.aPositiveInt(),
            height = forge.aPositiveInt(),
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        ) as MobileSegment.Wireframe.PlaceholderWireframe

        // Then
        verifyNoInteractions(mockResourceResolver)
        assertThat(isCloseTo(result.x.toInt(), fakeGlobalX)).isTrue
        assertThat(isCloseTo(result.y.toInt(), fakeGlobalY)).isTrue
    }

    @Test
    fun `M try to resolve bitmap W createImageWireframe() { non-PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any())).thenReturn(false)

        // When
        testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            drawable = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            resourceResolverCallback = any()
        )
    }

    @Test
    fun `M return content placeholder W createImageWireframe() { PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any())).thenReturn(true)

        // When
        val actualWireframe = testedHelper.createImageWireframe(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = 100,
            height = 100,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(actualWireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
        assertThat((actualWireframe as MobileSegment.Wireframe.PlaceholderWireframe).label)
            .isEqualTo(DefaultImageWireframeHelper.MASK_CONTEXTUAL_CONTENT_LABEL)
    }

    // endregion
}
