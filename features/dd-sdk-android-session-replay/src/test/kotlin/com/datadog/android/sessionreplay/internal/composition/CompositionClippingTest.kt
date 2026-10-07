/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class CompositionClippingTest {

    @ParameterizedTest
    @CsvSource("false, false", "false, true", "true, false", "true, true")
    fun `M honor clipping by the containing group W traverseWindow { child overflows parent }`(
        rootClipsChildren: Boolean,
        parentClipsChildren: Boolean
    ) {
        // Given: root's flag controls whether parent's entire subtree is clipped to parent's bounds.
        val fixture = Fixture()
        whenever(fixture.root.clipChildren).thenReturn(rootClipsChildren)
        whenever(fixture.parent.clipChildren).thenReturn(parentClipsChildren)

        // When
        val wireframe = fixture.capture().wireframe(fixture.identities.getValue(fixture.child))

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
        val fixture = Fixture()
        whenever(fixture.parent.clipChildren).thenReturn(clipChildren)
        whenever(fixture.parent.clipToPadding).thenReturn(clipToPadding)
        fixture.padding(fixture.parent, 10, 20, 30, 40)

        // When
        val result = fixture.capture()

        // Then: padding clips descendants, but never the parent's own background.
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isEqualTo(
            if (clipToPadding) CapturedClip(top = 40, bottom = 100, left = 30, right = 90) else null
        )
        assertThat(result.wireframe(fixture.identities.getValue(fixture.parent)).clip).isNull()
    }

    @Test
    fun `M retain overflow W traverseWindow { clipToPadding enabled without padding }`() {
        // Given
        val fixture = Fixture()
        whenever(fixture.parent.clipToPadding).thenReturn(true)

        // When
        val result = fixture.capture()

        // Then
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isNull()
    }

    @Test
    fun `M preserve ancestor clipping W traverseWindow { intervening group allows overflow }`() {
        // Given
        val fixture = Fixture()
        whenever(fixture.root.clipChildren).thenReturn(true)
        val intermediate = fixture.group(GlobalBounds(100, 100, 20, 20))
        fixture.children(fixture.parent, intermediate)
        fixture.children(intermediate, fixture.child)

        // When
        val result = fixture.capture()

        // Then: clip to parent, not the smaller non-clipping intermediate group.
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isEqualTo(
            CapturedClip(top = 20, bottom = 60, left = 20, right = 60)
        )
    }

    @Test
    fun `M intersect ancestor and padding clips W traverseWindow`() {
        // Given
        val fixture = Fixture()
        whenever(fixture.root.clipToPadding).thenReturn(true)
        fixture.padding(fixture.root, 100, 100, 0, 0)
        whenever(fixture.parent.clipToPadding).thenReturn(true)
        fixture.padding(fixture.parent, 10, 10, 10, 10)

        // When
        val result = fixture.capture()

        // Then: root constrains top/left, parent constrains bottom/right.
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isEqualTo(
            CapturedClip(top = 40, bottom = 70, left = 40, right = 70)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M clip a views own drawing only when requested by its parent W traverseWindow`(clipChildren: Boolean) {
        // Given: a mapper can emit content outside its owner's bounds.
        val fixture = Fixture()
        whenever(fixture.parent.clipChildren).thenReturn(clipChildren)
        fixture.wireframeBounds[fixture.child] = CapturedBounds(50, 50, 180, 180)

        // When
        val result = fixture.capture()

        // Then
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isEqualTo(
            if (clipChildren) CapturedClip(top = 10, bottom = 10, left = 10, right = 10) else null
        )
    }

    @Test
    fun `M preserve window boundary W traverseWindow { all groups allow overflow }`() {
        // Given
        val fixture = Fixture()
        fixture.wireframeBounds[fixture.child] = CapturedBounds(-10, -20, 350, 360)

        // When
        val result = fixture.capture()

        // Then
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isEqualTo(
            CapturedClip(top = 20, bottom = 40, left = 10, right = 40)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M retain visible overflow during occlusion W traverseWindow { opaque sibling covers parent bounds }`(
        clipChildren: Boolean
    ) {
        // Given
        val fixture = Fixture()
        whenever(fixture.root.clipChildren).thenReturn(clipChildren)
        val cover = fixture.view(GlobalBounds(80, 80, 80, 80))
        fixture.makeOpaque(cover)
        fixture.children(fixture.root, fixture.parent, cover)

        // When
        val result = fixture.capture()

        // Then: covering parent's rectangle hides the whole subtree only if it cannot overflow.
        assertThat(result.wireframes.any { it.identity == fixture.identities[fixture.child] })
            .isEqualTo(!clipChildren)
    }

    @Test
    fun `M preserve clipping constraints W resume { traversal yields after parent }`() {
        // Given
        val fixture = Fixture()
        whenever(fixture.root.clipChildren).thenReturn(true)
        fixture.onMapped = { if (it == fixture.parent) fixture.nowNs = 10 }
        val step = fixture.start()
        assertThat(step).isInstanceOf(CaptureStep.Yielded::class.java)

        // When
        fixture.context.sliceClock.markStart()
        val result = (step as CaptureStep.Yielded).resume() as CaptureStep.Done

        // Then
        assertThat(
            (result.value as WindowWalkResult.Present).wireframe(fixture.identities.getValue(fixture.child)).clip
        ).isEqualTo(
            CapturedClip(top = 20, bottom = 60, left = 20, right = 60)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M keep sibling clipping independent W traversal leaves a padded subtree`(fakeYield: Boolean) {
        // Given: the first subtree clips to padding; the second allows overflow up to the window.
        val fixture = Fixture()
        whenever(fixture.parent.clipToPadding).thenReturn(true)
        fixture.padding(fixture.parent, 10, 10, 10, 10)
        val sibling = fixture.group(GlobalBounds(180, 180, 100, 100))
        val siblingChild = fixture.view(GlobalBounds(160, 160, 140, 140))
        fixture.children(sibling, siblingChild)
        fixture.children(fixture.root, fixture.parent, sibling)
        if (fakeYield) fixture.onMapped = { fixture.nowNs += 10L }

        // When
        val result = fixture.capture()

        // Then
        assertThat(result.wireframe(fixture.identities.getValue(fixture.child)).clip).isEqualTo(
            CapturedClip(top = 30, bottom = 70, left = 30, right = 70)
        )
        assertThat(result.wireframe(fixture.identities.getValue(siblingChild)).clip).isNull()
        assertThat(fixture.resumes > 0).isEqualTo(fakeYield)
    }

    @ParameterizedTest
    @CsvSource("160, 80", "200, 80", "80, 160", "80, 200")
    fun `M preserve empty intersection W descendant bounds span disjoint clipping ancestors`(
        fakeInnerX: Long,
        fakeInnerY: Long
    ) {
        // Given: touching or disjoint clipping rectangles, followed by a descendant spanning both.
        val fixture = Fixture()
        whenever(fixture.root.clipChildren).thenReturn(true)
        whenever(fixture.parent.clipChildren).thenReturn(true)
        val inner = fixture.group(GlobalBounds(fakeInnerX, fakeInnerY, 80, 80))
        whenever(inner.clipChildren).thenReturn(true)
        val leaf = fixture.view(GlobalBounds(0, 0, 300, 300))
        fixture.children(fixture.parent, inner)
        fixture.children(inner, leaf)
        fixture.onMapped = { fixture.nowNs += 10L }

        // When
        val result = fixture.capture()

        // Then: the inverted/zero-width intersection must not turn into an absent or expanded clip.
        assertThat(result.wireframe(fixture.identities.getValue(leaf)).clip).isEqualTo(
            CapturedClip(top = fakeInnerY, bottom = 140, left = fakeInnerX, right = 140)
        )
        assertThat(fixture.resumes).isGreaterThan(0)
    }

    @ParameterizedTest
    @CsvSource("128, false", "128, true", "1024, false", "1024, true")
    fun `M preserve tightest clipping edges W hierarchy is deeply nested`(fakeDepth: Int, fakeYield: Boolean) {
        // Given: ancestors constrain different edges, then widen again without undoing earlier clips.
        val fixture = Fixture()
        whenever(fixture.root.clipChildren).thenReturn(true)
        var parent = fixture.root
        repeat(fakeDepth) { index ->
            val x = (index % 100).toLong()
            val y = (index % 80).toLong()
            val group = fixture.group(GlobalBounds(x, y, 300 - 2 * x, 300 - 2 * y))
            whenever(group.clipChildren).thenReturn(true)
            fixture.children(parent, group)
            parent = group
        }
        val leaf = fixture.view(GlobalBounds(0, 0, 300, 300))
        fixture.children(parent, leaf)
        if (fakeYield) fixture.onMapped = { fixture.nowNs++ }

        // When
        val result = fixture.capture()

        // Then
        assertThat(result.layers).hasSize(fakeDepth + 2)
        assertThat(result.wireframes).hasSize(fakeDepth + 2)
        assertThat(result.wireframe(fixture.identities.getValue(leaf)).clip).isEqualTo(
            CapturedClip(top = 79, bottom = 79, left = 99, right = 99)
        )
        assertThat(fixture.resumes > 0).isEqualTo(fakeYield)
    }

    private class Fixture {
        private val mockBoundsResolver: ViewBoundsResolver = mock()
        private val mockIdentifierResolver: ViewIdentifierResolver = mock()
        private val mockDrawableToColorMapper: DrawableToColorMapper = mock()
        private val bounds = mutableMapOf<View, GlobalBounds>()
        private val viewIds = mutableMapOf<View, Long>()
        val identities = mutableMapOf<View, CapturedIdentity>()
        val wireframeBounds = mutableMapOf<View, CapturedBounds>()
        val root = group(GlobalBounds(0, 0, 300, 300))
        val parent = group(GlobalBounds(80, 80, 80, 80))
        val child = view(GlobalBounds(60, 60, 160, 160))
        var nowNs = 0L
        var resumes = 0
            private set
        var onMapped: (View) -> Unit = {}
        val context = CaptureGenerationContext(1, 0, Long.MAX_VALUE, CaptureTimeProvider { nowNs }, sliceBudgetNs = 10)

        init {
            // Constant-time lookup keeps deep-tree tests from benchmarking mock argument matching.
            whenever(mockIdentifierResolver.resolveViewId(any())).thenAnswer { viewIds.getValue(it.getArgument(0)) }
            whenever(mockBoundsResolver.resolveViewGlobalBounds(any(), any()))
                .thenAnswer { bounds.getValue(it.getArgument(0)) }
            children(root, parent)
            children(parent, child)
        }

        fun group(bounds: GlobalBounds): ViewGroup = mock<ViewGroup>().also { configure(it, bounds) }
        fun view(bounds: GlobalBounds): View = mock<View>().also { configure(it, bounds) }

        private fun configure(view: View, fakeBounds: GlobalBounds) {
            bounds[view] = fakeBounds
            viewIds[view] = bounds.size.toLong()
            whenever(view.isShown).thenReturn(true)
            whenever(view.width).thenReturn((fakeBounds.width * DENSITY).toInt())
            whenever(view.height).thenReturn((fakeBounds.height * DENSITY).toInt())
            val resources: Resources = mock()
            whenever(resources.displayMetrics).thenReturn(DisplayMetrics().apply { density = DENSITY })
            whenever(view.resources).thenReturn(resources)
        }

        fun children(parent: ViewGroup, vararg children: View) {
            whenever(parent.childCount).thenReturn(children.size)
            children.forEachIndexed { index, child -> whenever(parent.getChildAt(index)).thenReturn(child) }
        }

        fun padding(view: ViewGroup, left: Int, top: Int, right: Int, bottom: Int) {
            whenever(view.paddingLeft).thenReturn((left * DENSITY).toInt())
            whenever(view.paddingTop).thenReturn((top * DENSITY).toInt())
            whenever(view.paddingRight).thenReturn((right * DENSITY).toInt())
            whenever(view.paddingBottom).thenReturn((bottom * DENSITY).toInt())
            val area = bounds.getValue(view)
            whenever(mockBoundsResolver.resolveViewPaddedBounds(view, DENSITY)).thenReturn(
                GlobalBounds(area.x + left, area.y + top, area.width - left - right, area.height - top - bottom)
            )
        }

        fun makeOpaque(view: View) {
            whenever(view.background).thenReturn(mock<Drawable>())
            whenever(view.alpha).thenReturn(1f)
            whenever(view.scaleX).thenReturn(1f)
            whenever(view.scaleY).thenReturn(1f)
            whenever(mockDrawableToColorMapper.mapDrawableToColor(any(), any())).thenReturn(Color.BLACK)
        }

        fun start(): CaptureStep<WindowWalkResult> {
            val mapper = CapturedViewMapper<View> { view, mappingContext ->
                val identity = mappingContext.identityFactory.shapeWireframe(mappingContext.ownerIdentity)
                identities[view] = identity
                onMapped(view)
                CapturedViewMapperResult.Wireframes(
                    listOf(
                        CapturedWireframe.Shape(identity, wireframeBounds[view] ?: bounds.getValue(view).toCaptured())
                    )
                )
            }
            val testedTraversal = AndroidWindowTraversal(
                mapperRegistry = CapturedViewMapperRegistry(emptyList(), mapper, mock()),
                internalLogger = mock(),
                viewBoundsResolver = mockBoundsResolver,
                viewIdentifierResolver = mockIdentifierResolver,
                drawableToColorMapper = mockDrawableToColorMapper
            )
            val factory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
            return testedTraversal.traverseWindow(root, factory.window("window"), factory, context)
        }

        fun capture(): WindowWalkResult.Present {
            resumes = 0
            var step = start()
            while (step is CaptureStep.Yielded) {
                assertThat(++resumes).isLessThan(bounds.size + 1)
                context.sliceClock.markStart()
                step = step.resume()
            }
            return (step as CaptureStep.Done).value as WindowWalkResult.Present
        }

        companion object {
            const val DENSITY = 2f
        }
    }

    private fun WindowWalkResult.Present.wireframe(identity: CapturedIdentity): CapturedWireframe =
        wireframes.single { it.identity == identity }
}
