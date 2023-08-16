/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.base64

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import fr.xgouchet.elmyr.Forge
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
    lateinit var mockView: View

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

    @LongForgery
    var fakeGeneratedIdentifier: Long = 0L

    @LongForgery(min = 1, max = 300)
    var fakeDrawableWidth: Long = 0L

    @LongForgery(min = 1, max = 300)
    var fakeDrawableHeight: Long = 0L

    private lateinit var fakeDrawableXY: Pair<Long, Long>

    @StringForgery
    var fakeMimeType: String = ""

    @BeforeEach
    fun `set up`(forge: Forge) {
        val fakeScreenWidth = 1000
        val fakeScreenHeight = 1000

        val randomXLocation = forge.aLong(min = 0, max = fakeScreenWidth - fakeDrawableWidth)
        val randomYLocation = forge.aLong(min = 0, max = fakeScreenHeight - fakeDrawableHeight)
        fakeDrawableXY = Pair(randomXLocation, randomYLocation)

        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(mockView, "drawable"))
            .thenReturn(fakeGeneratedIdentifier)

        whenever(mockDrawable.intrinsicWidth).thenReturn(fakeDrawableWidth.toInt())
        whenever(mockDrawable.intrinsicHeight).thenReturn(fakeDrawableHeight.toInt())

        whenever(mockView.resources).thenReturn(mockResources)
        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)

        whenever(mockView.context).thenReturn(mockContext)
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        whenever(mockImageCompression.getMimeType()).thenReturn(fakeMimeType)

        whenever(mockBounds.width).thenReturn(fakeDrawableWidth)
        whenever(mockBounds.height).thenReturn(fakeDrawableHeight)
        whenever(mockBounds.x).thenReturn(fakeDrawableXY.first)
        whenever(mockBounds.y).thenReturn(fakeDrawableXY.second)

        testedHelper = ImageWireframeHelper(
            imageCompression = mockImageCompression,
            uniqueIdentifierGenerator = mockUniqueIdentifierGenerator,
            base64Serializer = mockBase64Serializer
        )
    }

    @Test
    fun `M return null W createImageWireframe() { drawable is null }`() {
        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            index = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = null,
            shapeStyle = null,
            border = null
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return null W createImageWireframe() { id is null }`() {
        // Given
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            index = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return null W createImageWireframe() { drawable has no intrinsic width }`() {
        // Given
        whenever(mockDrawable.intrinsicWidth).thenReturn(-1)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            index = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return null W createImageWireframe() { drawable has no intrinsic height }`() {
        // Given
        whenever(mockDrawable.intrinsicHeight).thenReturn(-1)

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            index = 0,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        assertThat(wireframe).isNull()
    }

    @Test
    fun `M return wireframe W createImageWireframe()`(
        @LongForgery id: Long
    ) {
        // Given
        whenever(
            mockUniqueIdentifierGenerator
                .resolveChildUniqueIdentifier(any(), any())
        )
            .thenReturn(id)

        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = id,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeDrawableWidth,
            height = fakeDrawableHeight,
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        // When
        val wireframe = testedHelper.createImageWireframe(
            view = mockView,
            index = 0,
            x = fakeDrawableXY.first,
            y = fakeDrawableXY.second,
            width = fakeDrawableWidth,
            height = fakeDrawableHeight,
            drawable = mockDrawable,
            shapeStyle = null,
            border = null
        )

        // Then
        assertThat(wireframe).isEqualTo(expectedWireframe)
    }
}
