/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.view.View
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistration
import com.datadog.android.sessionreplay.internal.embedded.EmbeddedContentSlotRegistry
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.internal.recorder.mapper.EmbeddedContentViewMapper.Companion.EMBEDDED_CONTENT_KEY_NAME
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.GlobalBounds
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class EmbeddedContentViewMapperTest : LegacyBaseWireframeMapperTest() {

    private lateinit var testedMapper: EmbeddedContentViewMapper

    @Mock
    lateinit var mockView: View

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @Forgery
    lateinit var fakeBounds: GlobalBounds

    @LongForgery
    var fakeWireframeId: Long = 0

    @StringForgery
    lateinit var fakeSlotId: String

    private lateinit var fakeSlotRegistration: EmbeddedContentSlotRegistration

    private var slotId: String? = null

    @BeforeEach
    fun `set up`() {
        fakeSlotRegistration = EmbeddedContentSlotRegistration(fakeSlotId)
        slotId = fakeSlotId
        whenever(mockView.getTag(R.id.datadog_session_replay_slot_id)) doAnswer { slotId }
        whenever(mockViewUtilsInternal.isNotVisible(mockView)) doReturn false
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockView,
                EMBEDDED_CONTENT_KEY_NAME
            )
        ) doReturn fakeWireframeId
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                mockView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ) doReturn fakeBounds

        testedMapper = EmbeddedContentViewMapper(
            mockViewIdentifierResolver,
            mockColorStringFormatter,
            mockViewBoundsResolver,
            mockDrawableToColorMapper,
            mockViewUtilsInternal
        )
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeSlotRegistration)
    }

    @AfterEach
    fun `tear down`() {
        EmbeddedContentSlotRegistry.notifySlotChanged(fakeSlotRegistration, null)
    }

    @Test
    fun `M map embedded content W map { visible view }`() {
        // When
        val wireframe = testedMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        ).single()

        // Then
        assertThat(wireframe).isEqualTo(
            MobileSegment.Wireframe.EmbeddedContentWireframe(
                id = fakeWireframeId,
                x = fakeBounds.x,
                y = fakeBounds.y,
                width = fakeBounds.width,
                height = fakeBounds.height,
                slotId = fakeSlotId,
                isVisible = true
            )
        )
        verify(mockViewIdentifierResolver, never()).resolveViewId(mockView)
    }

    @Test
    fun `M map hidden embedded content W map { invisible view }`() {
        // Given
        whenever(mockViewUtilsInternal.isNotVisible(mockView)) doReturn true

        // When
        val wireframe = testedMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        ).single()

        // Then
        assertThat(wireframe).isEqualTo(
            MobileSegment.Wireframe.EmbeddedContentWireframe(
                id = fakeWireframeId,
                x = 0,
                y = 0,
                width = 0,
                height = 0,
                slotId = fakeSlotId,
                isVisible = false
            )
        )
    }

    @Test
    fun `M retain hidden slot W finishSnapshot { view missing from next snapshot }`() {
        // Given
        testedMapper.beginSnapshot()
        testedMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )
        assertThat(testedMapper.finishSnapshot()).isNull()

        // When
        testedMapper.beginSnapshot()
        val hiddenNode = testedMapper.finishSnapshot()

        // Then
        assertThat(hiddenNode?.wireframes).containsExactly(
            MobileSegment.Wireframe.EmbeddedContentWireframe(
                id = fakeWireframeId,
                x = 0,
                y = 0,
                width = 0,
                height = 0,
                slotId = fakeSlotId,
                isVisible = false
            )
        )
    }

    @Test
    fun `M remove cached slot W finishSnapshot { slot cleared }`() {
        // Given
        testedMapper.beginSnapshot()
        testedMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // When
        EmbeddedContentSlotRegistry.notifySlotChanged(fakeSlotRegistration, null)
        slotId = null
        testedMapper.beginSnapshot()

        // Then
        assertThat(testedMapper.finishSnapshot()).isNull()
    }

    @Test
    fun `M track views independently W finishSnapshot { multiple embedded views }`() {
        // Given
        val firstView = mock<View>()
        val secondView = mock<View>()
        val fakeFirstSlotId = "first-slot"
        val fakeSecondSlotId = "second-slot"
        val fakeFirstRegistration = EmbeddedContentSlotRegistration(fakeFirstSlotId)
        val fakeSecondRegistration = EmbeddedContentSlotRegistration(fakeSecondSlotId)
        whenever(firstView.getTag(R.id.datadog_session_replay_slot_id)) doReturn fakeFirstSlotId
        whenever(secondView.getTag(R.id.datadog_session_replay_slot_id)) doReturn fakeSecondSlotId
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeFirstRegistration)
        EmbeddedContentSlotRegistry.notifySlotChanged(null, fakeSecondRegistration)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(firstView, EMBEDDED_CONTENT_KEY_NAME)
        ) doReturn 101L
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(secondView, EMBEDDED_CONTENT_KEY_NAME)
        ) doReturn 202L
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                firstView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ) doReturn fakeBounds
        whenever(
            mockViewBoundsResolver.resolveViewGlobalBounds(
                secondView,
                fakeMappingContext.systemInformation.screenDensity
            )
        ) doReturn fakeBounds
        try {
            testedMapper.beginSnapshot()
            val firstWireframe = testedMapper.map(
                firstView,
                fakeMappingContext,
                mockAsyncJobStatusCallback,
                mockInternalLogger
            ).single()
            val secondWireframe = testedMapper.map(
                secondView,
                fakeMappingContext,
                mockAsyncJobStatusCallback,
                mockInternalLogger
            ).single()
            assertThat(testedMapper.finishSnapshot()).isNull()

            // When
            testedMapper.beginSnapshot()
            testedMapper.map(
                firstView,
                fakeMappingContext,
                mockAsyncJobStatusCallback,
                mockInternalLogger
            )
            val hiddenNode = testedMapper.finishSnapshot()

            // Then
            val firstEmbeddedWireframe =
                firstWireframe as MobileSegment.Wireframe.EmbeddedContentWireframe
            val secondEmbeddedWireframe =
                secondWireframe as MobileSegment.Wireframe.EmbeddedContentWireframe
            assertThat(firstEmbeddedWireframe.id).isEqualTo(101L)
            assertThat(firstEmbeddedWireframe.slotId).isEqualTo(fakeFirstSlotId)
            assertThat(secondEmbeddedWireframe.id).isEqualTo(202L)
            assertThat(secondEmbeddedWireframe.slotId).isEqualTo(fakeSecondSlotId)
            assertThat(hiddenNode?.wireframes).containsExactly(
                MobileSegment.Wireframe.EmbeddedContentWireframe(
                    id = 202L,
                    x = 0,
                    y = 0,
                    width = 0,
                    height = 0,
                    slotId = fakeSecondSlotId,
                    isVisible = false
                )
            )
        } finally {
            EmbeddedContentSlotRegistry.notifySlotChanged(fakeFirstRegistration, null)
            EmbeddedContentSlotRegistry.notifySlotChanged(fakeSecondRegistration, null)
        }
    }

    @Test
    fun `M ignore view W map { embedded wireframe id cannot be resolved }`() {
        // Given
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                mockView,
                EMBEDDED_CONTENT_KEY_NAME
            )
        ) doReturn null

        // When
        val wireframes = testedMapper.map(
            mockView,
            fakeMappingContext,
            mockAsyncJobStatusCallback,
            mockInternalLogger
        )

        // Then
        assertThat(wireframes).isEmpty()
    }
}
