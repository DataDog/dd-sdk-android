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
import com.datadog.android.api.InternalLogger
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.KStubbing
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import java.util.concurrent.atomic.AtomicLong

internal abstract class AndroidWindowTraversalTestFixture {

    protected val mockViewBoundsResolver: ViewBoundsResolver = mock()
    protected val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
    protected val mockDrawableToColorMapper: DrawableToColorMapper = mock()
    private lateinit var nextViewId: AtomicLong
    protected lateinit var identityFactory: DefaultCapturedIdentityFactory
    protected lateinit var fakeContext: CaptureGenerationContext
    protected var fakeDensity: Float = 1f

    protected val noOpFallback = CapturedViewMapper<View> { _, _ -> CapturedViewMapperResult.None }
    protected val markerMapper = CapturedViewMapper<View> { view, mappingContext ->
        val bounds = mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        CapturedViewMapperResult.Wireframes(
            CapturedWireframe.Shape(
                identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                bounds = CapturedBounds(bounds.x, bounds.y, bounds.width, bounds.height)
            )
        )
    }

    @BeforeEach
    fun `set up`(
        forge: Forge,
        @StringForgery fakeScope: String,
        @LongForgery(min = 1L, max = 1_000_000L) fakeViewIdSeed: Long,
        @FloatForgery(min = 0.75f, max = 4f) fakeDensityForgery: Float
    ) {
        nextViewId = AtomicLong(fakeViewIdSeed)
        identityFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScope))
        fakeDensity = fakeDensityForgery
        fakeContext = CaptureGenerationContext(
            id = forge.aLong(min = 1L),
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider { 0L }
        )
    }

    protected fun mockView(bounds: GlobalBounds): View = mockViewWithDefaults(bounds)

    protected fun mockViewGroup(bounds: GlobalBounds): ViewGroup = mockViewWithDefaults(bounds) {
        on { clipChildren } doReturn true
    }

    internal inline fun <reified T : View> mockViewWithDefaults(
        fakeBounds: GlobalBounds,
        crossinline customize: KStubbing<T>.() -> Unit = {}
    ): T {
        val fakeMetrics = DisplayMetrics().apply { density = fakeDensity }
        val mockResources: Resources = mock { on { displayMetrics } doReturn fakeMetrics }
        val mockView: T = mock {
            on { isShown } doReturn true
            on { scaleX } doReturn 1f
            on { scaleY } doReturn 1f
            on { left } doReturn fakeBounds.x.toInt()
            on { top } doReturn fakeBounds.y.toInt()
            on { width } doReturn fakeBounds.width.toInt().coerceAtLeast(1)
            on { height } doReturn fakeBounds.height.toInt().coerceAtLeast(1)
            on { getTag(any()) } doReturn null
            on { resources } doReturn mockResources
            customize()
        }
        whenever(mockViewIdentifierResolver.resolveViewId(mockView)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(mockView, fakeDensity)).thenReturn(fakeBounds)
        return mockView
    }

    protected fun ViewGroup.withChildren(vararg children: View) {
        stub {
            on { childCount } doReturn children.size
            children.forEachIndexed { index, child -> on { getChildAt(index) } doReturn child }
        }
    }

    protected fun traversal(
        fallback: CapturedViewMapper<View> = noOpFallback,
        typedMappers: List<CapturedMapperTypeWrapper<*>> = emptyList(),
        compositionHostDecomposer: CompositionHostDecomposer? = null,
        internalLogger: InternalLogger = mock()
    ) = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(typedMappers, fallback, mock()),
        internalLogger = internalLogger,
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        drawableToColorMapper = mockDrawableToColorMapper,
        viewUtilsInternal = ViewUtilsInternal(),
        compositionHostDecomposer = compositionHostDecomposer
    )

    /** These trees are tiny and the deadline is generous, so a call is expected to complete in one slice. */
    protected fun WindowCopyResult.doneValue(): WindowWalkResult = buildWindow(fakeContext)
}
