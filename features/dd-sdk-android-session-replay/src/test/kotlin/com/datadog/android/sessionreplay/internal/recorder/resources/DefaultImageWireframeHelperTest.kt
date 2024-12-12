/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.resources

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
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
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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
@Suppress("MaxLineLength")
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

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockBitmap: Bitmap

    @Mock
    lateinit var mockContext: Context

    @LongForgery
    var fakeViewId: Long = 0L

    @FloatForgery(min = 1f, max = 10f)
    var fakeDensity: Float = 0.0f

    @LongForgery(min = 1)
    var fakeGeneratedIdentifier: Long = 0L

    private lateinit var fakeDrawableXY: Pair<Long, Long>

    @IntForgery(min = 1)
    var fakePadding: Int = 0

    @StringForgery
    lateinit var fakeResourceId: String

    @Forgery
    lateinit var fakeBounds: GlobalBounds

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeScreenWidth = 655367L
        val fakeScreenHeight = 655367L

        val randomXLocation = forge.aLong(min = 1, max = (fakeScreenWidth - fakeBounds.width))
        val randomYLocation = forge.aLong(min = 1, max = (fakeScreenHeight - fakeBounds.height))
        fakeDrawableXY = Pair(randomXLocation, randomYLocation)
        whenever(mockMappingContext.imagePrivacy).thenReturn(ImagePrivacy.MASK_LARGE_ONLY)
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockSystemInformation.screenDensity).thenReturn(0f)
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(mockView, "drawable"))
            .thenReturn(fakeGeneratedIdentifier)
        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeBounds.width.toInt())
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeBounds.height.toInt())
        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockView.resources).thenReturn(mockResources)
        whenever(mockView.context).thenReturn(mockContext)
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockTextView.resources).thenReturn(mockResources)
        whenever(mockTextView.context).thenReturn(mockContext)
        whenever(mockViewUtilsInternal.resolveDrawableBounds(any(), any(), any()))
            .thenReturn(fakeBounds)
        whenever(mockTextView.width).thenReturn(fakeBounds.width.toInt())
        whenever(mockTextView.height).thenReturn(fakeBounds.height.toInt())
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

        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                view = any(),
                drawable = any(),
                pixelsDensity = any(),
                position = any()
            )
        ).thenReturn(fakeBounds)

        testedHelper = DefaultImageWireframeHelper(
            logger = mockLogger,
            resourceResolver = mockResourceResolver,
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewUtilsInternal = mockViewUtilsInternal,
            imageTypeResolver = mockImageTypeResolver
        )
    }

    @Test
    fun `M return wireframe W createImageWireframeByBitmap`(
        @Mock mockShapeStyle: MobileSegment.ShapeStyle,
        @Mock mockBorder: MobileSegment.ShapeBorder,
        @Mock stubWireframeClip: MobileSegment.WireframeClip
    ) {
        // Given
        whenever(
            mockResourceResolver.resolveResourceId(
                bitmap = any(),
                resourceResolverCallback = any()
            )
        ).thenAnswer {
            val callback = it.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess(fakeResourceId)
        }

        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeBounds.x,
            y = fakeBounds.y,
            width = fakeBounds.width,
            height = fakeBounds.height,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            resourceId = fakeResourceId,
            clip = stubWireframeClip,
            isEmpty = false
        )

        // When
        val wireframe = testedHelper.createImageWireframeByBitmap(
            id = fakeGeneratedIdentifier,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            density = fakeDensity,
            globalBounds = fakeBounds,
            bitmap = mockBitmap,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            asyncJobStatusCallback = mockAsyncJobStatusCallback,
            isContextualImage = false,
            clipping = stubWireframeClip
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            bitmap = any(),
            resourceResolverCallback = any()
        )
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
        assertThat(wireframe).isEqualTo(expectedWireframe)
    }

    // region createImageWireframeByBitmap

    @Test
    fun `M return content placeholder W createImageWireframeByBitmap { ImagePrivacy MASK_ALL }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByBitmap(
            id = fakeViewId,
            bitmap = mockBitmap,
            density = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            isContextualImage = false,
            globalBounds = fakeBounds,
            shapeStyle = null,
            border = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M return placeholder W createImageWireframeByBitmap { MASK_LARGE_ONLY & isContextual }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByBitmap(
            id = fakeViewId,
            bitmap = mockBitmap,
            density = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            isContextualImage = true,
            globalBounds = fakeBounds,
            shapeStyle = null,
            border = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M call jobFinished W createImageWireframeByBitmap { failure }`() {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeGeneratedIdentifier)

        whenever(
            mockResourceResolver.resolveResourceId(
                bitmap = any(),
                resourceResolverCallback = any()
            )
        ).thenAnswer {
            val callback = it.getArgument<ResourceResolverCallback>(1)
            callback.onFailure()
        }

        // When
        testedHelper.createImageWireframeByBitmap(
            id = fakeViewId,
            bitmap = mockBitmap,
            density = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            isContextualImage = false,
            globalBounds = fakeBounds,
            shapeStyle = null,
            border = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
    }

    // endregion

    // region createImageWireframeByDrawable

    @Test
    fun `M call jobFinished W createImageWireframeByDrawable { failure }`() {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeGeneratedIdentifier)
        whenever(
            mockResourceResolver.resolveResourceId(
                resources = any(),
                applicationContext = any(),
                displayMetrics = any(),
                originalDrawable = any(),
                drawableCopier = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                customResourceIdCacheKey = anyOrNull(),
                resourceResolverCallback = any()
            )
        ).thenAnswer {
            val callback = it.getArgument<ResourceResolverCallback>(8)
            callback.onFailure()
        }

        // When
        testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
    }

    @Test
    fun `M use customResourceIdCacheKey W createImageWireframeByDrawable { key provided }`(
        forge: Forge,
        @StringForgery fakeResourceIdCacheKey: String
    ) {
        // Given
        val fakeXPosition = forge.aPositiveLong()
        val fakeYPosition = forge.aPositiveLong()
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()

        // When
        testedHelper.createImageWireframeByDrawable(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            currentWireframeIndex = 0,
            x = fakeXPosition,
            y = fakeYPosition,
            width = fakeWidth,
            height = fakeHeight,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = false,
            customResourceIdCacheKey = fakeResourceIdCacheKey,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = eq(fakeResourceIdCacheKey),
            resourceResolverCallback = any()
        )
    }

    @Test
    fun `M return content placeholder W createImageWireframeByDrawable { ImagePrivacy MASK_ALL }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M return content placeholder W createImageWireframeByBitmap() { ImagePrivacy MASK_ALL }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByBitmap(
            id = fakeViewId,
            bitmap = mockBitmap,
            density = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            isContextualImage = false,
            globalBounds = fakeBounds,
            shapeStyle = null,
            border = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M not return image wireframe W createImageWireframeByDrawable(usePIIPlaceholder = true) { MASK_NONE }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)
    }

    @Test
    fun `M not return image wireframe W createImageWireframeByBitmap { MASK_LARGE_ONLY & isContextual image}`() {
        // When
        val wireframe = testedHelper.createImageWireframeByBitmap(
            id = fakeViewId,
            bitmap = mockBitmap,
            density = fakeDensity,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            isContextualImage = true,
            globalBounds = fakeBounds,
            shapeStyle = null,
            border = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
    }

    @Test
    fun `M return null W createImageWireframeByDrawable() { application context is null }`() {
        // Given
        whenever(mockView.context.applicationContext).thenReturn(null)

        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M send telemetry W createImageWireframeByDrawable { application context is null }`() {
        // Given
        whenever(mockView.context.applicationContext).thenReturn(null)

        // When
        testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
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
    fun `M log error W createImageWireframeByDrawable { resources is null }`() {
        // Given
        whenever(mockView.resources).thenReturn(null)

        // When
        testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
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
    fun `M return null W createImageWireframeByDrawable { id is null }`() {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
        verifyNoInteractions(mockAsyncJobStatusCallback)
    }

    @Test
    fun `M return null W createImageWireframeByDrawable { drawable has no width }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return null W createImageWireframeByDrawable { drawable has no height }`() {
        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return wireframe W createImageWireframeByDrawable`(
        @Mock mockShapeStyle: MobileSegment.ShapeStyle,
        @Mock mockBorder: MobileSegment.ShapeBorder,
        @Mock stubWireframeClip: MobileSegment.WireframeClip
    ) {
        // Given
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeGeneratedIdentifier)
        whenever(
            mockResourceResolver.resolveResourceId(
                resources = any(),
                applicationContext = any(),
                displayMetrics = any(),
                originalDrawable = any(),
                drawableCopier = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                customResourceIdCacheKey = anyOrNull(),
                resourceResolverCallback = any()
            )
        ).thenAnswer {
            val callback = it.getArgument<ResourceResolverCallback>(8)
            callback.onSuccess(fakeResourceId)
        }

        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeBounds.width,
            height = fakeBounds.height,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            resourceId = fakeResourceId,
            clip = stubWireframeClip,
            isEmpty = false
        )

        // When
        val wireframe = testedHelper.createImageWireframeByDrawable(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeBounds.width.toInt(),
            height = fakeBounds.height.toInt(),
            drawable = mockDrawable,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            asyncJobStatusCallback = mockAsyncJobStatusCallback,
            usePIIPlaceholder = true,
            clipping = stubWireframeClip,
            customResourceIdCacheKey = null
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = any()
        )
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
        assertThat(wireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M return wireframe W createImageWireframeByBitmap()`(
        @Mock mockShapeStyle: MobileSegment.ShapeStyle,
        @Mock mockBorder: MobileSegment.ShapeBorder,
        @Mock stubWireframeClip: MobileSegment.WireframeClip
    ) {
        // Given
        whenever(
            mockResourceResolver.resolveResourceId(
                bitmap = any(),
                resourceResolverCallback = any()
            )
        ).thenAnswer {
            val callback = it.getArgument<ResourceResolverCallback>(1)
            callback.onSuccess(fakeResourceId)
        }

        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeGeneratedIdentifier,
            x = fakeBounds.x,
            y = fakeBounds.y,
            width = fakeBounds.width,
            height = fakeBounds.height,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            resourceId = fakeResourceId,
            clip = stubWireframeClip,
            isEmpty = false
        )

        // When
        val wireframe = testedHelper.createImageWireframeByBitmap(
            id = fakeGeneratedIdentifier,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            density = fakeDensity,
            globalBounds = fakeBounds,
            bitmap = mockBitmap,
            shapeStyle = mockShapeStyle,
            border = mockBorder,
            asyncJobStatusCallback = mockAsyncJobStatusCallback,
            isContextualImage = false,
            clipping = stubWireframeClip
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            bitmap = any(),
            resourceResolverCallback = any()
        )
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        verifyNoMoreInteractions(mockAsyncJobStatusCallback)
        assertThat(wireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M use intrinsic dimensions W createImageWireframeByBitmap { inset drawable }`(
        @Mock mockInsetDrawable: InsetDrawable,
        forge: Forge
    ) {
        // Given
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        whenever(mockInsetDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockInsetDrawable.intrinsicHeight).thenReturn(fakeHeight)

        // When
        val placeholderWireframe = testedHelper.createImageWireframeByDrawable(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = fakeWidth,
            height = fakeHeight,
            drawable = mockInsetDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        ) as MobileSegment.Wireframe.PlaceholderWireframe

        // Then
        assertThat(placeholderWireframe.width.toInt()).isEqualTo(fakeWidth)
        assertThat(placeholderWireframe.height.toInt()).isEqualTo(fakeHeight)
    }

    @Test
    fun `M use intrinsic dimensions W createImageWireframeByBitmap { layerDrawable no layers }`(
        @Mock mockLayerDrawable: LayerDrawable,
        forge: Forge
    ) {
        // Given
        val fakeWidth = forge.aPositiveInt()
        val fakeHeight = forge.aPositiveInt()
        whenever(mockLayerDrawable.intrinsicWidth).thenReturn(fakeWidth)
        whenever(mockLayerDrawable.intrinsicHeight).thenReturn(fakeHeight)
        whenever(mockLayerDrawable.numberOfLayers).thenReturn(0)

        // When
        val placeholderWireframe = testedHelper.createImageWireframeByDrawable(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_ALL,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = fakeWidth,
            height = fakeHeight,
            drawable = mockLayerDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        ) as MobileSegment.Wireframe.PlaceholderWireframe

        // Then
        assertThat(placeholderWireframe.width.toInt()).isEqualTo(fakeWidth)
        assertThat(placeholderWireframe.height.toInt()).isEqualTo(fakeHeight)
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
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
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
                view = any(),
                drawable = any(),
                pixelsDensity = any(),
                position = any()
            )
        ).thenReturn(fakeBounds)
        val fakeDrawables = arrayOf(null, mockDrawable, null, null)
        whenever(mockTextView.compoundDrawables)
            .thenReturn(fakeDrawables)

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        assertThat(wireframes[0]).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)

        // Then
        val argumentCaptor = argumentCaptor<ResourceResolverCallback>()

        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onSuccess(fakeResourceId)
        }
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        assertThat(wireframes).hasSize(1)
    }

    @Test
    fun `M return multiple wireframes W createCompoundDrawableWireframes() { multiple drawables }`() {
        // Given
        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                view = any(),
                drawable = any(),
                pixelsDensity = any(),
                position = any()
            )
        )
            .thenReturn(fakeBounds)
        val fakeDrawables = arrayOf(null, mockDrawable, null, mockDrawable)
        whenever(mockTextView.compoundDrawables)
            .thenReturn(fakeDrawables)

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        assertThat(wireframes[0]).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)

        // Then
        val argumentCaptor = argumentCaptor<ResourceResolverCallback>()
        verify(mockResourceResolver, times(2)).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onSuccess(fakeResourceId)
        }
        verify(mockAsyncJobStatusCallback, times(2)).jobStarted()
        verify(mockAsyncJobStatusCallback, times(2)).jobFinished()
        assertThat(wireframes).hasSize(2)
    }

    @Test
    fun `M skip invalid elements W createCompoundDrawableWireframes() { invalid indices }`() {
        // Given
        whenever(mockTextView.compoundDrawables)
            .thenReturn(arrayOf(null, null, null, null, null, null))

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verifyNoInteractions(mockAsyncJobStatusCallback)
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M resolve view width and height W createImageWireframeByDrawable() { RippleDrawable }`(
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
        testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = captor.capture(),
            drawableHeight = captor.capture(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = any()
        )
        assertThat(captor.allValues).containsExactly(fakeViewWidth, fakeViewHeight)
    }

    @Test
    fun `M resolve drawable width and height W createImageWireframeByDrawable { TextView }`() {
        // When
        testedHelper.createImageWireframeByDrawable(
            view = mockView,
            imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
            currentWireframeIndex = 0,
            x = 0,
            y = 0,
            width = fakeBounds.width.toInt(),
            height = fakeBounds.height.toInt(),
            drawable = mockDrawable,
            shapeStyle = null,
            border = null,
            usePIIPlaceholder = true,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        val captor = argumentCaptor<Int>()
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = captor.capture(),
            drawableHeight = captor.capture(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = any()

        )
        assertThat(captor.allValues).containsExactly(fakeBounds.width.toInt(), fakeBounds.height.toInt())
    }

    @Test
    fun `M not try to resolve bitmap W createImageWireframeByDrawable() { PII image }`(
        forge: Forge,
        @Mock mockResources: Resources,
        @Mock mockDisplayMetrics: DisplayMetrics,
        @Mock mockContext: Context
    ) {
        // Given
        whenever(
            mockImageTypeResolver.isDrawablePII(
                drawable = any(),
                density = any()
            )
        ).thenReturn(true)

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
        val result = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        ) as MobileSegment.Wireframe.PlaceholderWireframe

        // Then
        verifyNoInteractions(mockResourceResolver)
        assertThat(isCloseTo(result.x.toInt(), fakeGlobalX)).isTrue
        assertThat(isCloseTo(result.y.toInt(), fakeGlobalY)).isTrue
    }

    @Test
    fun `M try to resolve bitmap W createImageWireframeByDrawable() { non-PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any())).thenReturn(false)

        // When
        testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = any()
        )
    }

    @Test
    fun `M return content placeholder W createImageWireframeByDrawable() { PII image }`() {
        // Given
        whenever(mockImageTypeResolver.isDrawablePII(any(), any())).thenReturn(true)

        // When
        val actualWireframe = testedHelper.createImageWireframeByDrawable(
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
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        assertThat(actualWireframe).isInstanceOf(MobileSegment.Wireframe.PlaceholderWireframe::class.java)
        assertThat((actualWireframe as MobileSegment.Wireframe.PlaceholderWireframe).label)
            .isEqualTo(DefaultImageWireframeHelper.MASK_CONTEXTUAL_CONTENT_LABEL)
    }

    // endregion

    // region createCompoundDrawableWireframes

    @Test
    fun `M use correct customResourceIdCacheKeys W createCompoundDrawableWireframes { key provided }`(
        @StringForgery fakeResourceIdCacheKey: String
    ) {
        // Given
        whenever(mockTextView.compoundDrawables)
            .thenReturn(arrayOf(mockDrawable, mockDrawable, mockDrawable, mockDrawable))

        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                view = any(),
                drawable = any(),
                pixelsDensity = any(),
                position = any()
            )
        ).thenReturn(fakeBounds)

        // When
        testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = fakeResourceIdCacheKey,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        mockTextView.compoundDrawables.forEachIndexed { index, _ ->
            val expectedKey = fakeResourceIdCacheKey + "_$index"

            // Then
            verify(mockResourceResolver).resolveResourceId(
                resources = any(),
                applicationContext = any(),
                displayMetrics = any(),
                originalDrawable = any(),
                drawableCopier = any(),
                drawableWidth = any(),
                drawableHeight = any(),
                customResourceIdCacheKey = eq(expectedKey),
                resourceResolverCallback = any()
            )
        }
    }

    @Test
    fun `M return empty list W createCompoundDrawableWireframes { no compound drawables }`() {
        // Given
        whenever(mockTextView.compoundDrawables)
            .thenReturn(arrayOf(null, null, null, null))

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verifyNoInteractions(mockAsyncJobStatusCallback)
        assertThat(wireframes).isEmpty()
    }

    @Test
    fun `M return wireframe W createCompoundDrawableWireframes`() {
        // Given
        val fakeDrawables = arrayOf(null, mockDrawable, null, null)
        whenever(mockTextView.compoundDrawables)
            .thenReturn(fakeDrawables)

        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                view = any(),
                drawable = any(),
                pixelsDensity = any(),
                position = any()
            )
        ).thenReturn(fakeBounds)

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        assertThat(wireframes[0]).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)

        // Then
        val argumentCaptor = argumentCaptor<ResourceResolverCallback>()

        verify(mockResourceResolver).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onSuccess(fakeResourceId)
        }
        verify(mockAsyncJobStatusCallback).jobStarted()
        verify(mockAsyncJobStatusCallback).jobFinished()
        assertThat(wireframes).hasSize(1)
    }

    @Test
    fun `M return multiple wireframes W createCompoundDrawableWireframes { multiple drawables }`() {
        // Given
        whenever(
            mockViewUtilsInternal.resolveCompoundDrawableBounds(
                view = any(),
                drawable = any(),
                pixelsDensity = any(),
                position = any()
            )
        )
            .thenReturn(fakeBounds)
        val fakeDrawables = arrayOf(null, mockDrawable, null, mockDrawable)
        whenever(mockTextView.compoundDrawables)
            .thenReturn(fakeDrawables)

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        assertThat(wireframes[0]).isInstanceOf(MobileSegment.Wireframe.ImageWireframe::class.java)

        // Then
        val argumentCaptor = argumentCaptor<ResourceResolverCallback>()
        verify(mockResourceResolver, times(2)).resolveResourceId(
            resources = any(),
            applicationContext = any(),
            displayMetrics = any(),
            originalDrawable = any(),
            drawableCopier = any(),
            drawableWidth = any(),
            drawableHeight = any(),
            customResourceIdCacheKey = anyOrNull(),
            resourceResolverCallback = argumentCaptor.capture()
        )
        argumentCaptor.allValues.forEach {
            it.onSuccess(fakeResourceId)
        }
        verify(mockAsyncJobStatusCallback, times(2)).jobStarted()
        verify(mockAsyncJobStatusCallback, times(2)).jobFinished()
        assertThat(wireframes).hasSize(2)
    }

    @Test
    fun `M skip invalid elements W createCompoundDrawableWireframes { invalid indices }`() {
        // Given
        whenever(mockTextView.compoundDrawables)
            .thenReturn(arrayOf(null, null, null, null, null, null))

        // When
        val wireframes = testedHelper.createCompoundDrawableWireframes(
            textView = mockTextView,
            mappingContext = mockMappingContext,
            prevWireframeIndex = 0,
            customResourceIdCacheKey = null,
            asyncJobStatusCallback = mockAsyncJobStatusCallback
        )

        // Then
        verifyNoInteractions(mockAsyncJobStatusCallback)
        assertThat(wireframes).isEmpty()
    }

    // endregion
}
