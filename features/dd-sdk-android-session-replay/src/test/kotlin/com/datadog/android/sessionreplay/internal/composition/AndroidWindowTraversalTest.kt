/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.webkit.WebView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.TouchPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedMapperTypeWrapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedWebViewMapper
import com.datadog.android.sessionreplay.internal.recorder.ViewUtilsInternal
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
internal class AndroidWindowTraversalTest {

    private val mockViewBoundsResolver: ViewBoundsResolver = mock()
    private val mockViewIdentifierResolver: ViewIdentifierResolver = mock()
    private val mockDrawableToColorMapper: DrawableToColorMapper = mock()
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
        whenever(view.clipChildren).thenReturn(true)
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
        internalLogger = mock(),
        viewIdentifierResolver = mockViewIdentifierResolver,
        viewBoundsResolver = mockViewBoundsResolver,
        drawableToColorMapper = mockDrawableToColorMapper,
        viewUtilsInternal = ViewUtilsInternal()
    )

    /** These trees are tiny and the deadline is generous, so a call is expected to complete in one slice. */
    private fun <T> CaptureStep<T>.doneValue(): T = (this as CaptureStep.Done<T>).value

    /** A view whose bounds fully cover whatever it's stacked on top of, painted with opaque black. */
    private fun mockOpaqueCoveringView(bounds: GlobalBounds): View {
        val view = mockView(bounds)
        val drawable: Drawable = mock()
        whenever(view.alpha).thenReturn(1f)
        whenever(view.scaleX).thenReturn(1f)
        whenever(view.scaleY).thenReturn(1f)
        whenever(view.background).thenReturn(drawable)
        whenever(mockDrawableToColorMapper.mapDrawableToColor(eq(drawable), any())).thenReturn(Color.BLACK)
        return view
    }

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
        val present = result.doneValue() as WindowWalkResult.Present
        assertThat(present.rootLayer.children).isEmpty()
        assertThat(present.layers).hasSize(1) // only the window root itself
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
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
        val present = result.doneValue() as WindowWalkResult.Present
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
        val present = result.doneValue() as WindowWalkResult.Present
        val hiddenLayer = present.layers.first { it.identity != present.rootLayer.identity }
        assertThat(hiddenLayer.children).hasSize(1)
        val placeholder = present.wireframes.single() as CapturedWireframe.PrivacyPlaceholder
        assertThat(placeholder.label).isEqualTo("Hidden")
        // No layer was created for grandChild since the hidden view's children are never visited.
        assertThat(present.layers).hasSize(2) // root + hiddenGroup only
    }

    @Test
    fun `M stop at WebView slot and continue siblings W visit { WebView has native descendants }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeBounds)
        val mockWebView: WebView = mock()
        stubDefaults(mockWebView, fakeBounds)
        val implementationChild = mockViewGroup(fakeBounds)
        val implementationGrandchild = mockView(fakeBounds)
        whenever(mockWebView.childCount).thenReturn(1)
        whenever(mockWebView.getChildAt(0)).thenReturn(implementationChild)
        whenever(implementationChild.childCount).thenReturn(1)
        whenever(implementationChild.getChildAt(0)).thenReturn(implementationGrandchild)
        val sibling = mockViewGroup(fakeBounds)
        val siblingChild = mockView(fakeBounds)
        whenever(sibling.childCount).thenReturn(1)
        whenever(sibling.getChildAt(0)).thenReturn(siblingChild)
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(mockWebView)
        whenever(root.getChildAt(1)).thenReturn(sibling)
        val slotId = mockViewIdentifierResolver.resolveViewId(mockWebView)
        val testedTraversal = traversal(
            fallback = markerMapper,
            typedMappers = listOf(
                CapturedMapperTypeWrapper(
                    WebView::class.java,
                    CapturedWebViewMapper(mockViewIdentifierResolver, mockViewBoundsResolver)
                )
            )
        )

        // When
        val present = testedTraversal.traverseWindow(
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then: root, the WebView slot, and the ordinary sibling subtree only.
        val slot = present.wireframes.filterIsInstance<CapturedWireframe.WebView>().single()
        assertThat(slot.identity.wireId).isEqualTo(slotId)
        assertThat(present.layers).hasSize(4)
        assertThat(present.wireframes).hasSize(4)
        val webViewLayer = present.layers.single { it.identity.localId == slotId.toString() }
        assertThat(webViewLayer.children).containsExactly(CapturedChild.Wireframe(slot.identity))
        verify(mockWebView, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(implementationChild)
        verify(mockViewIdentifierResolver, never()).resolveViewId(implementationGrandchild)
        verify(mockViewIdentifierResolver).resolveViewId(siblingChild)
    }

    @Test
    fun `M hide WebView before mapping slot W visit { WebView has hidden tag and native child }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeBounds)
        val mockWebView: WebView = mock()
        stubDefaults(mockWebView, fakeBounds)
        whenever(mockWebView.getTag(R.id.datadog_hidden)).thenReturn(true)
        val implementationChild = mockView(fakeBounds)
        whenever(mockWebView.childCount).thenReturn(1)
        whenever(mockWebView.getChildAt(0)).thenReturn(implementationChild)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(mockWebView)
        val testedTraversal = traversal(
            typedMappers = listOf(
                CapturedMapperTypeWrapper(
                    WebView::class.java,
                    CapturedWebViewMapper(mockViewIdentifierResolver, mockViewBoundsResolver)
                )
            )
        )

        // When
        val present = testedTraversal.traverseWindow(
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(present.wireframes.single()).isInstanceOf(CapturedWireframe.PrivacyPlaceholder::class.java)
        assertThat(present.layers).hasSize(2)
        verify(mockWebView, never()).getChildAt(any())
        verify(mockViewIdentifierResolver, never()).resolveViewId(implementationChild)
    }

    @Test
    fun `M traverse mapped container children W visit { dedicated mapper only describes own view }`(
        @Forgery fakeBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeBounds)
        val child = mockViewGroup(fakeBounds)
        val grandchild = mockView(fakeBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        whenever(child.childCount).thenReturn(1)
        whenever(child.getChildAt(0)).thenReturn(grandchild)
        val testedTraversal = traversal(
            fallback = markerMapper,
            typedMappers = listOf(
                CapturedMapperTypeWrapper(
                    ViewGroup::class.java,
                    CapturedViewMapper<ViewGroup> { view, context -> markerMapper.map(view, context) }
                )
            )
        )

        // When
        val present = testedTraversal.traverseWindow(
            root,
            identityFactory.window("window"),
            identityFactory,
            fakeContext
        ).doneValue() as WindowWalkResult.Present

        // Then
        assertThat(present.layers).hasSize(3)
        assertThat(present.wireframes).hasSize(3)
        verify(mockViewIdentifierResolver).resolveViewId(grandchild)
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
        val present = result.doneValue() as WindowWalkResult.Present
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
        val present = result.doneValue() as WindowWalkResult.Present
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
                // Not expired for the root's own pre-pop checks (checked every item now, not just
                // periodically), expired from the first child's pre-pop check onward.
                if (calls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(root, windowIdentity, identityFactory, expiringContext)

        // Then
        assertThat(result.doneValue()).isEqualTo(WindowWalkResult.Aborted)
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
                // Not expired for the root's own two pre-pop checks; expired by the time the
                // compose host handoff's own re-poll runs, immediately after it returns.
                if (calls <= 2) 0L else fakeDeadlineNs * 2
            }
        )
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mock(),
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
        assertThat(result.doneValue()).isEqualTo(WindowWalkResult.Aborted)
    }

    // region touch privacy tests

    @Test
    fun `M add a touch override area W visit { view tagged with touch privacy }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(child.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(child.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = fakeLocationX
            location[1] = fakeLocationY
            null
        }
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + child.width,
            fakeLocationY + child.height
        )
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M not add a touch override area W visit { view has no touch privacy tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal().traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
    }

    @Test
    fun `M log an error and not add an area W visit { invalid touch privacy tag }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @StringForgery fakeInvalidValue: String
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val child = mockView(fakeChildBounds)
        whenever(child.getTag(R.id.datadog_touch_privacy)).thenReturn(fakeInvalidValue)
        whenever(root.childCount).thenReturn(1)
        whenever(root.getChildAt(0)).thenReturn(child)
        val windowIdentity = identityFactory.window("window")
        val mockInternalLogger: InternalLogger = mock()
        val testedTraversal = AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), noOpFallback, mock()),
            internalLogger = mockInternalLogger,
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            viewUtilsInternal = ViewUtilsInternal()
        )

        // When
        val result = testedTraversal.traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas).isEmpty()
        verify(mockInternalLogger).log(
            level = eq(InternalLogger.Level.ERROR),
            targets = eq(listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY)),
            messageBuilder = any(),
            throwable = any(),
            onlyOnce = eq(false),
            additionalProperties = isNull()
        )
    }

    // endregion

    // region occlusion culling tests

    @Test
    fun `M skip mapping but still register touch override area W visit { fully covered by later opaque sibling }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val victim = mockView(fakeChildBounds)
        whenever(victim.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(victim.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = fakeLocationX
            location[1] = fakeLocationY
            null
        }
        // Drawn after (on top of) victim, with identical bounds - fully covers it.
        val coveringView = mockOpaqueCoveringView(fakeChildBounds)
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(victim)
        whenever(root.getChildAt(1)).thenReturn(coveringView)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        // Only the window root and the covering sibling were mapped - the occluded victim
        // contributed no wireframe of its own.
        assertThat(present.wireframes).hasSize(2)
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + victim.width,
            fakeLocationY + victim.height
        )
        assertThat((result.doneValue() as WindowWalkResult.Present).touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M still map both children W visit { later sibling only partially overlaps }`(
        @Forgery fakeRootBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val victimBounds = GlobalBounds(x = 0L, y = 0L, width = 100L, height = 100L)
        val partialCoverBounds = GlobalBounds(x = 0L, y = 0L, width = 50L, height = 50L)
        val victim = mockView(victimBounds)
        val partialCover = mockOpaqueCoveringView(partialCoverBounds)
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(victim)
        whenever(root.getChildAt(1)).thenReturn(partialCover)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        // Root + both children - the partial cover doesn't fully contain the victim, so nothing is culled.
        assertThat(present.wireframes).hasSize(3)
    }

    @Test
    fun `M still map both children W visit { later sibling fully overlaps but is translucent }`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeChildBounds: GlobalBounds
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val victim = mockView(fakeChildBounds)
        val translucentCover = mockOpaqueCoveringView(fakeChildBounds).apply {
            whenever(alpha).thenReturn(0.5f)
        }
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(victim)
        whenever(root.getChildAt(1)).thenReturn(translucentCover)
        val windowIdentity = identityFactory.window("window")

        // When
        val result = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, fakeContext)

        // Then
        val present = result.doneValue() as WindowWalkResult.Present
        // Root + both children - a translucent cover never counts as fully opaque, so nothing is culled.
        assertThat(present.wireframes).hasSize(3)
    }

    // endregion

    // region yield/resume tests

    /**
     * Drives a possibly-yielding walk to completion, resetting [resetSlice] before every resume -
     * standing in for the orchestrator's own [com.datadog.android.sessionreplay.internal.composition.SliceYieldClock.markStart]
     * call between slices, which this test simulates by resetting the fake clock's call counter
     * back to its initial "under budget" reading instead.
     */
    private fun driveToCompletion(
        first: CaptureStep<WindowWalkResult>,
        resetSlice: () -> Unit
    ): WindowWalkResult {
        var step = first
        var resumes = 0
        while (step is CaptureStep.Yielded) {
            check(++resumes < 100) { "resume loop did not converge" }
            resetSlice()
            step = step.resume()
        }
        return step.doneValue()
    }

    @Test
    fun `M yield mid walk and resume to the full result W slice budget is exceeded`(
        @Forgery fakeRootBounds: GlobalBounds,
        forge: Forge
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val children = List(3) { mockView(forge.getForgery<GlobalBounds>()) }
        whenever(root.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
        val windowIdentity = identityFactory.window("window")
        var calls = 0
        val yieldingContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider {
                calls++
                // Under the slice budget for a view's own two pre-pop checks; over budget once a
                // third check runs before this is reset between resumes.
                if (calls <= 2) 0L else 2_000L
            },
            sliceBudgetNs = 1_000L
        )

        // When
        val firstStep = traversal(fallback = markerMapper)
            .traverseWindow(root, windowIdentity, identityFactory, yieldingContext)

        // Then
        assertThat(firstStep).isInstanceOf(CaptureStep.Yielded::class.java)

        // When
        val result = driveToCompletion(firstStep) { calls = 0 }

        // Then
        val present = result as WindowWalkResult.Present
        // Root's own markerMapper wireframe, plus a Layer for each of the 3 children.
        assertThat(present.rootLayer.children).hasSize(4)
        assertThat(present.layers).hasSize(4) // root + 3 children
        assertThat(present.wireframes).hasSize(4) // markerMapper wireframe for root + each child
    }

    @Test
    fun `M still register touch override areas for a child visited after a resume`(
        @Forgery fakeRootBounds: GlobalBounds,
        @Forgery fakeFirstChildBounds: GlobalBounds,
        @Forgery fakeSecondChildBounds: GlobalBounds,
        @IntForgery(min = 0, max = 1000) fakeLocationX: Int,
        @IntForgery(min = 0, max = 1000) fakeLocationY: Int
    ) {
        // Given
        val root = mockViewGroup(fakeRootBounds)
        val firstChild = mockView(fakeFirstChildBounds)
        val secondChild = mockView(fakeSecondChildBounds)
        whenever(secondChild.getTag(R.id.datadog_touch_privacy)).thenReturn(TouchPrivacy.HIDE.name)
        whenever(secondChild.getLocationOnScreen(any())).thenAnswer {
            val location = it.getArgument<IntArray>(0)
            location[0] = fakeLocationX
            location[1] = fakeLocationY
            null
        }
        whenever(root.childCount).thenReturn(2)
        whenever(root.getChildAt(0)).thenReturn(firstChild)
        whenever(root.getChildAt(1)).thenReturn(secondChild)
        val windowIdentity = identityFactory.window("window")
        var calls = 0
        val yieldingContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider {
                calls++
                // Yields right after the root - before either child, including secondChild, is visited.
                if (calls <= 2) 0L else 2_000L
            },
            sliceBudgetNs = 1_000L
        )

        // When
        val firstStep = traversal().traverseWindow(root, windowIdentity, identityFactory, yieldingContext)
        val result = driveToCompletion(firstStep) { calls = 0 } as WindowWalkResult.Present

        // Then
        val expectedArea = Rect(
            fakeLocationX,
            fakeLocationY,
            fakeLocationX + secondChild.width,
            fakeLocationY + secondChild.height
        )
        assertThat(result.touchOverrideAreas)
            .containsEntry(expectedArea, TouchPrivacy.HIDE)
    }

    @Test
    fun `M produce the same tree W walk yields repeatedly vs not at all`(
        @Forgery fakeRootBounds: GlobalBounds,
        forge: Forge
    ) {
        // Given - the exact same tree, walked by two separate traversal instances
        val bounds = List(4) { forge.getForgery<GlobalBounds>() }
        val windowIdentity = identityFactory.window("window")

        fun freshRootWithChildren(): ViewGroup {
            val root = mockViewGroup(fakeRootBounds)
            val children = bounds.map { mockView(it) }
            whenever(root.childCount).thenReturn(children.size)
            children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
            return root
        }

        val neverYieldingContext = CaptureGenerationContext(
            id = 1L,
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider { 0L },
            sliceBudgetNs = Long.MAX_VALUE / 2
        )
        var yieldingCalls = 0
        val alwaysYieldingContext = CaptureGenerationContext(
            id = 2L,
            startedAtNs = 0L,
            deadlineNs = Long.MAX_VALUE / 2,
            timeProvider = CaptureTimeProvider {
                yieldingCalls++
                // Under budget for exactly one item's two pre-pop checks per slice (reset before
                // every resume below), over budget for the next item's - one item per slice.
                if (yieldingCalls <= 2) 0L else 2_000L
            },
            sliceBudgetNs = 1_000L
        )

        // When
        val baseline = traversal(fallback = markerMapper)
            .traverseWindow(freshRootWithChildren(), windowIdentity, identityFactory, neverYieldingContext)
            .doneValue() as WindowWalkResult.Present
        val firstStep = traversal(fallback = markerMapper)
            .traverseWindow(freshRootWithChildren(), windowIdentity, identityFactory, alwaysYieldingContext)
        val yielded = driveToCompletion(firstStep) { yieldingCalls = 0 } as WindowWalkResult.Present

        // Then - identities themselves differ (each freshRootWithChildren() call mints new mock
        // views with their own sequential ids), so bounds/counts are what must line up structurally.
        assertThat(yielded.wireframes.map { it.bounds }).isEqualTo(baseline.wireframes.map { it.bounds })
        assertThat(yielded.rootLayer.children).hasSize(baseline.rootLayer.children.size)
        assertThat(yielded.layers).hasSize(baseline.layers.size)
    }

    // endregion
}
