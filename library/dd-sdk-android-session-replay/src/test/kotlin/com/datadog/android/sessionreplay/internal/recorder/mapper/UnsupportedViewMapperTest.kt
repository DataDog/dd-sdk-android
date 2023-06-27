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
import com.nhaarman.mockitokotlin2.whenever
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

    @BeforeEach
    fun setup() {
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockAppCompatToolbar,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockToolbarSubclass,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockToolbar,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockActionBarContainer,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockAppcompatSubclass,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)

        testedUnsupportedViewMapper = UnsupportedViewMapper(mockViewUtils)
    }

    @Test
    fun `M resolve with the toolbar label as text W map { AppCompatToolbar }`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.PlaceholderWireframe(
            id = System.identityHashCode(mockAppCompatToolbar).toLong(),
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y,
            width = fakeViewGlobalBounds.width,
            height = fakeViewGlobalBounds.height,
            label = UnsupportedViewMapper.TOOLBAR_LABEL
        )
        // When
        val actualWireframe = getWireframe(mockAppCompatToolbar)

        // Then
        assertThat(actualWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve with the toolbar label as text W map { Subclass of AppCompatToolbar }`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.PlaceholderWireframe(
            id = System.identityHashCode(mockAppcompatSubclass).toLong(),
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y,
            width = fakeViewGlobalBounds.width,
            height = fakeViewGlobalBounds.height,
            label = UnsupportedViewMapper.TOOLBAR_LABEL
        )

        // When
        val actualWireframe = getWireframe(mockAppcompatSubclass)

        // Then
        assertThat(actualWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `M resolve with the toolbar label as text W map { Subclass of Toolbar }`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.PlaceholderWireframe(
            id = System.identityHashCode(mockToolbarSubclass).toLong(),
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y,
            width = fakeViewGlobalBounds.width,
            height = fakeViewGlobalBounds.height,
            label = UnsupportedViewMapper.TOOLBAR_LABEL
        )

        // When
        val actualWireframe = getWireframe(mockToolbarSubclass)

        // Then
        assertThat(actualWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    @TestTargetApi(Build.VERSION_CODES.LOLLIPOP)
    fun `M resolve with the toolbar label as text W map { Toolbar }`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.PlaceholderWireframe(
            id = System.identityHashCode(mockToolbar).toLong(),
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y,
            width = fakeViewGlobalBounds.width,
            height = fakeViewGlobalBounds.height,
            label = UnsupportedViewMapper.TOOLBAR_LABEL
        )

        // When
        val actualWireframe = getWireframe(mockToolbar)

        // Then
        assertThat(actualWireframe).isEqualTo(expectedWireframe)
    }

    @Test
    fun `M resolve with the default label as text W map { default unsupported view }`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.PlaceholderWireframe(
            id = System.identityHashCode(mockActionBarContainer).toLong(),
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y,
            width = fakeViewGlobalBounds.width,
            height = fakeViewGlobalBounds.height,
            label = UnsupportedViewMapper.DEFAULT_LABEL
        )

        // When
        val actualWireframe = getWireframe(mockActionBarContainer)

        // Then
        assertThat(actualWireframe).isEqualTo(expectedWireframe)
    }

    // region Internal

    private fun getWireframe(view: View): MobileSegment.Wireframe.PlaceholderWireframe {
        return testedUnsupportedViewMapper.map(
            view,
            fakeMappingContext
        )[0]
    }
    // endregion
}
