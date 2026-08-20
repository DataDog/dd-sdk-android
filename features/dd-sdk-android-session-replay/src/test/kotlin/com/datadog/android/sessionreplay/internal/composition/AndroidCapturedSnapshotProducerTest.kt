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
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
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
import org.mockito.kotlin.mock
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

    private fun mockWindow(bounds: GlobalBounds): ViewGroup {
        val view: ViewGroup = mock()
        whenever(view.isShown).thenReturn(true)
        whenever(view.width).thenReturn(bounds.width.toInt())
        whenever(view.height).thenReturn(bounds.height.toInt())
        whenever(view.getTag(any())).thenReturn(null)
        whenever(view.childCount).thenReturn(0)
        whenever(mockViewIdentifierResolver.resolveViewId(view)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, fakeDensity)).thenReturn(bounds)
        val mockResources: Resources = mock()
        val metrics = DisplayMetrics().apply { density = fakeDensity }
        whenever(mockResources.displayMetrics).thenReturn(metrics)
        whenever(view.resources).thenReturn(mockResources)
        return view
    }

    private fun traversal() = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(
            mappers = listOf(
                CapturedMapperTypeWrapper(TextView::class.java, CapturedTextViewMapper(internalLogger = mock()))
            ),
            fallbackMapper = CapturedViewGroupFallbackMapper(internalLogger = mock()),
            internalLogger = mock()
        ),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        viewUtilsInternal = ViewUtilsInternal()
    )

    private fun producer(
        windows: List<View>,
        scope: CapturedRumViewScope?,
        fakeDeviceTimestampMs: Long
    ): AndroidCapturedSnapshotProducer {
        val windowSource = ActiveWindowSource().apply { update(windows) }
        val timeProvider: TimeProvider = mock()
        whenever(timeProvider.getDeviceTimestampMillis()).thenReturn(fakeDeviceTimestampMs)
        return AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = RumViewScopeProvider { scope },
            timeProvider = timeProvider,
            traversal = traversal(),
            viewIdentifierResolver = mockViewIdentifierResolver
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
        val snapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

        // Then
        assertThat(snapshot).isNull()
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
        val snapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

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
        val snapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

        // Then
        assertThat(snapshot?.timestamp).isEqualTo(fakeTimestamp + fakeOffset)
        assertThat(snapshot?.scope).isEqualTo(RumViewIdentityScope(fakeViewId))
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
        val firstWindow = mockWindow(fakeFirstBounds)
        val secondWindow = mockWindow(fakeSecondBounds)
        val testedProducer = producer(
            windows = listOf(firstWindow, secondWindow),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

        // Then
        val root = snapshot!!.root!!
        assertThat(root.kind).isEqualTo(CapturedLayerKind.SYNTHETIC_SCREEN_ROOT)
        assertThat(root.children).hasSize(2)
        val windowLayerIds = snapshot.layers
            .filter { it.kind == CapturedLayerKind.WINDOW_ROOT }
            .map { it.identity }
        assertThat(root.children.map { it.identity }).isEqualTo(windowLayerIds)
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
        val window = mockWindow(fakeWindowBounds)
        val textView: TextView = mock()
        whenever(textView.isShown).thenReturn(true)
        whenever(textView.width).thenReturn(fakeTextBounds.width.toInt())
        whenever(textView.height).thenReturn(fakeTextBounds.height.toInt())
        whenever(textView.getTag(any())).thenReturn(null)
        whenever(textView.text).thenReturn(fakeText)
        whenever(textView.background).thenReturn(null)
        whenever(textView.currentTextColor).thenReturn(fakeTextColor)
        whenever(textView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockViewIdentifierResolver.resolveViewId(textView)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(textView, fakeDensity)).thenReturn(fakeTextBounds)
        whenever(window.childCount).thenReturn(1)
        whenever(window.getChildAt(0)).thenReturn(textView)
        val testedProducer = producer(
            windows = listOf(window),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

        // Then
        val validation = DefaultCapturedTreeValidator().validate(snapshot!!)
        assertThat(validation).isEqualTo(CaptureValidationResult.Valid)
    }

    @Test
    fun `M keep the same wire id across generations W capture { same RUM view scope }`(
        @Forgery fakeBounds: GlobalBounds,
        @StringForgery fakeViewId: String,
        @LongForgery(min = 0L) fakeOffset: Long,
        @LongForgery fakeTimestamp: Long
    ) {
        // Given
        val window = mockWindow(fakeBounds)
        val testedProducer = producer(
            windows = listOf(window),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val firstSnapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot
        val secondSnapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

        // Then
        val firstWindowLayerId = firstSnapshot!!.layers
            .single { it.kind == CapturedLayerKind.WINDOW_ROOT }.identity.wireId
        val secondWindowLayerId = secondSnapshot!!.layers
            .single { it.kind == CapturedLayerKind.WINDOW_ROOT }.identity.wireId
        assertThat(secondWindowLayerId).isEqualTo(firstWindowLayerId)
    }

    @Test
    fun `M start a fresh identity space W capture { RUM view scope changes }`(
        @Forgery fakeBounds: GlobalBounds,
        @StringForgery fakeViewId: String,
        @StringForgery fakeOtherViewId: String,
        @LongForgery(min = 0L) fakeOffset: Long,
        @LongForgery fakeTimestamp: Long
    ) {
        // Given
        val window = mockWindow(fakeBounds)
        var scope: CapturedRumViewScope? = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset)
        val timeProvider: TimeProvider = mock()
        whenever(timeProvider.getDeviceTimestampMillis()).thenReturn(fakeTimestamp)
        val testedProducer = AndroidCapturedSnapshotProducer(
            windowSource = ActiveWindowSource().apply { update(listOf(window)) },
            scopeProvider = RumViewScopeProvider { scope },
            timeProvider = timeProvider,
            traversal = traversal(),
            viewIdentifierResolver = mockViewIdentifierResolver
        )

        // When
        val firstSnapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot
        scope = CapturedRumViewScope(RumViewIdentityScope(fakeOtherViewId), fakeOffset)
        val secondSnapshot = testedProducer.capture(fakeContext, CaptureChangeset.EMPTY)?.snapshot

        // Then
        assertThat(firstSnapshot!!.scope).isEqualTo(RumViewIdentityScope(fakeViewId))
        assertThat(secondSnapshot!!.scope).isEqualTo(RumViewIdentityScope(fakeOtherViewId))
    }
}
