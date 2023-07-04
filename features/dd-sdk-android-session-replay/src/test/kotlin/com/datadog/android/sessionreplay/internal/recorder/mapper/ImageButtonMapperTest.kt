/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.widget.ImageButton
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.base64.Base64Serializer
import com.datadog.android.sessionreplay.internal.recorder.base64.ImageCompression
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils
import fr.xgouchet.elmyr.Forge
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
internal class ImageButtonMapperTest {

    lateinit var testedMapper: ImageButtonMapper

    @Mock
    lateinit var mockImageButton: ImageButton

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
    lateinit var mockViewUtils: ViewUtils

    @Mock
    lateinit var mockGlobalBounds: GlobalBounds

    @Mock
    lateinit var mockBackground: Drawable

    private val fakeId = Forge().aLong()

    private val fakeMimeType = Forge().aString()

    @BeforeEach
    fun setup(forge: Forge) {
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(fakeId)

        whenever(mockImageButton.drawable).thenReturn(mockDrawable)

        whenever(mockWebPImageCompression.getMimeType()).thenReturn(fakeMimeType)

        whenever(mockSystemInformation.screenDensity).thenReturn(forge.aFloat())
        whenever(mockMappingContext.systemInformation).thenReturn(mockSystemInformation)

        whenever(mockResources.displayMetrics).thenReturn(mockDisplayMetrics)
        whenever(mockImageButton.resources).thenReturn(mockResources)

        whenever(mockImageButton.background).thenReturn(mockBackground)

        whenever(mockViewUtils.resolveViewGlobalBounds(any(), any())).thenReturn(mockGlobalBounds)

        testedMapper = ImageButtonMapper(
            webPImageCompression = mockWebPImageCompression,
            base64Serializer = mockBase64Serializer,
            uniqueIdentiferGenerator = mockUniqueIdentifierGenerator
        )
    }

    @Test
    fun `M return emptylist W map() { and could not get view id }`() {
        // Given
        whenever(mockUniqueIdentifierGenerator.resolveChildUniqueIdentifier(any(), any()))
            .thenReturn(null)

        // When
        val result = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return emptylist W map() { and could not get drawable }`() {
        // Given
        whenever(mockImageButton.drawable).thenReturn(null)

        // When
        val result = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return expected wireframe W map()`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = fakeId,
            width = mockGlobalBounds.width,
            height = mockGlobalBounds.height,
            x = mockGlobalBounds.x,
            y = mockGlobalBounds.y,
            shapeStyle = null,
            border = null,
            base64 = "",
            mimeType = fakeMimeType,
            isEmpty = true
        )

        // When
        val result = testedMapper.map(mockImageButton, mockMappingContext)

        // Then
        val wireframe = result[0]
        assertThat(wireframe).isEqualTo(expectedWireframe)
    }
}
