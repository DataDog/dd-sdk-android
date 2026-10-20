/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition.mapper

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.View
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.PixelResource
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.DefaultCapturedIdentityFactory
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicInteger

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class LegacyCapturedViewMapperTest {
    private val mockMapper: WireframeMapper<View> = mock()
    private val mockView: View = mock()
    private val mockLogger: InternalLogger = mock()
    private val mockImageHelper: ImageWireframeHelper = mock()
    private val fakeTouchPrivacyManager = TouchPrivacyManager(TouchPrivacy.SHOW)
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
    private val fakeContext = CapturedMappingContext(
        fakeFactory,
        fakeFactory.view(fakeFactory.window("window"), "owner"),
        1f,
        imagePrivacy = ImagePrivacy.MASK_LARGE_ONLY,
        textAndInputPrivacy = TextAndInputPrivacy.MASK_ALL_INPUTS,
        hasOptionSelectorParent = true
    )
    private val testedMapper = LegacyCapturedViewMapper(
        mockMapper,
        emptyList(),
        mock(),
        mockImageHelper,
        fakeTouchPrivacyManager,
        mock(),
        mockLogger
    )

    @BeforeEach
    fun setUp() {
        val stubResources: Resources = mock()
        val fakeMetrics = DisplayMetrics().apply { widthPixels = 100; heightPixels = 200 }
        whenever(mockView.resources).thenReturn(stubResources)
        whenever(stubResources.displayMetrics).thenReturn(fakeMetrics)
        whenever(stubResources.configuration).thenReturn(Configuration())
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M contain extension failures W capture() { async job started }`(fakeLinkageError: Boolean) {
        // Given
        whenever(mockMapper.map(any(), any(), any(), any())).thenAnswer {
            it.getArgument<AsyncJobStatusCallback>(2).jobStarted()
            if (fakeLinkageError) throw NoClassDefFoundError("Extension")
            error("Extension")
        }
        val fakeMappings = mutableListOf<PendingViewMapping>()
        val fakeNotifications = AtomicInteger()

        // When
        val copied = testedMapper.capture(mockView, fakeContext.copy(pendingMappingSink = fakeMappings::add))
        fakeMappings.single().whenReady { fakeNotifications.incrementAndGet() }

        // Then
        assertThat(fakeNotifications.get()).isEqualTo(1)
        assertThat((copied.map(fakeContext) as CapturedViewMapperResult.Wireframes).wireframes).isEmpty()
    }

    @Test
    fun `M collect owned touch overrides W capture() { retained manager and mutable bounds }`() {
        // Given
        val fakeBounds = Rect(1, 2, 30, 40)
        var fakeRetainedManager: TouchPrivacyManager? = null
        whenever(mockMapper.map(any(), any(), any(), any())).thenAnswer {
            val manager = it.getArgument<MappingContext>(1).touchPrivacyManager
            fakeRetainedManager = manager
            manager.addTouchOverrideArea(fakeBounds, TouchPrivacy.HIDE)
            manager.addTouchOverrideArea(Rect(fakeBounds), TouchPrivacy.SHOW)
            emptyList<MobileSegment.Wireframe>()
        }
        val fakePolicies = mutableListOf<Map<Rect, TouchPrivacy>?>()

        // When
        testedMapper.capture(mockView, fakeContext.copy(customTouchPrivacySink = fakePolicies::add))
        fakeBounds.left = 100
        fakeRetainedManager!!.addTouchOverrideArea(Rect(50, 50, 70, 70), TouchPrivacy.SHOW)

        // Then
        val fakePolicy = checkNotNull(fakePolicies.single())
        assertThat(fakePolicy).containsOnlyKeys(Rect(1, 2, 30, 40))
        assertThat(fakePolicy.values).containsExactly(TouchPrivacy.HIDE)
        assertThat(fakeTouchPrivacyManager.getNextOverrideAreas()).isEmpty()
        assertThat(fakeTouchPrivacyManager.getCurrentOverrideAreas()).isEmpty()
    }

    @Test
    fun `M suppress visual resource work W captureTouchPrivacy() { skipped visual subtree }`() {
        // Given
        val fakePolicies = mutableListOf<Map<Rect, TouchPrivacy>?>()
        val fakeJobs = mutableListOf<PendingViewMapping>()
        val stubBitmap: Bitmap = mock()
        whenever(mockMapper.map(any(), any(), any(), any())).thenAnswer {
            val context = it.getArgument<MappingContext>(1)
            val callback = it.getArgument<AsyncJobStatusCallback>(2)
            assertThat(context.imagePrivacy).isEqualTo(fakeContext.imagePrivacy)
            assertThat(
                context.imageWireframeHelper.createImageWireframeByBitmap(
                    1L,
                    GlobalBounds(0, 0, 10, 10),
                    stubBitmap,
                    1f,
                    false,
                    ImagePrivacy.MASK_NONE,
                    callback
                )
            ).isNull()
            context.touchPrivacyManager.addTouchOverrideArea(Rect(0, 0, 100, 100), TouchPrivacy.HIDE)
            emptyList<MobileSegment.Wireframe>()
        }

        // When
        testedMapper.captureTouchPrivacy(
            mockView,
            fakeContext.copy(
                customTouchPrivacySink = fakePolicies::add,
                pendingMappingSink = fakeJobs::add
            )
        )

        // Then
        assertThat(fakePolicies.single()).containsEntry(Rect(0, 0, 100, 100), TouchPrivacy.HIDE)
        assertThat(fakeJobs).isEmpty()
        verifyNoInteractions(mockImageHelper)
    }

    @Test
    fun `M discard partial custom touch policy W capture() { mapper throws }`() {
        // Given
        whenever(mockMapper.map(any(), any(), any(), any())).thenAnswer {
            it.getArgument<MappingContext>(1).touchPrivacyManager
                .addTouchOverrideArea(Rect(0, 0, 100, 100), TouchPrivacy.SHOW)
            error("Extension")
        }
        val fakePolicies = mutableListOf<Map<Rect, TouchPrivacy>?>()

        // When
        testedMapper.capture(mockView, fakeContext.copy(customTouchPrivacySink = fakePolicies::add))

        // Then
        assertThat(fakePolicies).containsExactly(null)
        assertThat(fakeTouchPrivacyManager.getNextOverrideAreas()).isEmpty()
    }

    @Test
    fun `M propagate privacy and selector ancestry W capture()`() {
        // Given
        whenever(mockMapper.map(any(), any(), any(), any()))
            .thenReturn(listOf(MobileSegment.Wireframe.ShapeWireframe(1L, 1L, 2L, 3L, 4L)))

        // When
        val copied = testedMapper.capture(mockView, fakeContext)

        // Then
        val fakeContextCaptor = argumentCaptor<MappingContext>()
        verify(mockMapper).map(eq(mockView), fakeContextCaptor.capture(), any(), eq(mockLogger))
        assertThat(fakeContextCaptor.firstValue.imagePrivacy).isEqualTo(ImagePrivacy.MASK_LARGE_ONLY)
        assertThat(fakeContextCaptor.firstValue.textAndInputPrivacy).isEqualTo(TextAndInputPrivacy.MASK_ALL_INPUTS)
        assertThat(fakeContextCaptor.firstValue.hasOptionSelectorParent).isTrue()
        assertThat((copied.map(fakeContext) as CapturedViewMapperResult.Wireframes).wireframes)
            .hasSize(1).allMatch { it is CapturedWireframe.Shape }
    }

    @Test
    fun `M wait for extension resources W capture()`() {
        // Given
        val fakeImage = MobileSegment.Wireframe.ImageWireframe(1L, 0L, 0L, 10L, 10L)
        lateinit var fakeCallback: AsyncJobStatusCallback
        whenever(mockMapper.map(any(), any(), any(), any())).thenAnswer {
            fakeCallback = it.getArgument(2)
            fakeCallback.jobStarted()
            listOf(fakeImage)
        }
        val fakeMappings = mutableListOf<PendingViewMapping>()
        val fakeNotifications = AtomicInteger()
        val copied = testedMapper.capture(mockView, fakeContext.copy(pendingMappingSink = fakeMappings::add))
        fakeMappings.single().whenReady { fakeNotifications.incrementAndGet() }
        assertThat(fakeNotifications.get()).isZero()

        // When
        fakeImage.resourceId = "resource"
        fakeCallback.jobFinished()
        fakeCallback.jobFinished()

        // Then
        assertThat(fakeNotifications.get()).isEqualTo(1)
        val result = copied.map(fakeContext) as CapturedViewMapperResult.Wireframes
        assertThat((result.wireframes.single() as CapturedWireframe.Pixel).resource)
            .isEqualTo(PixelResource.Resolved("resource"))
    }

    @Test
    fun `M prevent recursive interop W capture() { extension maps its own view }`() {
        // Given
        whenever(mockMapper.map(any(), any(), any(), any())).thenAnswer {
            val context = it.getArgument<MappingContext>(1)
            context.interopViewCallback.map(mockView, context)
        }

        // When
        val copied = testedMapper.capture(mockView, fakeContext)

        // Then
        verify(mockMapper).map(eq(mockView), any(), any(), any())
        assertThat((copied.map(fakeContext) as CapturedViewMapperResult.Wireframes).wireframes).isEmpty()
    }
}
