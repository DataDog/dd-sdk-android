/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedIdentity
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionClippingTest {

    @StringForgery
    lateinit var fakeScopeValue: String

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M honor clipping by the containing group W traverseWindow { child overflows parent }`(
        rootClipsChildren: Boolean,
        parentClipsChildren: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(rootClipsChildren)
        whenever(fakeFixture.mockParent.clipChildren).thenReturn(parentClipsChildren)

        // When
        val wireframe = fakeFixture.traverseWindow().wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild))

        // Then
        assertThat(wireframe.clip).isEqualTo(
            if (rootClipsChildren) CapturedClip(top = 20, bottom = 60, left = 20, right = 60) else null
        )
    }

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M honor padding independently W traverseWindow { parent has padding }`(
        clipChildren: Boolean,
        clipToPadding: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockParent.clipChildren).thenReturn(clipChildren)
        whenever(fakeFixture.mockParent.clipToPadding).thenReturn(clipToPadding)
        fakeFixture.padding(fakeFixture.mockParent, 10, 20, 30, 40)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isEqualTo(
            if (clipToPadding) CapturedClip(top = 40, bottom = 100, left = 30, right = 90) else null
        )
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockParent)).clip).isNull()
    }

    @Test
    fun `M retain overflow W traverseWindow { clipToPadding enabled without padding }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockParent.clipToPadding).thenReturn(true)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isNull()
    }

    @Test
    fun `M preserve ancestor clipping W traverseWindow { intervening group allows overflow }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(true)
        val mockIntermediate = fakeFixture.mockGroup(GlobalBounds(100, 100, 20, 20))
        fakeFixture.children(fakeFixture.mockParent, mockIntermediate)
        fakeFixture.children(mockIntermediate, fakeFixture.mockChild)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isEqualTo(
            CapturedClip(top = 20, bottom = 60, left = 20, right = 60)
        )
    }

    @Test
    fun `M intersect ancestor and padding clips W traverseWindow`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipToPadding).thenReturn(true)
        fakeFixture.padding(fakeFixture.mockRoot, 100, 100, 0, 0)
        whenever(fakeFixture.mockParent.clipToPadding).thenReturn(true)
        fakeFixture.padding(fakeFixture.mockParent, 10, 10, 10, 10)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isEqualTo(
            CapturedClip(top = 40, bottom = 70, left = 40, right = 70)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M clip a views own drawing only when requested by its parent W traverseWindow`(clipChildren: Boolean) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockParent.clipChildren).thenReturn(clipChildren)
        fakeFixture.wireframeBounds[fakeFixture.mockChild] = CapturedBounds(50, 50, 180, 180)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isEqualTo(
            if (clipChildren) CapturedClip(top = 10, bottom = 10, left = 10, right = 10) else null
        )
    }

    @Test
    fun `M preserve window boundary W traverseWindow { all groups allow overflow }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        fakeFixture.wireframeBounds[fakeFixture.mockChild] = CapturedBounds(-10, -20, 350, 360)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isEqualTo(
            CapturedClip(top = 20, bottom = 40, left = 10, right = 40)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain visible overflow during occlusion W traverseWindow { opaque sibling covers parent bounds }`(
        clipChildren: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(clipChildren)
        val mockCover = fakeFixture.mockView(GlobalBounds(80, 80, 80, 80))
        fakeFixture.makeOpaque(mockCover)
        fakeFixture.children(fakeFixture.mockRoot, fakeFixture.mockParent, mockCover)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframes.any { it.identity == fakeFixture.identities[fakeFixture.mockChild] })
            .isEqualTo(!clipChildren)
    }

    @Test
    fun `M preserve copied clipping constraints W buildWindow() { live clipping changes }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(true)
        fakeFixture.onMapped = { if (it == fakeFixture.mockParent) fakeFixture.nowNs = 10 }
        val testedCopy = fakeFixture.start()

        // When
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(false)
        val result = testedCopy.buildWindow(fakeFixture.context) as WindowWalkResult.Present

        // Then
        assertThat(testedCopy).isInstanceOf(WindowCopyResult.Present::class.java)
        assertThat(
            result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip
        ).isEqualTo(
            CapturedClip(top = 20, bottom = 60, left = 20, right = 60)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M keep sibling clipping independent W traverseWindow() { traversal leaves a padded subtree }`(
        fakeAdvanceClock: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockParent.clipToPadding).thenReturn(true)
        fakeFixture.padding(fakeFixture.mockParent, 10, 10, 10, 10)
        val mockSibling = fakeFixture.mockGroup(GlobalBounds(180, 180, 100, 100))
        val mockSiblingChild = fakeFixture.mockView(GlobalBounds(160, 160, 140, 140))
        fakeFixture.children(mockSibling, mockSiblingChild)
        fakeFixture.children(fakeFixture.mockRoot, fakeFixture.mockParent, mockSibling)
        if (fakeAdvanceClock) fakeFixture.onMapped = { fakeFixture.nowNs += 10L }

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip).isEqualTo(
            CapturedClip(top = 30, bottom = 70, left = 30, right = 70)
        )
        assertThat(result.wireframe(fakeFixture.identities.getValue(mockSiblingChild)).clip).isNull()
    }

    @ParameterizedTest
    @CsvSource("160, 80", "200, 80", "80, 160", "80, 200")
    fun `M preserve empty intersection W traverseWindow() { descendant bounds span disjoint clipping ancestors }`(
        fakeInnerX: Long,
        fakeInnerY: Long
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(true)
        whenever(fakeFixture.mockParent.clipChildren).thenReturn(true)
        val mockInner = fakeFixture.mockGroup(GlobalBounds(fakeInnerX, fakeInnerY, 80, 80))
        whenever(mockInner.clipChildren).thenReturn(true)
        val mockLeaf = fakeFixture.mockView(GlobalBounds(0, 0, 300, 300))
        fakeFixture.children(fakeFixture.mockParent, mockInner)
        fakeFixture.children(mockInner, mockLeaf)
        fakeFixture.onMapped = { fakeFixture.nowNs += 10L }

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(mockLeaf)).clip).isEqualTo(
            CapturedClip(top = fakeInnerY, bottom = 140, left = fakeInnerX, right = 140)
        )
    }

    @ParameterizedTest
    @CsvSource("128, false", "128, true", "1024, false", "1024, true")
    fun `M preserve tightest clipping edges W traverseWindow() { hierarchy is deeply nested }`(
        fakeDepth: Int,
        fakeAdvanceClock: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(true)
        var mockParent = fakeFixture.mockRoot
        repeat(fakeDepth) { index ->
            val fakeX = (index % 100).toLong()
            val fakeY = (index % 80).toLong()
            val mockGroup = fakeFixture.mockGroup(GlobalBounds(fakeX, fakeY, 300 - 2 * fakeX, 300 - 2 * fakeY))
            whenever(mockGroup.clipChildren).thenReturn(true)
            fakeFixture.children(mockParent, mockGroup)
            mockParent = mockGroup
        }
        val mockLeaf = fakeFixture.mockView(GlobalBounds(0, 0, 300, 300))
        fakeFixture.children(mockParent, mockLeaf)
        if (fakeAdvanceClock) fakeFixture.onMapped = { fakeFixture.nowNs++ }

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.layers).hasSize(fakeDepth + 2)
        assertThat(result.wireframes).hasSize(fakeDepth + 2)
        assertThat(result.wireframe(fakeFixture.identities.getValue(mockLeaf)).clip).isEqualTo(
            CapturedClip(top = 79, bottom = 79, left = 99, right = 99)
        )
    }

    @Test
    fun `M apply local clip bounds W traverseWindow() { leaf has explicit clip }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        fakeFixture.clip(fakeFixture.mockChild, 20, 40, 300, 280)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip)
            .isEqualTo(CapturedClip(top = 20, bottom = 20, left = 10, right = 10))
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M propagate explicit clip to descendants W traverseWindow() { group has clip bounds }`(
        fakeAdvanceClock: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        fakeFixture.clip(fakeFixture.mockParent, 20, 40, 100, 120)
        if (fakeAdvanceClock) fakeFixture.onMapped = { fakeFixture.nowNs += 10 }

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockParent)).clip)
            .isEqualTo(CapturedClip(top = 20, bottom = 20, left = 10, right = 30))
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip)
            .isEqualTo(CapturedClip(top = 40, bottom = 80, left = 30, right = 90))
    }

    @Test
    fun `M preserve empty explicit clip W traverseWindow() { group has empty clip bounds }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        fakeFixture.clip(fakeFixture.mockParent, 0, 0, 0, 0)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip)
            .isEqualTo(CapturedClip(top = 20, bottom = 140, left = 20, right = 140))
    }

    @Test
    fun `M intersect explicit and ancestor clips W traverseWindow() { local clip exceeds parent bounds }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockRoot.clipChildren).thenReturn(true)
        fakeFixture.clip(fakeFixture.mockChild, 80, 80, 400, 400)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(fakeFixture.mockChild)).clip)
            .isEqualTo(CapturedClip(top = 40, bottom = 60, left = 40, right = 60))
    }

    @Test
    fun `M keep explicit clips independent W traverseWindow() { siblings follow clipped subtree }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        fakeFixture.clip(fakeFixture.mockParent, 0, 0, 40, 40)
        val mockSibling = fakeFixture.mockView(GlobalBounds(180, 180, 80, 80))
        fakeFixture.children(fakeFixture.mockRoot, fakeFixture.mockParent, mockSibling)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.wireframe(fakeFixture.identities.getValue(mockSibling)).clip).isNull()
    }

    @ParameterizedTest
    @CsvSource("0.0, false", "0.5, false", "0.5, true", "1.0, true")
    fun `M capture subtree opacity once W traverseWindow() { view group has alpha }`(
        fakeAlpha: Float,
        fakeAdvanceClock: Boolean
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockParent.alpha).thenReturn(fakeAlpha)
        whenever(fakeFixture.mockChild.alpha).thenReturn(0.25f)
        if (fakeAdvanceClock) fakeFixture.onMapped = { fakeFixture.nowNs += 10 }

        // When
        val result = fakeFixture.traverseWindow()
        val parentLayer = result.layers.single { it.bounds == CapturedBounds(80, 80, 80, 80) }
        val childLayer = result.layers.single { it.bounds == CapturedBounds(60, 60, 160, 160) }

        // Then
        assertThat(parentLayer.modifiers).isEqualTo(
            if (fakeAlpha == 1f) {
                emptyList<CapturedModifier>()
            } else {
                listOf(CapturedModifier.Opacity(fakeAlpha.toDouble()))
            }
        )
        assertThat(childLayer.modifiers).containsExactly(CapturedModifier.Opacity(0.25))
    }

    private class Fixture(private val fakeScopeValue: String) {
        private val mockBoundsResolver: ViewBoundsResolver = mock()
        private val mockIdentifierResolver: ViewIdentifierResolver = mock()
        private val mockDrawableToColorMapper: DrawableToColorMapper = mock()
        private val bounds = mutableMapOf<View, GlobalBounds>()
        private val viewIds = mutableMapOf<View, Long>()
        val identities = mutableMapOf<View, CapturedIdentity>()
        val wireframeBounds = mutableMapOf<View, CapturedBounds>()
        val mockRoot = mockGroup(GlobalBounds(0, 0, 300, 300))
        val mockParent = mockGroup(GlobalBounds(80, 80, 80, 80))
        val mockChild = mockView(GlobalBounds(60, 60, 160, 160))
        var nowNs = 0L
        var onMapped: (View) -> Unit = {}
        val context = CaptureGenerationContext(1, 0, Long.MAX_VALUE, CaptureTimeProvider { nowNs })

        init {
            // Constant-time lookup keeps deep-tree tests from benchmarking mock argument matching.
            whenever(mockIdentifierResolver.resolveViewId(any())).thenAnswer { viewIds.getValue(it.getArgument(0)) }
            whenever(mockBoundsResolver.resolveViewGlobalBounds(any(), any()))
                .thenAnswer { bounds.getValue(it.getArgument(0)) }
            children(mockRoot, mockParent)
            children(mockParent, mockChild)
        }

        fun mockGroup(bounds: GlobalBounds): ViewGroup = mockViewWithBounds(bounds)
        fun mockView(bounds: GlobalBounds): View = mockViewWithBounds(bounds)

        private inline fun <reified T : View> mockViewWithBounds(fakeBounds: GlobalBounds): T {
            val fakeMetrics = DisplayMetrics().apply { density = DENSITY }
            val mockResources: Resources = mock { on { displayMetrics } doReturn fakeMetrics }
            val mockView: T = mock {
                on { isShown } doReturn true
                on { scaleX } doReturn 1f
                on { scaleY } doReturn 1f
                on { alpha } doReturn 1f
                on { getLocationOnScreen(any()) } doAnswer { invocation ->
                    val fakeCoordinates = invocation.getArgument<IntArray>(0)
                    fakeCoordinates[0] = (fakeBounds.x * DENSITY).toInt()
                    fakeCoordinates[1] = (fakeBounds.y * DENSITY).toInt()
                    null
                }
                on { left } doReturn (fakeBounds.x * DENSITY).toInt()
                on { top } doReturn (fakeBounds.y * DENSITY).toInt()
                on { width } doReturn (fakeBounds.width * DENSITY).toInt()
                on { height } doReturn (fakeBounds.height * DENSITY).toInt()
                on { resources } doReturn mockResources
            }
            bounds[mockView] = fakeBounds
            viewIds[mockView] = bounds.size.toLong()
            return mockView
        }

        fun children(mockParent: ViewGroup, vararg children: View) {
            whenever(mockParent.childCount).thenReturn(children.size)
            children.forEachIndexed { index, mockChild -> whenever(mockParent.getChildAt(index)).thenReturn(mockChild) }
        }

        fun padding(view: ViewGroup, left: Int, top: Int, right: Int, bottom: Int) {
            view.stub {
                on { paddingLeft } doReturn (left * DENSITY).toInt()
                on { paddingTop } doReturn (top * DENSITY).toInt()
                on { paddingRight } doReturn (right * DENSITY).toInt()
                on { paddingBottom } doReturn (bottom * DENSITY).toInt()
            }
            val area = bounds.getValue(view)
            whenever(mockBoundsResolver.resolveViewPaddedBounds(view, DENSITY)).thenReturn(
                GlobalBounds(area.x + left, area.y + top, area.width - left - right, area.height - top - bottom)
            )
        }

        fun clip(view: View, left: Int, top: Int, right: Int, bottom: Int) {
            val fakeClip = Rect().also {
                it.left = left
                it.top = top
                it.right = right
                it.bottom = bottom
            }
            whenever(view.clipBounds).thenReturn(fakeClip)
        }

        fun makeOpaque(view: View) {
            val mockBackground: ColorDrawable = mock()
            @Suppress("DEPRECATION") // Stub the same opacity check as the production occluder.
            whenever(mockBackground.opacity).thenReturn(PixelFormat.OPAQUE)
            view.stub {
                on { background } doReturn mockBackground
                on { alpha } doReturn 1f
                on { scaleX } doReturn 1f
                on { scaleY } doReturn 1f
            }
            whenever(mockDrawableToColorMapper.mapDrawableToColor(any(), any())).thenReturn(Color.BLACK)
        }

        fun start(): WindowCopyResult {
            val mapper = CapturedViewMapper<View> { view, mappingContext ->
                val identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity)
                identities[view] = identity
                onMapped(view)
                CapturedViewMapperResult.Wireframes(
                    CapturedWireframe.Shape(identity, wireframeBounds[view] ?: bounds.getValue(view).toCaptured())
                )
            }
            val testedTraversal = AndroidWindowTraversal(
                mapperRegistry = CapturedViewMapperRegistry(emptyList(), mapper, mock()),
                internalLogger = mock(),
                viewBoundsResolver = mockBoundsResolver,
                viewIdentifierResolver = mockIdentifierResolver,
                drawableToColorMapper = mockDrawableToColorMapper
            )
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScopeValue))
            return testedTraversal.traverseWindow(mockRoot, factory.window("window"), factory, context)
        }

        fun traverseWindow(): WindowWalkResult.Present = start().buildWindow(context) as WindowWalkResult.Present

        companion object {
            const val DENSITY = 2f
        }
    }

    private fun WindowWalkResult.Present.wireframe(identity: CapturedIdentity): CapturedWireframe =
        wireframes.single { it.identity == identity }
}
