/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
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
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class CompositionChildDrawingOrderTest {

    @Test
    fun `M preserve stable Z order W traverseWindow { children have different elevations }`() {
        // Given
        val fixture = Fixture()
        whenever(fixture.children[0].z).thenReturn(4f)
        whenever(fixture.children[1].z).thenReturn(-2f)
        whenever(fixture.children[2].z).thenReturn(4f)

        // When
        val result = fixture.traverse()

        // Then: Z controls stacking, with original order retained for equal-Z siblings.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2", "1", "3")
        assertThat(
            fixture.visited
        ).containsExactly(fixture.root, fixture.children[1], fixture.children[0], fixture.children[2])
    }

    @Test
    fun `M retain raised child W traverseWindow { later opaque sibling is below it }`() {
        // Given
        val fixture = Fixture(childCount = 2)
        fixture.makeOpaque(fixture.children[0])
        fixture.makeOpaque(fixture.children[1])
        whenever(fixture.children[0].z).thenReturn(10f)

        // When
        val result = fixture.traverse()

        // Then: the lower child is occluded, even though its raw index is higher.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0])
    }

    @ParameterizedTest
    @CsvSource(
        "1, 0, 100, 100",
        "0, 1, 100, 100",
        "0, 0, 99, 100",
        "0, 0, 100, 99",
        "0, 0, 0, 0",
        "50, 0, 50, 100",
        "0, 50, 100, 50",
        "60, 60, 40, 40"
    )
    fun `M retain visible sibling W traverseWindow { opaque sibling has restrictive or empty clip }`(
        fakeLeft: Int,
        fakeTop: Int,
        fakeRight: Int,
        fakeBottom: Int
    ) {
        // Given: local clip coordinates stay in pixels even when captured bounds are density-scaled.
        val fixture = Fixture(childCount = 2, screenDensity = 2f)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        fixture.clip(covering, fakeLeft, fakeTop, fakeRight, fakeBottom)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0], covering)
    }

    @ParameterizedTest
    @CsvSource("0, 0, 100, 100", "-20, -30, 120, 130", "0, -10, 100, 110")
    fun `M cull covered sibling W traverseWindow { clip contains entire opaque view }`(
        fakeLeft: Int,
        fakeTop: Int,
        fakeRight: Int,
        fakeBottom: Int
    ) {
        // Given
        val fixture = Fixture(childCount = 2, screenDensity = 2f)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        fixture.clip(covering, fakeLeft, fakeTop, fakeRight, fakeBottom)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fixture.visited).containsExactly(fixture.root, covering)
    }

    @Test
    fun `M cull covered sibling W traverseWindow { opaque sibling has no clip }`() {
        // Given
        val fixture = Fixture(childCount = 2, screenDensity = 2f)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        whenever(covering.clipBounds).thenReturn(null)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fixture.visited).containsExactly(fixture.root, covering)
    }

    @ParameterizedTest
    @ValueSource(
        classes = [
            InsetDrawable::class, ShapeDrawable::class, GradientDrawable::class, LayerDrawable::class, Drawable::class
        ]
    )
    fun `M retain visible sibling W traverseWindow { background color does not prove coverage }`(
        fakeDrawableClass: Class<out Drawable>
    ) {
        // Given: the color mapper reports opaque black even for an inset, shaped or unknown background.
        val fixture = Fixture(childCount = 2)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        val stubBackground = Mockito.mock(fakeDrawableClass)
        whenever(covering.background).thenReturn(stubBackground)

        // When
        val result = fixture.traverse()

        // Then: potentially visible content remains beneath backgrounds with uncertain coverage.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0], covering)
    }

    @ParameterizedTest
    @CsvSource(
        "30, 0, 0, 1, 1",
        "0, 30, 0, 1, 1",
        "0, 0, 30, 1, 1",
        "0, 0, 0, 2, 1",
        "0, 0, 0, 1, 2",
        "0, 0, 0, -1, 1",
        "0, 0, 0, 1, 0.5"
    )
    fun `M retain siblings W traverseWindow { either sibling has scale or rotation }`(
        fakeRotation: Float,
        fakeRotationX: Float,
        fakeRotationY: Float,
        fakeScaleX: Float,
        fakeScaleY: Float
    ) {
        for (fakeTransformedIndex in 0..1) {
            // Given: the computed rectangles overlap completely, but transformed drawing may not.
            val fixture = Fixture(childCount = 2)
            fixture.children.forEach(fixture::makeOpaque)
            val mockTransformedChild = fixture.children[fakeTransformedIndex]
            whenever(mockTransformedChild.rotation).thenReturn(fakeRotation)
            whenever(mockTransformedChild.rotationX).thenReturn(fakeRotationX)
            whenever(mockTransformedChild.rotationY).thenReturn(fakeRotationY)
            whenever(mockTransformedChild.scaleX).thenReturn(fakeScaleX)
            whenever(mockTransformedChild.scaleY).thenReturn(fakeScaleY)

            // When
            val result = fixture.traverse()

            // Then: a transformed view is neither culled nor allowed to cull another view.
            assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
            assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0], fixture.children[1])
        }
    }

    @Test
    fun `M retain visible sibling W traverseWindow { opaque sibling clips to outline }`() {
        // Given: even without clipBounds, an outline can expose the sibling at rounded corners.
        val fixture = Fixture(childCount = 2)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        whenever(covering.clipToOutline).thenReturn(true)
        whenever(covering.clipBounds).thenReturn(null)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0], covering)
    }

    @Test
    fun `M resume culling W traverseWindow { outline clipping disabled between captures }`() {
        // Given
        val fixture = Fixture(childCount = 2)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        whenever(covering.clipToOutline).thenReturn(true)
        assertThat(fixture.traverse().rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        fixture.visited.clear()

        // When
        whenever(covering.clipToOutline).thenReturn(false)
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fixture.visited).containsExactly(fixture.root, covering)
    }

    @Test
    fun `M resume culling W traverseWindow { restrictive clip removed between captures }`() {
        // Given
        val fixture = Fixture(childCount = 2, screenDensity = 2f)
        val covering = fixture.children[1]
        fixture.makeOpaque(covering)
        fixture.clip(covering, 0, 0, 50, 100)
        assertThat(fixture.traverse().rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        fixture.visited.clear()

        // When
        whenever(covering.clipBounds).thenReturn(null)
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fixture.visited).containsExactly(fixture.root, covering)
    }

    @Test
    fun `M use other opaque siblings W traverseWindow { frontmost sibling has restrictive clip }`() {
        // Given
        val fixture = Fixture(screenDensity = 2f)
        fixture.makeOpaque(fixture.children[1])
        fixture.makeOpaque(fixture.children[2])
        fixture.clip(fixture.children[2], 0, 0, 50, 100)

        // When
        val result = fixture.traverse()

        // Then: the middle sibling still covers the back one and must itself remain in the replay.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2", "3")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[1], fixture.children[2])
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M preserve custom order W traverseWindow { equal Z siblings }`(sdkInt: Int) {
        // Given
        val fixture = Fixture(sdkInt = sdkInt)
        fixture.customOrder(2, 0, 1)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("3", "1", "2")
        assertThat(
            fixture.visited
        ).containsExactly(fixture.root, fixture.children[2], fixture.children[0], fixture.children[1])
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M apply Z after custom order W traverseWindow`(sdkInt: Int) {
        // Given
        val fixture = Fixture(sdkInt = sdkInt)
        fixture.customOrder(2, 1, 0)
        whenever(fixture.children[1].z).thenReturn(5f)

        // When
        val result = fixture.traverse()

        // Then: equal-Z children keep custom order, while the raised sibling is drawn last.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("3", "1", "2")
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M cull using custom order W traverseWindow { opaque siblings overlap }`(sdkInt: Int) {
        // Given
        val fixture = Fixture(childCount = 2, sdkInt = sdkInt)
        fixture.customOrder(1, 0)
        fixture.children.forEach(fixture::makeOpaque)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0])
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M ignore custom callback W traverseWindow { custom ordering disabled }`(sdkInt: Int) {
        // Given
        val fixture = Fixture(childCount = 2, sdkInt = sdkInt)
        fixture.customOrder(1, 0)
        whenever(fixture.root.isChildrenDrawingOrderEnabled).thenReturn(false)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        verify(fixture.root, never()).getChildDrawingOrder(any(), any())
        verify(fixture.root, never()).getChildDrawingOrder(any())
    }

    @ParameterizedTest
    @CsvSource("28, -1", "28, 1", "28, 2", "29, -1", "29, 1", "29, 2")
    fun `M retain every child W traverseWindow { callback returns invalid or duplicate index }`(
        sdkInt: Int,
        invalidIndex: Int
    ) {
        // Given: every invalid ordering would otherwise risk skipping or duplicating a visible child.
        val fixture = Fixture(childCount = 2, sdkInt = sdkInt)
        fixture.customOrder(invalidIndex, 1)
        fixture.children.forEach(fixture::makeOpaque)

        // When
        val result = fixture.traverse()

        // Then: uncertain ordering disables culling even though the children are fully opaque.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[0], fixture.children[1])
    }

    @ParameterizedTest
    @ValueSource(ints = [28, 29])
    fun `M retain every child W traverseWindow { custom callback throws }`(sdkInt: Int) {
        // Given
        val fixture = Fixture(childCount = 2, sdkInt = sdkInt)
        fixture.customOrder(1, 0)
        fixture.children.forEach(fixture::makeOpaque)
        whenever(fixture.root.getChildDrawingOrder(any(), any())).thenThrow(IllegalStateException())
        whenever(fixture.root.getChildDrawingOrder(any())).thenThrow(IllegalStateException())

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
    }

    @Test
    fun `M retain every child W traverseWindow { custom ordering flag cannot be read }`() {
        // Given
        val fixture = Fixture(childCount = 2)
        fixture.children.forEach(fixture::makeOpaque)
        whenever(fixture.root.isChildrenDrawingOrderEnabled).thenThrow(IllegalStateException())

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
    }

    @ParameterizedTest
    @CsvSource("0.5, 0, 50, 25", "1, 180, -25, 25")
    fun `M retain visible sibling W parent transform changes screen origin`(
        fakeParentScale: Float,
        fakeParentRotation: Float,
        fakeLeft: Int,
        fakeScreenX: Long
    ) {
        // Given: screen origins include the parent's transform but view width/height do not.
        val fixture = Fixture(childCount = 2)
        val lower = fixture.children[0]
        val upper = fixture.children[1]
        whenever(fixture.root.scaleX).thenReturn(fakeParentScale)
        whenever(fixture.root.rotation).thenReturn(fakeParentRotation)
        whenever(lower.left).thenReturn(fakeLeft)
        whenever(lower.width).thenReturn(20)
        whenever(lower.height).thenReturn(20)
        whenever(upper.width).thenReturn(50)
        whenever(upper.height).thenReturn(50)
        whenever(fixture.boundsResolver.resolveViewGlobalBounds(lower, 1f))
            .thenReturn(GlobalBounds(fakeScreenX, 0, 20, 20))
        whenever(fixture.boundsResolver.resolveViewGlobalBounds(upper, 1f))
            .thenReturn(GlobalBounds(0, 0, 50, 50))
        fixture.makeOpaque(upper)

        // When
        val result = fixture.traverse()

        // Then: the lower view extends outside the upper in their shared parent coordinates.
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fixture.visited).containsExactly(fixture.root, lower, upper)
    }

    @ParameterizedTest
    @CsvSource("0.25, 0", "-0.25, 0", "0, 0.25", "0, -0.25")
    fun `M retain fractional overflow W sibling is translated`(fakeTranslationX: Float, fakeTranslationY: Float) {
        // Given: density-rounded screen bounds are identical, but a fraction of a pixel remains visible.
        val fixture = Fixture(childCount = 2, screenDensity = 3f)
        whenever(fixture.children[0].translationX).thenReturn(fakeTranslationX)
        whenever(fixture.children[0].translationY).thenReturn(fakeTranslationY)
        fixture.makeOpaque(fixture.children[1])

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
    }

    @Test
    fun `M cull covered sibling W siblings share fractional translation and transformed parent`() {
        // Given: a common transform does not change containment between siblings.
        val fixture = Fixture(childCount = 2, screenDensity = 3f)
        whenever(fixture.root.scaleX).thenReturn(0.5f)
        whenever(fixture.root.rotation).thenReturn(30f)
        fixture.children.forEach {
            whenever(it.translationX).thenReturn(0.25f)
            whenever(it.translationY).thenReturn(-0.25f)
        }
        fixture.makeOpaque(fixture.children[1])

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fixture.visited).containsExactly(fixture.root, fixture.children[1])
    }

    @Test
    fun `M retain all children W sibling group exceeds occlusion limit`() {
        // Given: every child would otherwise be occluded by the last opaque sibling.
        val fixture = Fixture(childCount = 65)
        fixture.children.forEach(fixture::makeOpaque)

        // When
        val result = fixture.traverse()

        // Then
        assertThat(result.rootLayer.children).hasSize(65)
        assertThat(fixture.visited).containsExactlyElementsOf(listOf(fixture.root) + fixture.children)
    }

    @Test
    fun `M skip geometry and drawable reads W group is too large for occlusion preprocessing`() {
        // Given
        val mockColorMapper: DrawableToColorMapper = mock()
        val testedDetector = ViewOcclusionDetector(mockColorMapper, mock(), mock())
        val fakeChildren = List(65) { mock<View>() }

        // When
        val result = testedDetector.occludedChildIndices(ViewGroupDrawingOrder(fakeChildren, true))

        // Then: the optional quadratic pass does no per-child work above the limit.
        assertThat(result).isEmpty()
        verifyNoInteractions(mockColorMapper, *fakeChildren.toTypedArray())
    }

    private class Fixture(childCount: Int = 3, private val sdkInt: Int = 28, screenDensity: Float = 1f) {
        val root: CustomOrderViewGroup = mock()
        val children = List(childCount) { mock<View>() }
        val visited = mutableListOf<View>()
        val boundsResolver: ViewBoundsResolver = mock()
        private val identifierResolver: ViewIdentifierResolver = mock()
        private val drawableToColorMapper: DrawableToColorMapper = mock()

        init {
            val resources: Resources = mock()
            whenever(resources.displayMetrics).thenReturn(DisplayMetrics().apply { density = screenDensity })
            whenever(root.resources).thenReturn(resources)
            whenever(root.childCount).thenReturn(children.size)
            whenever(root.clipChildren).thenReturn(true)
            (listOf(root) + children).forEachIndexed { index, view ->
                whenever(view.isShown).thenReturn(true)
                whenever(view.scaleX).thenReturn(1f)
                whenever(view.scaleY).thenReturn(1f)
                whenever(view.width).thenReturn(100)
                whenever(view.height).thenReturn(100)
                whenever(identifierResolver.resolveViewId(view)).thenReturn(index.toLong())
                val size = (100 / screenDensity).toLong()
                whenever(boundsResolver.resolveViewGlobalBounds(view, screenDensity))
                    .thenReturn(GlobalBounds(0, 0, size, size))
            }
            children.forEachIndexed { index, child -> whenever(root.getChildAt(index)).thenReturn(child) }
        }

        fun customOrder(vararg indices: Int) {
            whenever(root.isChildrenDrawingOrderEnabled).thenReturn(true)
            indices.forEachIndexed { position, index ->
                whenever(root.getChildDrawingOrder(children.size, position)).thenReturn(index)
                whenever(root.getChildDrawingOrder(position)).thenReturn(index)
            }
        }

        fun makeOpaque(view: View) {
            val drawable: ColorDrawable = mock()
            whenever(view.background).thenReturn(drawable)
            whenever(view.alpha).thenReturn(1f)
            whenever(view.scaleX).thenReturn(1f)
            whenever(view.scaleY).thenReturn(1f)
            whenever(drawableToColorMapper.mapDrawableToColor(any(), any())).thenReturn(Color.BLACK)
        }

        fun clip(view: View, left: Int, top: Int, right: Int, bottom: Int) {
            // Framework constructors are stubbed in JVM tests; populate the real Rect fields directly.
            val fakeClip = Rect().also {
                it.left = left
                it.top = top
                it.right = right
                it.bottom = bottom
            }
            whenever(view.clipBounds).thenReturn(fakeClip)
        }

        private val testedTraversal by lazy {
            AndroidWindowTraversal(
                mapperRegistry = CapturedViewMapperRegistry(
                    emptyList(),
                    CapturedViewMapper { view, _ ->
                        visited.add(view)
                        CapturedViewMapperResult.None
                    },
                    mock()
                ),
                internalLogger = mock(),
                viewBoundsResolver = boundsResolver,
                viewIdentifierResolver = identifierResolver,
                drawableToColorMapper = drawableToColorMapper,
                drawingOrderResolver = ViewGroupDrawingOrderResolver(mock(), sdkInt)
            )
        }

        fun traverse(): WindowWalkResult.Present {
            val identityFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("view"))
            val context = CaptureGenerationContext(1L, 0L, Long.MAX_VALUE, CaptureTimeProvider { 0L })
            return (
                testedTraversal.traverseWindow(root, identityFactory.window("window"), identityFactory, context)
                    as CaptureStep.Done
                ).value as WindowWalkResult.Present
        }
    }

    // Widen protected SDK hooks so tests can control custom ordering, including the enabled flag.
    internal abstract class CustomOrderViewGroup(context: Context) : ViewGroup(context) {
        public override fun isChildrenDrawingOrderEnabled(): Boolean = super.isChildrenDrawingOrderEnabled()
        public override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int = drawingPosition
    }
}
