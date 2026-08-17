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
import android.view.ViewStub
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
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
internal class AndroidWindowTraversalTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
    private lateinit var nextViewId: AtomicLong
    private lateinit var identityFactory: DefaultCapturedIdentityFactory
    private lateinit var fakeContext: CaptureGenerationContext
    private var fakeDensity: Float = 1f

    private val noOpFallback = CapturedViewMapper<View> { _, _ -> CapturedViewMapperResult.None }
    private val markerMapper = CapturedViewMapper<View> { view, mappingContext ->
        val bounds = mockViewBoundsResolver.resolveViewGlobalBounds(view, mappingContext.screenDensity)
        CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.Shape(
                    identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity),
                    bounds = CapturedBounds(bounds.x, bounds.y, bounds.width, bounds.height)
                )
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

    private fun mockView(bounds: GlobalBounds): View {
        val view: View = mock()
        stubDefaults(view, bounds)
        return view
    }

    private fun mockViewGroup(bounds: GlobalBounds): ViewGroup {
        val view: ViewGroup = mock()
        stubDefaults(view, bounds)
        return view
    }

    private fun stubDefaults(view: View, bounds: GlobalBounds) {
        whenever(view.isShown).thenReturn(true)
        whenever(view.width).thenReturn(bounds.width.toInt().coerceAtLeast(1))
        whenever(view.height).thenReturn(bounds.height.toInt().coerceAtLeast(1))
        whenever(view.getTag(any())).thenReturn(null)
        whenever(mockViewIdentifierResolver.resolveViewId(view)).thenReturn(nextViewId.getAndIncrement())
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(view, fakeDensity)).thenReturn(bounds)
        val mockResources: Resources = mock()
        val metrics = DisplayMetrics().apply { density = fakeDensity }
        whenever(mockResources.displayMetrics).thenReturn(metrics)
        whenever(view.resources).thenReturn(mockResources)
    }

    private fun traversal(
        fallback: CapturedViewMapper<View> = noOpFallback,
        typedMappers: List<CapturedMapperTypeWrapper<*>> = emptyList()
    ) = AndroidWindowTraversal(
        mapperRegistry = CapturedViewMapperRegistry(typedMappers, fallback, mock()),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        viewUtilsInternal = ViewUtilsInternal()
    )

    @Test
    fun `M drop the child W visit { not visible }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val hiddenChild = mockView(fakeChildBounds).apply { whenever(isShown).thenReturn(false) }
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(hiddenChild)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
        assertThat(present.layers).hasSize(1) // only the window root itself
    }

    @Test
    fun `M drop the child W visit { system noise }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val viewStubChild: ViewStub = mock()
        stubDefaults(viewStubChild, fakeChildBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(viewStubChild)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
    }

    @Test
    fun `M emit a placeholder and not recurse W visit { hidden tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeHiddenBounds: GlobalBounds,
        @Forgery fakeGrandChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val hiddenGroup = mockViewGroup(fakeHiddenBounds)
        whenever(hiddenGroup.getTag(any())).thenReturn(true)
        val grandChild = mockView(fakeGrandChildBounds)
        whenever(hiddenGroup.childCount).thenReturn(1)
        whenever(hiddenGroup.getChildAt(0)).thenReturn(grandChild)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(hiddenGroup)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        val hiddenLayer = present.layers.first { it.identity != present.rootLayer.identity }
        assertThat(hiddenLayer.children).hasSize(1)
        val placeholder = present.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo("Hidden")
        // No layer was created for grandChild since the hidden view's children are never visited.
        assertThat(present.layers).hasSize(2) // root + hiddenGroup only
    }

    @Test
    fun `M preserve child order W visit { multiple children }`(forge: Forge) {
        // Given
        val root = mockViewGroup(forge.getForgery<GlobalBounds>())
        val children = List(3) { mockView(forge.getForgery<GlobalBounds>()) }
        whenever(root.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        val childIdentities = present.rootLayer.children.map { it.identity }
        val expectedOrder = children.map { child ->
            present.layers.first { layer ->
                layer.identity.localId == mockViewIdentifierResolver.resolveViewId(child).toString()
            }.identity
        }
        assertThat(childIdentities).isEqualTo(expectedOrder)
    }

    @Test
    fun `M compute clip against ancestor bounds W child overflows parent`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 500L) fakeRightOverflow: Long,
        @LongForgery(min = 1L, max = 500L) fakeBottomOverflow: Long
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val childBounds = GlobalBounds(
            x = fakeRootBounds.x,
            y = fakeRootBounds.y,
            width = fakeRootBounds.width + fakeRightOverflow,
            height = fakeRootBounds.height + fakeBottomOverflow
        )
        val child = mockView(childBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(
            fallback = markerMapper
        ).traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result as WindowWalkResult.Present
        val childWireframe = present.wireframes.first {
            it.bounds.x == childBounds.x &&
                it.bounds.width == childBounds.width
        }
        assertThat(childWireframe.clip).isEqualTo(
            CapturedClip(top = null, bottom = fakeBottomOverflow, left = null, right = fakeRightOverflow)
        )
    }

    @Test
    fun `M abort the whole capture W deadline expires mid walk`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 1000L) fakeDeadlineNs: Long
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val children = List(5) { mockView(fakeRootBounds) }
        whenever(root.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
        val windowIdentity = identityFactory.window("window")
        var calls = 0
        val expiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = fakeDeadlineNs,
            timeProvider = CaptureTimeProvider {
                calls++
                // Not expired for the upfront per-window check and the first checkpoint,
                // expired from the second checkpoint onward.
                if (calls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal(),
            viewsPerCheckpoint = 1
        )

        // When
        val result = testedTraversal.traverseWindow(root, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result).isEqualTo(WindowWalkResult.Aborted)
    }

    @Test
    fun `M abort W deadline expires during compose host handoff {before next checkpoint tick}`(
        @Forgery fakeRootBounds: GlobalBounds,
        @LongForgery(min = 1L, max = 1000L) fakeDeadlineNs: Long
    ) {
        // Given
        val composeHost = androidx.compose.ui.platform.ComposeView(mock(), fakeDensity)
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(composeHost, fakeDensity)).thenReturn(fakeRootBounds)
        val windowIdentity = identityFactory.window("window")
        var calls = 0
        val expiringContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = fakeDeadlineNs,
            timeProvider = CaptureTimeProvider {
                calls++
                // Not expired for the upfront per-window check; expired by the time the
                // compose host handoff returns, well before a 200-view checkpoint would fire.
                if (calls <= 1) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            // The real ViewUtilsInternal calls isShown()/getWidth()/getHeight() on the view, which
            // are permanently stubbed to false/0/0 for any real (non-mocked) View here since View
            // isn't in this module's unMock keep-list - a mock instead, defaulting every check to
            // false, isolates this test from that unrelated limitation.
            viewUtilsInternal = mock(),
            composeHostCallback = CapturedInteropViewCallback { _, _ -> CapturedViewMapperResult.None }
        )

        // When
        val result = testedTraversal.traverseWindow(composeHost, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result).isEqualTo(WindowWalkResult.Aborted)
    }
}
