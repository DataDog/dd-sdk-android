/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.embedded.internal

import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.embedded.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedViewWireframeMapperTest {

    private lateinit var testedEmbeddedViewWireframeMapper: EmbeddedViewWireframeMapper

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockColorStringFormatter: ColorStringFormatter

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockDrawableToColorMapper: DrawableToColorMapper

    @Mock
    lateinit var mockAsyncJobStatusCallback: AsyncJobStatusCallback

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    @Forgery
    lateinit var fakeViewGlobalBounds: GlobalBounds

    @LongForgery
    var fakeSlotId: Long = 0L

    @BeforeEach
    fun `set up`() {
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ).thenReturn(fakeViewGlobalBounds)
        whenever(
            mockViewIdentifierResolver.resolveViewId(mockView)
        ).thenReturn(fakeSlotId)

        testedEmbeddedViewWireframeMapper = EmbeddedViewWireframeMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper
        )
    }

    @Test
    fun `M resolve an EmbeddedContentWireframe W map()`() {
        // Given
        val expectedWireframe = MobileSegment.Wireframe.EmbeddedContentWireframe(
            id = fakeSlotId,
            x = fakeViewGlobalBounds.x,
            y = fakeViewGlobalBounds.y,
            width = fakeViewGlobalBounds.width,
            height = fakeViewGlobalBounds.height,
            slotId = fakeSlotId.toString(),
            isVisible = true
        )

        // When
        val mappedWireframes = testedEmbeddedViewWireframeMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(mappedWireframes).hasSize(1)
            .contains(expectedWireframe)
    }
}
