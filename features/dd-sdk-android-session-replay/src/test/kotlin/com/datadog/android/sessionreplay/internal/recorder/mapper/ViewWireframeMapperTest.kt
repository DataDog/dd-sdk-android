/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.ColorDrawable
import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.aMockView
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.tools.unit.extensions.ApiLevelExtension
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
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ViewWireframeMapperTest : BaseWireframeMapperTest() {

    lateinit var testedWireframeMapper: ViewWireframeMapper

    @Forgery
    lateinit var fakeBounds: GlobalBounds

    @LongForgery
    var fakeWireframeId: Long = 0L

    @BeforeEach
    fun `set up`() {
        testedWireframeMapper = ViewWireframeMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    @Test
    fun `M resolve a ShapeWireframe W map()`(forge: Forge) {
        // Given
        val mockView: View = forge.aMockView()
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ) doReturn fakeBounds
        whenever(mockViewIdentifierResolver.resolveViewId(mockView)) doReturn fakeWireframeId

        // When
        val wireframes = testedWireframeMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        assertThat(wireframes.size).isEqualTo(1)
        val wireframe = wireframes.first()

        assertThat(wireframe.id).isEqualTo(fakeWireframeId)
        assertThat(wireframe.x).isEqualTo(fakeBounds.x)
        assertThat(wireframe.y).isEqualTo(fakeBounds.y)
        assertThat(wireframe.width).isEqualTo(fakeBounds.width)
        assertThat(wireframe.height).isEqualTo(fakeBounds.height)
        assertThat(wireframe.clip).isNull()
        assertThat(wireframe.shapeStyle).isNull()
        assertThat(wireframe.border).isNull()
    }

    @Test
    fun `M resolve a ShapeWireframe with shapeStyle W map { ColorDrawable }`(
        forge: Forge,
        @IntForgery(0, 0xFFFFFF) fakeBackgroundColor: Int,
        @StringForgery(regex = "#[0-9A-Z]{8}") fakeBackgroundColorString: String
    ) {
        // Given
        val fakeViewAlpha = forge.aFloat(min = 0f, max = 1f)
        val mockDrawable = mock<ColorDrawable>()
        val mockView = forge.aMockView<View>().apply {
            whenever(this.background).thenReturn(mockDrawable)
            whenever(this.alpha).thenReturn(fakeViewAlpha)
        }
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ) doReturn fakeBounds
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockDrawable)) doReturn fakeBackgroundColor
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeBackgroundColor))
            .doReturn(fakeBackgroundColorString)
        whenever(mockViewIdentifierResolver.resolveViewId(mockView)) doReturn fakeWireframeId

        // When
        val shapeWireframes = testedWireframeMapper.map(mockView, fakeMappingContext, mockAsyncJobStatusCallback)

        // Then
        val expectedWireframe = MobileSegment.Wireframe.ShapeWireframe(
            id = fakeWireframeId,
            x = fakeBounds.x,
            y = fakeBounds.y,
            width = fakeBounds.width,
            height = fakeBounds.height,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fakeBackgroundColorString,
                opacity = fakeViewAlpha,
                cornerRadius = null
            )
        )

        assertThat(shapeWireframes).isEqualTo(listOf(expectedWireframe))
    }
}
