/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedBackgroundShapeStyleResolver
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedTextViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.FloatForgery
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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.atomic.AtomicLong

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class AndroidCapturedSnapshotProducerTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
    private val mockTouchPrivacyManager: TouchPrivacyManager = mock()
    private lateinit var nextViewId: AtomicLong
    private lateinit var fakeContext: CaptureGenerationContext
    private var fakeDensity: Float = 1f

    @BeforeEach
    fun `set up`(
        @LongForgery(min = 1L, max = 1_000_000L) fakeViewIdSeed: Long,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensityForgery: Float
    ) {
        nextViewId = AtomicLong(fakeViewIdSeed)
        fakeDensity = fakeDensityForgery
        fakeContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider { 0L }
        )
    }

    private fun mockWindow(fakeBounds: GlobalBounds): ViewGroup {
        val fakeMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock { on { displayMetrics } doReturn fakeMetrics }
        val mockView: ViewGroup = mock {
            on { isShown } doReturn true
            on { width } doReturn fakeBounds.width.toInt()
            on { height } doReturn fakeBounds.height.toInt()
            on { getTag(any()) } doReturn null
            on { childCount } doReturn 0
            on { resources } doReturn mockResources
        }
        whenever(mockViewIdentifierResolver.resolveViewId(mockView)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeDensity)).thenReturn(fakeBounds)
        return mockView
    }

    private fun traversal() = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(
            mappers = listOf(
                CapturedMapperTypeWrapper(
                    TextView::class.java,
                    CapturedTextViewMapper(
                        backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(mock()),
                        internalLogger = mock()
                    )
                )
            ),
            fallbackMapper = CapturedViewGroupFallbackMapper(
                backgroundShapeStyleResolver = CapturedBackgroundShapeStyleResolver(mock())
            ),
            internalLogger = mock()
        ),
        internalLogger = mock(),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        viewUtilsInternal = ViewUtilsInternal()
    )

    /** These trees are tiny and the deadline is generous, so every capture must complete in one slice. */
    private fun CapturedSnapshotInput?.doneValue(): CapturedFullSnapshot? =
        when (val input = this) {
            null -> null
            is CapturedFullSnapshot -> input
            is CapturedNativeSnapshot -> CapturedNativeSnapshotBuilder().build(input, fakeContext)
        }

    private fun producer(
        windows: List<View>,
        scope: CapturedRumViewScope?,
        fakeDeviceTimestampMs: Long
    ): AndroidCapturedSnapshotProducer {
        val windowSource = ActiveWindowSource().apply { update(windows) }
        val mockTimeProvider: TimeProvider = mock()
        whenever(mockTimeProvider.getDeviceTimestampMillis()).thenReturn(fakeDeviceTimestampMs)
        return AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = RumViewScopeProvider { scope },
            timeProvider = mockTimeProvider,
            traversal = traversal(),
            touchPrivacyManager = mockTouchPrivacyManager,
            viewIdentifierResolver = mockViewIdentifierResolver,
            isMainThreadWindow = { true }
        )
    }

    @Test
    fun `M return null W capture { no active RUM view }`(
        @Forgery fakeBounds: GlobalBounds,
        @LongForgery fakeTimestamp: Long
    ) {
        // Given
        val testedProducer = producer(
            windows = listOf(mockWindow(fakeBounds)),
            scope = null,
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        assertThat(snapshot).isNull()
        verify(mockTouchPrivacyManager).replaceCurrentTouchOverrideAreas(emptyMap())
    }

    @Test
    fun `M return null W capture { no windows }`(
        @StringForgery fakeViewId: String,
        @LongForgery(min = 0L) fakeOffset: Long,
        @LongForgery fakeTimestamp: Long
    ) {
        // Given
        val testedProducer = producer(
            windows = emptyList(),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        assertThat(snapshot).isNull()
    }

    @Test
    fun `M stamp the timestamp from device time plus view offset W capture()`(
        @Forgery fakeBounds: GlobalBounds,
        @StringForgery fakeViewId: String,
        @LongForgery(min = 0L, max = 100_000L) fakeOffset: Long,
        @LongForgery(min = 0L, max = Long.MAX_VALUE / 2) fakeTimestamp: Long
    ) {
        // Given
        val testedProducer = producer(
            windows = listOf(mockWindow(fakeBounds)),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val result = testedProducer.capture(fakeContext).doneValue()

        // Then
        val snapshot = checkNotNull(result) { "Expected a captured snapshot with the current RUM scope" }
        assertThat(snapshot.timestamp).isEqualTo(fakeTimestamp + fakeOffset)
        assertThat(snapshot.scope).isEqualTo(RumViewIdentityScope(fakeViewId))
        verify(mockTouchPrivacyManager).replaceCurrentTouchOverrideAreas(emptyMap())
    }

    @Test
    fun `M assemble windows under one synthetic root in order W capture { multiple windows }`(
        @Forgery fakeFirstBounds: GlobalBounds,
        @Forgery fakeSecondBounds: GlobalBounds,
        @StringForgery fakeViewId: String,
        @LongForgery(min = 0L) fakeOffset: Long,
        @LongForgery fakeTimestamp: Long
    ) {
        // Given
        val mockFirstWindow = mockWindow(fakeFirstBounds)
        val mockSecondWindow = mockWindow(fakeSecondBounds)
        val testedProducer = producer(
            windows = listOf(mockFirstWindow, mockSecondWindow),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        val capturedSnapshot = checkNotNull(snapshot) { "Expected a snapshot containing both windows" }
        val root = checkNotNull(capturedSnapshot.root) { "Expected a synthetic screen root" }
        assertThat(root.kind).isEqualTo(CapturedLayerKind.SYNTHETIC_SCREEN_ROOT)
        assertThat(root.children).hasSize(2)
        val windowLayerIds = capturedSnapshot.layers
            .filter { it.kind == CapturedLayerKind.WINDOW_ROOT }
            .map { it.identity }
        assertThat(root.children.map { it.identity }).isEqualTo(windowLayerIds)
        assertThat(root.children.map { it.identity.localId }).containsExactly(
            mockViewIdentifierResolver.resolveViewId(mockFirstWindow).toString(),
            mockViewIdentifierResolver.resolveViewId(mockSecondWindow).toString()
        )
    }

    @Test
    fun `M produce a snapshot that passes full validation W capture { mixed tree }`(
        @Forgery fakeWindowBounds: GlobalBounds,
        @Forgery fakeTextBounds: GlobalBounds,
        @StringForgery fakeViewId: String,
        @LongForgery(min = 0L) fakeOffset: Long,
        @LongForgery fakeTimestamp: Long,
        @StringForgery fakeText: String,
        @IntForgery(min = 0, max = 0xFFFFFF) fakeTextColor: Int
    ) {
        // Given
        val mockWindow = mockWindow(fakeWindowBounds)
        val mockTextView: TextView = mock {
            on { isShown } doReturn true
            on { width } doReturn fakeTextBounds.width.toInt()
            on { height } doReturn fakeTextBounds.height.toInt()
            on { getTag(any()) } doReturn null
            on { text } doReturn fakeText
            on { background } doReturn null
            on { currentTextColor } doReturn fakeTextColor
        }
        whenever(mockViewIdentifierResolver.resolveViewId(mockTextView)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeTextBounds)
        whenever(mockWindow.childCount).thenReturn(1)
        whenever(mockWindow.getChildAt(0)).thenReturn(mockTextView)
        val testedProducer = producer(
            windows = listOf(mockWindow),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        val capturedSnapshot = checkNotNull(snapshot) { "Expected a snapshot containing the mixed tree" }
        val validation = DefaultCapturedTreeValidator().validate(capturedSnapshot)
        assertThat(validation).isEqualTo(CaptureValidationResult.Valid)
    }
}
