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
import com.datadog.android.internal.heatmaps.HeatmapIdentifierRegistry
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedTextViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewGroupFallbackMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.HeatmapIdentifierResolver
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
        val timeProvider: TimeProvider = mock()
        whenever(timeProvider.getDeviceTimestampMillis()).thenReturn(fakeDeviceTimestampMs)
        return AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = RumViewScopeProvider { scope },
            timeProvider = timeProvider,
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
        val snapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        assertThat(snapshot?.timestamp).isEqualTo(fakeTimestamp + fakeOffset)
        assertThat(snapshot?.scope).isEqualTo(RumViewIdentityScope(fakeViewId))
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
        val firstWindow = mockWindow(fakeFirstBounds)
        val secondWindow = mockWindow(fakeSecondBounds)
        val testedProducer = producer(
            windows = listOf(firstWindow, secondWindow),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val snapshot = testedProducer.capture(fakeContext).doneValue()

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
        val snapshot = testedProducer.capture(fakeContext).doneValue()

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
        val mockTextView: TextView = mock()
        whenever(mockTextView.isShown).thenReturn(true)
        whenever(mockTextView.width).thenReturn(fakeBounds.width.toInt())
        whenever(mockTextView.height).thenReturn(fakeBounds.height.toInt())
        whenever(mockTextView.text).thenReturn("unchanged text")
        whenever(mockTextView.compoundDrawables).thenReturn(arrayOfNulls(4))
        whenever(mockViewIdentifierResolver.resolveViewId(mockTextView)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockTextView, fakeDensity)).thenReturn(fakeBounds)
        whenever(window.childCount).thenReturn(1)
        whenever(window.getChildAt(0)).thenReturn(mockTextView)
        val testedProducer = producer(
            windows = listOf(window),
            scope = CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset),
            fakeDeviceTimestampMs = fakeTimestamp
        )

        // When
        val firstSnapshot = testedProducer.capture(fakeContext).doneValue()
        val secondSnapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        val firstWindowLayerId = firstSnapshot!!.layers
            .single { it.kind == CapturedLayerKind.WINDOW_ROOT }.identity.wireId
        val secondWindowLayerId = secondSnapshot!!.layers
            .single { it.kind == CapturedLayerKind.WINDOW_ROOT }.identity.wireId
        assertThat(secondWindowLayerId).isEqualTo(firstWindowLayerId)
        assertThat(secondSnapshot.root?.identity).isEqualTo(firstSnapshot.root?.identity)
        assertThat(firstSnapshot.wireframes).isNotEmpty()
        assertThat(secondSnapshot.wireframes).isEqualTo(firstSnapshot.wireframes)
        assertThat(CapturedSnapshotDiffer.diff(firstSnapshot, secondSnapshot)).isEqualTo(
            CapturedMutationSet(timestamp = secondSnapshot.timestamp, scope = secondSnapshot.scope)
        )
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
            touchPrivacyManager = mockTouchPrivacyManager,
            isMainThreadWindow = { true },
            viewIdentifierResolver = mockViewIdentifierResolver
        )

        // When
        val firstSnapshot = testedProducer.capture(fakeContext).doneValue()
        scope = CapturedRumViewScope(RumViewIdentityScope(fakeOtherViewId), fakeOffset)
        val secondSnapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        assertThat(firstSnapshot!!.scope).isEqualTo(RumViewIdentityScope(fakeViewId))
        assertThat(secondSnapshot!!.scope).isEqualTo(RumViewIdentityScope(fakeOtherViewId))
    }

    @Test
    fun `M forward the RUM view URL into the traversal W capture { heatmap resolver configured }`(
        @Forgery fakeBounds: GlobalBounds,
        @StringForgery fakeViewId: String,
        @StringForgery fakeViewUrl: String,
        @LongForgery(min = 0L) fakeOffset: Long,
        @LongForgery fakeTimestamp: Long
    ) {
        // Given
        val window = mockWindow(fakeBounds).apply {
            whenever(isClickable).thenReturn(true)
            whenever(visibility).thenReturn(View.VISIBLE)
        }
        val resolver = HeatmapIdentifierResolver(
            appPackageName = "com.example.app",
            registry = HeatmapIdentifierRegistry.create(),
            internalLogger = mock()
        )
        val markerMapper = CapturedViewMapper<View> { view, mappingContext ->
            CapturedViewMapperResult.Wireframes(
                listOf(
                    CapturedWireframe.Shape(
                        identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                        bounds = mockViewBoundsResolver.resolveViewGlobalBounds(view, fakeDensity).toCaptured()
                    )
                )
            )
        }
        val windowSource = ActiveWindowSource().apply { update(listOf(window)) }
        val timeProvider: TimeProvider = mock()
        whenever(timeProvider.getDeviceTimestampMillis()).thenReturn(fakeTimestamp)
        val testedProducer = AndroidCapturedSnapshotProducer(
            windowSource = windowSource,
            scopeProvider = RumViewScopeProvider {
                CapturedRumViewScope(RumViewIdentityScope(fakeViewId), fakeOffset, fakeViewUrl)
            },
            timeProvider = timeProvider,
            traversal = AndroidWindowTraversal(
                mapperRegistry = CapturedViewMapperRegistry(
                    mappers = emptyList(),
                    fallbackMapper = markerMapper,
                    internalLogger = mock()
                ),
                viewIdentifierResolver = mockViewIdentifierResolver,
                viewBoundsResolver = mockViewBoundsResolver,
                viewUtilsInternal = ViewUtilsInternal(),
                internalLogger = mock(),
                heatmapResolver = resolver
            ),
            viewIdentifierResolver = mockViewIdentifierResolver,
            touchPrivacyManager = mockTouchPrivacyManager,
            isMainThreadWindow = { true }
        )

        // When
        val snapshot = testedProducer.capture(fakeContext).doneValue()

        // Then
        val windowWireframe = snapshot!!.wireframes.single()
        assertThat(windowWireframe.permanentId).isNotNull()
    }
}
