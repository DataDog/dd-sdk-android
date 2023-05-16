/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.Toolbar
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewUtils
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class UnsupportedViewMapperTest : BaseTextViewWireframeMapperTest() {

    private lateinit var testedUnsupportedViewMapper: UnsupportedViewMapper

    @Mock
    lateinit var mockUnsupportedView: Toolbar

    @Mock
    lateinit var mockViewUtils: ViewUtils

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @BeforeEach
    fun setup() {
        whenever(
            mockViewUtils.resolveViewGlobalBounds(
                mockUnsupportedView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)

        testedUnsupportedViewMapper = UnsupportedViewMapper()
    }

    @Test
    fun `M resolve with the class name of the View W map()`() {
        // When
        val wireframes = testedUnsupportedViewMapper.map(
            mockUnsupportedView,
            fakeMappingContext
        )

        // Then
        Assertions.assertThat(wireframes[0].text)
            .isEqualTo(mockUnsupportedView::class.java.name)
    }
}
