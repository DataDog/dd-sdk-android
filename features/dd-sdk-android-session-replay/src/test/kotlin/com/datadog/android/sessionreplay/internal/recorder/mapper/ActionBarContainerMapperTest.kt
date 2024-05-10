/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.graphics.drawable.Drawable
import androidx.appcompat.widget.ActionBarContainer
import androidx.appcompat.widget.DatadogActionBarContainerAccessor
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ActionBarContainerMapperTest : BaseWireframeMapperTest() {

    lateinit var testedMapper: WireframeMapper<ActionBarContainer>

    @Mock
    lateinit var mockActionBarContainer: ActionBarContainer

    @Mock
    lateinit var mockBackgroundDrawable: Drawable

    @StringForgery(regex = "#[0-9A-F]{8}")
    lateinit var fakeBackgroundHexColor: String

    @LongForgery
    var fakeViewId: Long = 0L

    @LongForgery
    var fakeViewX: Long = 0L

    @LongForgery
    var fakeViewY: Long = 0L

    @LongForgery
    var fakeViewWidth: Long = 0L

    @LongForgery
    var fakeViewHeight: Long = 0L

    @IntForgery
    var fakeBackgroundColor: Int = 0

    @FloatForgery
    var fakeViewAlpha: Float = 0f

    @BeforeEach
    fun `set up`() {
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockActionBarContainer,
                BaseAsyncBackgroundWireframeMapper.PREFIX_BACKGROUND_DRAWABLE
            )
        ) doReturn fakeViewId
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockActionBarContainer,
                fakeMappingContext.systemInformation.screenDensity
            )
        ) doReturn GlobalBounds(fakeViewX, fakeViewY, fakeViewWidth, fakeViewHeight)

        DatadogActionBarContainerAccessor(mockActionBarContainer).setBackgroundDrawable(mockBackgroundDrawable)
        whenever(mockActionBarContainer.alpha) doReturn fakeViewAlpha

        testedMapper = ActionBarContainerMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    @Test
    fun `M return a shape wireframe with the background W map()`() {
        // Given
        whenever(
            mockDrawableToColorMapper.mapDrawableToColor(
                mockBackgroundDrawable,
                mockInternalLogger
            )
        ) doReturn fakeBackgroundColor
        whenever(mockColorStringFormatter.formatColorAsHexString(fakeBackgroundColor)) doReturn fakeBackgroundHexColor

        // When
        val result =
            testedMapper.map(mockActionBarContainer, fakeMappingContext, mockAsyncJobStatusCallback, mockInternalLogger)

        // Then
        assertThat(result).hasSize(1)
        val wireframe = result[0]
        assertThat(wireframe).isEqualTo(
            MobileSegment.Wireframe.ShapeWireframe(
                id = fakeViewId,
                x = fakeViewX,
                y = fakeViewY,
                width = fakeViewWidth,
                height = fakeViewHeight,
                clip = null,
                shapeStyle = MobileSegment.ShapeStyle(
                    backgroundColor = fakeBackgroundHexColor,
                    opacity = fakeViewAlpha,
                    cornerRadius = null
                ),
                border = null
            )
        )
    }

    @Test
    fun `M return nothing W map() {unsupported background drawable}`() {
        // Given
        whenever(mockDrawableToColorMapper.mapDrawableToColor(mockBackgroundDrawable, mockInternalLogger)) doReturn null

        // When
        val result = testedMapper.map(
            mockActionBarContainer,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(result).isEmpty()
    }
}
