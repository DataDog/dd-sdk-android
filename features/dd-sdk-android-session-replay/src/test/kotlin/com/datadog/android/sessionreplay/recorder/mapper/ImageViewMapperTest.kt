/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.recorder.mapper

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Drawable.ConstantState
import android.util.DisplayMetrics
import android.view.View
import android.widget.ImageView
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.utils.ImageViewUtils
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.recorder.resources.DrawableCopier
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
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
    lateinit var mockDrawable: Drawable

    @Mock
    lateinit var mockSystemInformation: SystemInformation

    @Mock
    lateinit var mockResources: Resources

    @Mock
    lateinit var mockDisplayMetrics: DisplayMetrics

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockDrawableCopier: DrawableCopier

    @Mock
    lateinit var mockGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockBackgroundDrawable: Drawable

    @Mock
    lateinit var mockConstantState: ConstantState

    @Mock
    lateinit var mockBackgroundConstantState: ConstantState

    @Mock
    lateinit var stubClipping: Rect

    @Mock
    lateinit var stubParentRect: Rect

    @Mock
    lateinit var stubContentRect: Rect

    @Mock
    lateinit var mockContext: Context

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeImagePrivacy: ImagePrivacy

    @LongForgery
    var fakeId: Long = 0L

    @StringForgery(regex = "\\w+/\\w+")
    lateinit var fakeMimeType: String

    private lateinit var expectedImageWireframe: MobileSegment.Wireframe.ImageWireframe

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockImageView.background).thenReturn(null)

        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeId)

        whenever(mockConstantState.newDrawable(any())).thenReturn(mockDrawable)
        whenever(mockDrawable.constantState).thenReturn(mockConstantState)
        whenever(mockImageView.drawable).thenReturn(mockDrawable)
        whenever(mockImageView.drawable.current).thenReturn(mockDrawable)

        whenever(mockDrawable.intrinsicWidth).thenReturn(forge.aPositiveInt())
        whenever(mockDrawable.intrinsicHeight).thenReturn(forge.aPositiveInt())

        whenever(mockSystemInformation.screenDensity).thenReturn(forge.aFloat())
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)
        whenever(mockMappingContext.imageWireframeHelper).thenReturn(mockImageWireframeHelper)
        whenever(mockMappingContext.imagePrivacy).thenReturn(fakeImagePrivacy)

        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockImageView.resources).thenReturn(mockResources)
        mockDisplayMetrics.density = 1f

        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockImageView.context).thenReturn(mockContext)
        whenever(mockBackgroundDrawable.current).thenReturn(mockBackgroundDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(any(), eq(mockInternalLogger))) doReturn null

        whenever(stubImageViewUtils.resolveParentRectAbsPosition(any())).thenReturn(stubParentRect)
        whenever(stubImageViewUtils.resolveContentRectWithScaling(any(), any(), anyOrNull()))
            .thenReturn(stubContentRect)
        whenever(stubImageViewUtils.calculateClipping(any(), any(), any())).thenReturn(stubClipping)
        stubContentRect.left = forge.aPositiveInt()
        stubContentRect.top = forge.aPositiveInt()
        whenever(stubContentRect.width()).thenReturn(forge.aPositiveInt())
        whenever(stubContentRect.height()).thenReturn(forge.aPositiveInt())

        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(any(), any())).thenReturn(mockGlobalBounds)

        whenever(mockBackgroundConstantState.newDrawable(any())) doReturn mockBackgroundDrawable
        whenever(mockBackgroundDrawable.constantState) doReturn mockBackgroundConstantState

        expectedImageWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeId,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageView.width.toLong(),
            height = mockImageView.height.toLong(),
            shapeStyle = null,
            border = null,
            mimeType = fakeMimeType,
            isEmpty = true
        )

        testedMapper = ImageViewMapper(
            viewIdentifierResolver = mockViewIdentifierResolver,
            colorStringFormatter = mockColorStringFormatter,
            viewBoundsResolver = mockViewBoundsResolver,
            drawableToColorMapper = mockDrawableToColorMapper,
            imageViewUtils = stubImageViewUtils,
            drawableCopier = mockDrawableCopier
        )
    }

    @Test
    fun `M return foreground wireframe W map() { no background }`() {
        // Given
        whenever(mockImageView.background).thenReturn(null)
        mockImageWireframeHelper(
            expectedView = mockImageView,
            expectedImagePrivacy = fakeImagePrivacy,
            expectedDrawable = mockDrawable,
            expectedIndex = 0,
            expectedPrefix = ImageWireframeHelper.DRAWABLE_CHILD_NAME,
            expectedUsePIIPlaceholder = true,
            returnedWireframe = expectedImageWireframe
        )

        // When
        val wireframes = testedMapper.map(
            mockImageView,
            mockMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes.size).isEqualTo(1)
        assertThat(wireframes[0]).isEqualTo(expectedImageWireframe)
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
            mimeType = fakeMimeType,
            isEmpty = true
        )
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockBackgroundDrawable, mockInternalLogger)) doReturn null
        whenever(mockImageView.background).thenReturn(mockBackgroundDrawable)
        mockImageWireframeHelper(
            expectedView = mockImageView,
            expectedImagePrivacy = fakeImagePrivacy,
            expectedDrawable = mockBackgroundDrawable,
            expectedIndex = 0,
            expectedPrefix = BaseAsyncBackgroundWireframeMapper.PREFIX_BACKGROUND_DRAWABLE,
            expectedUsePIIPlaceholder = false,
            returnedWireframe = expectedBackgroundWireframe
        )
        mockImageWireframeHelper(
            expectedView = mockImageView,
            expectedImagePrivacy = fakeImagePrivacy,
            expectedDrawable = mockDrawable,
            expectedIndex = 1,
            expectedPrefix = ImageWireframeHelper.DRAWABLE_CHILD_NAME,
            expectedUsePIIPlaceholder = true,
            returnedWireframe = expectedImageWireframe
        )

        // When
        val wireframes = testedMapper.map(
            mockImageView,
            mockMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes.size).isEqualTo(2)
        assertThat(wireframes[0]).isEqualTo(expectedBackgroundWireframe)
        assertThat(wireframes[1]).isEqualTo(expectedImageWireframe)
    }

    @Test
    fun `M call async callback W map() { }`() {
        // Given
        whenever(
            mockImageWireframeHelper.createImageWireframeByDrawable(
                view = any(),
                imagePrivacy = any(),
                currentWireframeIndex = any(),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = any(),
                drawable = any(),
                drawableCopier = any(),
                asyncJobStatusCallback = anyOrNull(),
                clipping = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                prefix = anyOrNull(),
                customResourceIdCacheKey = anyOrNull()
            )
        ).thenReturn(expectedImageWireframe)

        // When
        val wireframes = testedMapper.map(
            mockImageView,
            mockMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes.size).isEqualTo(1)
        assertThat(wireframes[0]).isEqualTo(expectedImageWireframe)

        verify(mockImageWireframeHelper)
            .createImageWireframeByDrawable(
                view = any(),
                imagePrivacy = any(),
                currentWireframeIndex = any(),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = any(),
                drawable = any(),
                drawableCopier = any(),
                asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
                clipping = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                prefix = anyOrNull(),
                customResourceIdCacheKey = anyOrNull()
            )
    }

    @Test
    fun `M return background of type ImageWireframe W map() { no shape style or border }`(
        @LongForgery id: Long
    ) {
        // Given
        whenever(mockImageView.background).thenReturn(mockBackgroundDrawable)
        val expectedBackgroundWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            width = mockImageView.width.toLong(),
            height = mockImageView.height.toLong(),
            shapeStyle = null,
            border = null,
            mimeType = fakeMimeType,
            isEmpty = true
        )

        mockImageWireframeHelper(
            expectedView = mockImageView,
            expectedImagePrivacy = fakeImagePrivacy,
            expectedDrawable = mockBackgroundDrawable,
            expectedIndex = 0,
            expectedPrefix = BaseAsyncBackgroundWireframeMapper.PREFIX_BACKGROUND_DRAWABLE,
            expectedUsePIIPlaceholder = false,
            returnedWireframe = expectedBackgroundWireframe
        )
        mockImageWireframeHelper(
            expectedView = mockImageView,
            expectedImagePrivacy = fakeImagePrivacy,
            expectedDrawable = mockDrawable,
            expectedIndex = 1,
            expectedPrefix = ImageWireframeHelper.DRAWABLE_CHILD_NAME,
            expectedUsePIIPlaceholder = true,
            returnedWireframe = expectedImageWireframe
        )

        // When
        val wireframes = testedMapper.map(
            mockImageView,
            mockMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes[0]::class.java).isEqualTo(MobileSegment.Wireframe.ImageWireframe::class.java)
    }

    @Test
    fun `M return background of type ShapeWireframe W map() { has shapestyle or border }`(
        @Mock mockColorDrawable: ColorDrawable,
        @IntForgery mockColor: Int
    ) {
        // Given
        whenever(mockImageView.background).thenReturn(mockColorDrawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockColorDrawable, mockInternalLogger)) doReturn mockColor

        // When
        val wireframes = testedMapper.map(
            mockImageView,
            mockMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes[0]::class.java).isEqualTo(MobileSegment.Wireframe.ShapeWireframe::class.java)
    }

    @Test
    fun `M return no background W map() { cant resolve id for shapeDrawable }`(
        @Mock mockColorDrawable: ColorDrawable
    ) {
        // Given
        whenever(mockImageView.background).thenReturn(mockColorDrawable)
        whenever(mockViewIdentifierResolver.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)
        mockImageWireframeHelper(
            expectedView = mockImageView,
            expectedImagePrivacy = fakeImagePrivacy,
            expectedDrawable = mockDrawable,
            expectedIndex = 0,
            expectedPrefix = ImageWireframeHelper.DRAWABLE_CHILD_NAME,
            expectedUsePIIPlaceholder = true,
            returnedWireframe = expectedImageWireframe
        )

        // When
        val wireframes = testedMapper.map(
            mockImageView,
            mockMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes.size).isEqualTo(1)
        assertThat(wireframes[0]).isEqualTo(expectedImageWireframe)
    }

    private fun mockImageWireframeHelper(
        expectedView: View,
        expectedImagePrivacy: ImagePrivacy,
        expectedDrawable: Drawable,
        expectedIndex: Int,
        expectedPrefix: String?,
        expectedUsePIIPlaceholder: Boolean,
        returnedWireframe: MobileSegment.Wireframe
    ) {
        whenever(
            mockImageWireframeHelper.createImageWireframeByDrawable(
                view = eq(expectedView),
                imagePrivacy = eq(expectedImagePrivacy),
                currentWireframeIndex = eq(expectedIndex),
                x = any(),
                y = any(),
                width = any(),
                height = any(),
                usePIIPlaceholder = eq(expectedUsePIIPlaceholder),
                drawable = eq(expectedDrawable),
                drawableCopier = any(),
                asyncJobStatusCallback = eq(mockAsyncJobStatusCallback),
                clipping = anyOrNull(),
                shapeStyle = anyOrNull(),
                border = anyOrNull(),
                prefix = eq(expectedPrefix),
                customResourceIdCacheKey = anyOrNull()
            )
        )
            .thenReturn(returnedWireframe)
    }
}
