/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.Context
import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.aMockView
import com.datadog.android.sessionreplay.model.MobileSegment
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DecorViewMapperTest : BaseWireframeMapperTest() {

    @Mock
    lateinit var mockViewWireframeMapper: ViewWireframeMapper

    lateinit var mockDecorView: View

    lateinit var mockViewWireframes: List<MobileSegment.Wireframe.ShapeWireframe>

    private lateinit var testedDecorViewMapper: DecorViewMapper

    @LongForgery
    var fakeUniqueIdentifier: Long = 0L

    @BeforeEach
    fun `set up`(forge: Forge) {
        mockDecorView = forge.aMockView()
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockDecorView,
                DecorViewMapper.WINDOW_KEY_NAME
            )
        )
            .thenReturn(fakeUniqueIdentifier)
        mockViewWireframes = forge.aList(forge.anInt(min = 1, max = 10)) {
            getForgery()
        }
        whenever(mockViewWireframeMapper.map(eq(mockDecorView), eq(fakeMappingContext), any()))
            .thenReturn(mockViewWireframes)
        testedDecorViewMapper = DecorViewMapper(
            mockViewWireframeMapper,
            mockViewIdentifierResolver
        )
    }

    // region Theme background

    @Test
    fun `M use the theme color W map { view wireframes miss ShapeStyle, has theme color }`(
        forge: Forge
    ) {
        // Given
        val fakeThemeColor = forge.aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}")
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = fakeThemeColor,
            opacity = mockDecorView.alpha
        )
        fakeMappingContext = fakeMappingContext.copy(
            systemInformation =
            fakeMappingContext.systemInformation.copy(themeColor = fakeThemeColor)
        )
        mockViewWireframes = mockViewWireframes.map {
            it.copy(shapeStyle = null)
        }
        whenever(mockViewWireframeMapper.map(eq(mockDecorView), eq(fakeMappingContext), any()))
            .thenReturn(mockViewWireframes)
        val viewWireframesIds = mockViewWireframes.map { it.id }.toSet()
        val expectedDecorViewWireframes = mockViewWireframes.map {
            it.copy(shapeStyle = expectedShapeStyle)
        }

        // When
        val mappedDecorViewWireframes = testedDecorViewMapper
            .map(mockDecorView, fakeMappingContext, mockAsyncJobStatusCallback)
            .filter { (it as MobileSegment.Wireframe.ShapeWireframe).id in viewWireframesIds }

        // Then
        assertThat(mappedDecorViewWireframes).isEqualTo(expectedDecorViewWireframes)
    }

    @Test
    fun `M do nothing W map { view wireframes having at least one ShapeStyle, has theme color }`(
        forge: Forge
    ) {
        // Given
        val fakeThemeColor = forge.aStringMatching("#[0-9A-Fa-f]{6}[fF]{2}")
        fakeMappingContext = fakeMappingContext.copy(
            systemInformation =
            fakeMappingContext.systemInformation.copy(themeColor = fakeThemeColor)
        )
        val randIndex = forge.anInt(min = 0, max = mockViewWireframes.size)
        mockViewWireframes = mockViewWireframes.mapIndexed { index, wireframe ->
            if (index <= randIndex) {
                wireframe.copy(shapeStyle = forge.getForgery())
            } else {
                wireframe.copy(shapeStyle = null)
            }
        }
        whenever(mockViewWireframeMapper.map(eq(mockDecorView), eq(fakeMappingContext), any()))
            .thenReturn(mockViewWireframes)
        val viewWireframesIds = mockViewWireframes.map { it.id }.toSet()
        val expectedDecorViewWireframes = mockViewWireframes

        // When
        val mappedDecorViewWireframes = testedDecorViewMapper
            .map(mockDecorView, fakeMappingContext, mockAsyncJobStatusCallback)
            .filter { (it as MobileSegment.Wireframe.ShapeWireframe).id in viewWireframesIds }

        // Then
        assertThat(mappedDecorViewWireframes).isEqualTo(expectedDecorViewWireframes)
    }

    @Test
    fun `M do nothing W map { view wireframes miss ShapeStyle, has not theme color }`() {
        // Given
        fakeMappingContext = fakeMappingContext.copy(
            systemInformation =
            fakeMappingContext.systemInformation.copy(themeColor = null)
        )
        mockViewWireframes = mockViewWireframes.map {
            it.copy(shapeStyle = null)
        }
        whenever(mockViewWireframeMapper.map(eq(mockDecorView), eq(fakeMappingContext), any()))
            .thenReturn(mockViewWireframes)
        val viewWireframesIds = mockViewWireframes.map { it.id }.toSet()
        val expectedDecorViewWireframes = mockViewWireframes

        // When
        val mappedDecorViewWireframes = testedDecorViewMapper
            .map(mockDecorView, fakeMappingContext, mockAsyncJobStatusCallback)
            .filter { (it as MobileSegment.Wireframe.ShapeWireframe).id in viewWireframesIds }

        // Then
        assertThat(mappedDecorViewWireframes).isEqualTo(expectedDecorViewWireframes)
    }

    // endregion

    // region Window background

    @Test
    fun `M add a ShapeWireframe as Window background W map { no PopUpDecorView }`() {
        // Given
        val expectedWindowWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeUniqueIdentifier,
            x = 0,
            y = 0,
            width = fakeMappingContext.systemInformation.screenBounds.width,
            height = fakeMappingContext.systemInformation.screenBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DecorViewMapper.WINDOW_WIREFRAME_COLOR,
                opacity = DecorViewMapper.WINDOW_WIREFRAME_OPACITY
            )
        )

        // When
        val wireframes = testedDecorViewMapper.map(mockDecorView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes.size).isEqualTo(mockViewWireframes.size + 1)
        assertThat(wireframes.first()).isEqualTo(expectedWindowWireframe)
    }

    @Test
    fun `M do not handle the Window background W map {uniqueIdentifier could not be generated}`() {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockDecorView,
                DecorViewMapper.WINDOW_KEY_NAME
            )
        ).thenReturn(null)
        val expectedWindowWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeUniqueIdentifier,
            x = 0,
            y = 0,
            width = fakeMappingContext.systemInformation.screenBounds.width,
            height = fakeMappingContext.systemInformation.screenBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DecorViewMapper.WINDOW_WIREFRAME_COLOR,
                opacity = DecorViewMapper.WINDOW_WIREFRAME_OPACITY
            )
        )

        // When
        val wireframes = testedDecorViewMapper.map(mockDecorView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes.size).isEqualTo(mockViewWireframes.size)
        assertThat(wireframes.first()).isNotEqualTo(expectedWindowWireframe)
    }

    @Test
    fun `M do not handle the Window background W map {PopUpDecorView class name}`(forge: Forge) {
        // Given
        val mockPopUpDecorView = forge.aMockView<PopUpDecorView>()
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockPopUpDecorView,
                DecorViewMapper.WINDOW_KEY_NAME
            )
        ).thenReturn(fakeUniqueIdentifier)
        whenever(mockViewWireframeMapper.map(eq(mockPopUpDecorView), eq(fakeMappingContext), any()))
            .thenReturn(mockViewWireframes)
        val expectedWindowWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeUniqueIdentifier,
            x = 0,
            y = 0,
            width = fakeMappingContext.systemInformation.screenBounds.width,
            height = fakeMappingContext.systemInformation.screenBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = DecorViewMapper.WINDOW_WIREFRAME_COLOR,
                opacity = DecorViewMapper.WINDOW_WIREFRAME_OPACITY
            )
        )

        // When
        val wireframes = testedDecorViewMapper.map(mockPopUpDecorView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes.size).isEqualTo(mockViewWireframes.size)
        assertThat(wireframes.first()).isNotEqualTo(expectedWindowWireframe)
    }

    private class PopUpDecorView(context: Context) : View(context)

    // endregion
}
