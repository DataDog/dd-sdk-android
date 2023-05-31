/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.view.View
import android.widget.Toolbar
import androidx.appcompat.widget.ActionBarContainer
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.optionselectormocks.AppcompatToolbarCustomSubclass
import com.datadog.android.sessionreplay.internal.recorder.optionselectormocks.ToolbarCustomSubclass
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.datadog.tools.unit.annotations.TestTargetApi
import com.datadog.tools.unit.extensions.ApiLevelExtension
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import androidx.appcompat.widget.Toolbar as AppCompatToolbar

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class UnsupportedViewMapperTest : BaseTextViewWireframeMapperTest() {

    private lateinit var testedUnsupportedViewMapper: UnsupportedViewMapper

    @Mock
    lateinit var mockToolbar: Toolbar

    @Mock
    lateinit var mockAppCompatToolbar: AppCompatToolbar

    @Mock
    lateinit var mockActionBarContainer: ActionBarContainer

    @Mock
    lateinit var mockToolbarSubclass: ToolbarCustomSubclass

    @Mock
    lateinit var mockAppcompatSubclass: AppcompatToolbarCustomSubclass

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    lateinit var actualWireframe: MobileSegment.Wireframe.TextWireframe

    @BeforeEach
    fun setup() {
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockAppCompatToolbar,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)

        testedUnsupportedViewMapper = UnsupportedViewMapper()

        actualWireframe = getWireframe(mockAppCompatToolbar)
    }

    @Test
    fun `M resolve with the toolbar label as text W map { AppCompatToolbar }`() {
        // Then
        assertThat(actualWireframe.text)
            .isEqualTo(UnsupportedViewMapper.TOOLBAR_LABEL)
    }

    @Test
    fun `M resolve with the toolbar label as text W map { Subclass of AppCompatToolbar }`() {
        // Given
        actualWireframe = getWireframe(mockAppcompatSubclass)

        // Then
        assertThat(actualWireframe.text)
            .isEqualTo(UnsupportedViewMapper.TOOLBAR_LABEL)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `M resolve with the toolbar label as text W map { Subclass of Toolbar }`() {
        // Given
        actualWireframe = getWireframe(mockToolbarSubclass)

        // Then
        assertThat(actualWireframe.text)
            .isEqualTo(UnsupportedViewMapper.TOOLBAR_LABEL)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `M resolve with the toolbar label as text W map { Toolbar }`() {
        // Given
        actualWireframe = getWireframe(mockToolbar)

        // Then
        assertThat(actualWireframe.text)
            .isEqualTo(UnsupportedViewMapper.TOOLBAR_LABEL)
    }

    @Test
    fun `M resolve with the default label as text W map { default unsupported view }`() {
        // When
        actualWireframe = getWireframe(mockActionBarContainer)

        // Then
        assertThat(actualWireframe.text)
            .isEqualTo(UnsupportedViewMapper.DEFAULT_LABEL)
    }

    @Test
    fun `M resolve with the correct textstyle W map`() {
        val expectedTextStyle = MobileSegment.TextStyle(
            family = UnsupportedViewMapper.SANS_SERIF_FAMILY_NAME,
            size = UnsupportedViewMapper.LABEL_TEXT_SIZE,
            color = UnsupportedViewMapper.TEXT_COLOR
        )

        assertThat(actualWireframe.textStyle)
            .isEqualTo(expectedTextStyle)
    }

    @Test
    fun `M resolve the correct textposition W map`() {
        val expectedTextPosition = MobileSegment.TextPosition(
            alignment = MobileSegment.Alignment(
                horizontal = MobileSegment.Horizontal.CENTER,
                vertical = MobileSegment.Vertical.CENTER
            )
        )

        assertThat(actualWireframe.textPosition)
            .isEqualTo(expectedTextPosition)
    }

    @Test
    fun `M resolve with the correct shapestyle W map`() {
        // Given
        val expectedShapeStyle = MobileSegment.ShapeStyle(
            backgroundColor = UnsupportedViewMapper.BACKGROUND_COLOR,
            opacity = mockAppCompatToolbar.alpha,
            cornerRadius = UnsupportedViewMapper.CORNER_RADIUS
        )

        // Then
        assertThat(actualWireframe.shapeStyle)
            .isEqualTo(expectedShapeStyle)
    }

    @Test
    fun `M resolve with the correct border W map`() {
        // Given
        val expectedBorder = MobileSegment.ShapeBorder(
            color = UnsupportedViewMapper.BORDER_COLOR,
            width = UnsupportedViewMapper.BORDER_WIDTH
        )

        // Then
        assertThat(actualWireframe.border)
            .isEqualTo(expectedBorder)
    }

    // region Internal
    private fun getWireframe(view: View): MobileSegment.Wireframe.TextWireframe {
        return testedUnsupportedViewMapper.map(
            view,
            fakeMappingContext
        )[0]
    }
    // endregion
}
