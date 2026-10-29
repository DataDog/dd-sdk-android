/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.ShapeDrawable
import android.util.DisplayMetrics
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
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
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionChildDrawingOrderTest {

    @StringForgery
    lateinit var fakeScopeValue: String

    @Test
    fun `M preserve stable Z order W traverseWindow { children have different elevations }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue)
        whenever(fakeFixture.mockChildren[0].z).thenReturn(4f)
        whenever(fakeFixture.mockChildren[1].z).thenReturn(-2f)
        whenever(fakeFixture.mockChildren[2].z).thenReturn(4f)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2", "1", "3")
        assertThat(
            fakeFixture.visited
        ).containsExactly(
            fakeFixture.mockRoot,
            fakeFixture.mockChildren[1],
            fakeFixture.mockChildren[0],
            fakeFixture.mockChildren[2]
        )
    }

    @Test
    fun `M retain raised child W traverseWindow { later opaque sibling is below it }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        fakeFixture.makeOpaque(fakeFixture.mockChildren[0])
        fakeFixture.makeOpaque(fakeFixture.mockChildren[1])
        whenever(fakeFixture.mockChildren[0].z).thenReturn(10f)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0])
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
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, screenDensity = 2f)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        fakeFixture.clip(mockCovering, fakeLeft, fakeTop, fakeRight, fakeBottom)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0], mockCovering)
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
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, screenDensity = 2f)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        fakeFixture.clip(mockCovering, fakeLeft, fakeTop, fakeRight, fakeBottom)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, mockCovering)
    }

    @Test
    fun `M cull covered sibling W traverseWindow { opaque sibling has no clip }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, screenDensity = 2f)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        whenever(mockCovering.clipBounds).thenReturn(null)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, mockCovering)
    }

    @Suppress("DEPRECATION")
    @ParameterizedTest
    @ValueSource(ints = [PixelFormat.UNKNOWN, PixelFormat.TRANSPARENT, PixelFormat.TRANSLUCENT])
    fun `M retain visible sibling W traverseWindow { background opacity does not guarantee coverage }`(
        fakeOpacity: Int
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        val mockBackground = mockCovering.background as ColorDrawable
        whenever(mockBackground.opacity).thenReturn(fakeOpacity)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0], mockCovering)
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
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        val mockBackground = Mockito.mock(fakeDrawableClass)
        whenever(mockCovering.background).thenReturn(mockBackground)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0], mockCovering)
    }

    @ParameterizedTest
    @CsvSource(
        "30, 0, 0, 1, 1, 0",
        "30, 0, 0, 1, 1, 1",
        "0, 30, 0, 1, 1, 0",
        "0, 30, 0, 1, 1, 1",
        "0, 0, 30, 1, 1, 0",
        "0, 0, 30, 1, 1, 1",
        "0, 0, 0, 2, 1, 0",
        "0, 0, 0, 2, 1, 1",
        "0, 0, 0, 1, 2, 0",
        "0, 0, 0, 1, 2, 1",
        "0, 0, 0, -1, 1, 0",
        "0, 0, 0, -1, 1, 1",
        "0, 0, 0, 1, 0.5, 0",
        "0, 0, 0, 1, 0.5, 1"
    )
    fun `M retain siblings W traverseWindow { either sibling has scale or rotation }`(
        fakeRotation: Float,
        fakeRotationX: Float,
        fakeRotationY: Float,
        fakeScaleX: Float,
        fakeScaleY: Float,
        fakeTransformedIndex: Int
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        fakeFixture.mockChildren.forEach(fakeFixture::makeOpaque)
        val mockTransformedChild = fakeFixture.mockChildren[fakeTransformedIndex]
        mockTransformedChild.stub {
            on { rotation } doReturn fakeRotation
            on { rotationX } doReturn fakeRotationX
            on { rotationY } doReturn fakeRotationY
            on { scaleX } doReturn fakeScaleX
            on { scaleY } doReturn fakeScaleY
        }

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(
            fakeFixture.visited
        ).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0], fakeFixture.mockChildren[1])
    }

    @Test
    fun `M retain visible sibling W traverseWindow { opaque sibling clips to outline }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        whenever(mockCovering.clipToOutline).thenReturn(true)
        whenever(mockCovering.clipBounds).thenReturn(null)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0], mockCovering)
    }

    @Test
    fun `M resume culling W traverseWindow { outline clipping disabled between captures }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        whenever(mockCovering.clipToOutline).thenReturn(true)
        val previousWindow = fakeFixture.traverseWindow()
        fakeFixture.visited.clear()

        // When
        whenever(mockCovering.clipToOutline).thenReturn(false)
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(previousWindow.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, mockCovering)
    }

    @Test
    fun `M resume culling W traverseWindow { restrictive clip removed between captures }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, screenDensity = 2f)
        val mockCovering = fakeFixture.mockChildren[1]
        fakeFixture.makeOpaque(mockCovering)
        fakeFixture.clip(mockCovering, 0, 0, 50, 100)
        val previousWindow = fakeFixture.traverseWindow()
        fakeFixture.visited.clear()

        // When
        whenever(mockCovering.clipBounds).thenReturn(null)
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(previousWindow.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, mockCovering)
    }

    @Test
    fun `M use other opaque siblings W traverseWindow { frontmost sibling has restrictive clip }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, screenDensity = 2f)
        fakeFixture.makeOpaque(fakeFixture.mockChildren[1])
        fakeFixture.makeOpaque(fakeFixture.mockChildren[2])
        fakeFixture.clip(fakeFixture.mockChildren[2], 0, 0, 50, 100)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2", "3")
        assertThat(
            fakeFixture.visited
        ).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[1], fakeFixture.mockChildren[2])
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M preserve custom order W traverseWindow { equal Z siblings }`(sdkInt: Int) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, sdkInt = sdkInt)
        fakeFixture.customOrder(2, 0, 1)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("3", "1", "2")
        assertThat(
            fakeFixture.visited
        ).containsExactly(
            fakeFixture.mockRoot,
            fakeFixture.mockChildren[2],
            fakeFixture.mockChildren[0],
            fakeFixture.mockChildren[1]
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M apply Z after custom order W traverseWindow`(sdkInt: Int) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, sdkInt = sdkInt)
        fakeFixture.customOrder(2, 1, 0)
        whenever(fakeFixture.mockChildren[1].z).thenReturn(5f)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("3", "1", "2")
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M cull using custom order W traverseWindow { opaque siblings overlap }`(sdkInt: Int) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, sdkInt = sdkInt)
        fakeFixture.customOrder(1, 0)
        fakeFixture.mockChildren.forEach(fakeFixture::makeOpaque)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0])
    }

    @ParameterizedTest
    @ValueSource(ints = [23, 28, 29, 35])
    fun `M ignore custom callback W traverseWindow { custom ordering disabled }`(sdkInt: Int) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, sdkInt = sdkInt)
        fakeFixture.customOrder(1, 0)
        whenever(fakeFixture.mockRoot.isChildrenDrawingOrderEnabled).thenReturn(false)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        verify(fakeFixture.mockRoot, never()).getChildDrawingOrder(any(), any())
        verify(fakeFixture.mockRoot, never()).getChildDrawingOrder(any())
    }

    @ParameterizedTest
    @CsvSource("28, -1", "28, 1", "28, 2", "29, -1", "29, 1", "29, 2")
    fun `M retain every child W traverseWindow { callback returns invalid or duplicate index }`(
        sdkInt: Int,
        invalidIndex: Int
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, sdkInt = sdkInt)
        fakeFixture.customOrder(invalidIndex, 1)
        fakeFixture.mockChildren.forEach(fakeFixture::makeOpaque)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(
            fakeFixture.visited
        ).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[0], fakeFixture.mockChildren[1])
    }

    @ParameterizedTest
    @ValueSource(ints = [28, 29])
    fun `M retain every child W traverseWindow { custom callback throws }`(sdkInt: Int) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, sdkInt = sdkInt)
        fakeFixture.customOrder(1, 0)
        fakeFixture.mockChildren.forEach(fakeFixture::makeOpaque)
        whenever(fakeFixture.mockRoot.getChildDrawingOrder(any(), any())).thenThrow(IllegalStateException())
        whenever(fakeFixture.mockRoot.getChildDrawingOrder(any())).thenThrow(IllegalStateException())

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
    }

    @Test
    fun `M retain every child W traverseWindow { custom ordering flag cannot be read }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        fakeFixture.mockChildren.forEach(fakeFixture::makeOpaque)
        whenever(fakeFixture.mockRoot.isChildrenDrawingOrderEnabled).thenThrow(IllegalStateException())

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
    }

    @ParameterizedTest
    @CsvSource("0.5, 0, 50, 25", "1, 180, -25, 25")
    fun `M retain visible sibling W traverseWindow() { parent transform changes screen origin }`(
        fakeParentScale: Float,
        fakeParentRotation: Float,
        fakeLeft: Int,
        fakeScreenX: Long
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        val mockLower = fakeFixture.mockChildren[0]
        val mockUpper = fakeFixture.mockChildren[1]
        whenever(fakeFixture.mockRoot.scaleX).thenReturn(fakeParentScale)
        whenever(fakeFixture.mockRoot.rotation).thenReturn(fakeParentRotation)
        mockLower.stub {
            on { left } doReturn fakeLeft
            on { width } doReturn 20
            on { height } doReturn 20
        }
        whenever(mockUpper.width).thenReturn(50)
        whenever(mockUpper.height).thenReturn(50)
        whenever(fakeFixture.mockBoundsResolver.resolveViewGlobalBounds(mockLower, 1f))
            .thenReturn(GlobalBounds(fakeScreenX, 0, 20, 20))
        whenever(fakeFixture.mockBoundsResolver.resolveViewGlobalBounds(mockUpper, 1f))
            .thenReturn(GlobalBounds(0, 0, 50, 50))
        fakeFixture.makeOpaque(mockUpper)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, mockLower, mockUpper)
    }

    @ParameterizedTest
    @CsvSource("0.25, 0", "-0.25, 0", "0, 0.25", "0, -0.25")
    fun `M retain fractional overflow W traverseWindow() { sibling is translated }`(
        fakeTranslationX: Float,
        fakeTranslationY: Float
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, screenDensity = 3f)
        whenever(fakeFixture.mockChildren[0].translationX).thenReturn(fakeTranslationX)
        whenever(fakeFixture.mockChildren[0].translationY).thenReturn(fakeTranslationY)
        fakeFixture.makeOpaque(fakeFixture.mockChildren[1])

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("1", "2")
    }

    @Test
    fun `M cull covered sibling W traverseWindow() { siblings share fractional translation and transformed parent }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2, screenDensity = 3f)
        whenever(fakeFixture.mockRoot.scaleX).thenReturn(0.5f)
        whenever(fakeFixture.mockRoot.rotation).thenReturn(30f)
        fakeFixture.mockChildren.forEach {
            whenever(it.translationX).thenReturn(0.25f)
            whenever(it.translationY).thenReturn(-0.25f)
        }
        fakeFixture.makeOpaque(fakeFixture.mockChildren[1])

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children.map { it.identity.localId }).containsExactly("2")
        assertThat(fakeFixture.visited).containsExactly(fakeFixture.mockRoot, fakeFixture.mockChildren[1])
    }

    @Test
    fun `M retain all children W traverseWindow() { sibling group exceeds occlusion limit }`() {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 65)
        fakeFixture.mockChildren.forEach(fakeFixture::makeOpaque)

        // When
        val result = fakeFixture.traverseWindow()

        // Then
        assertThat(result.rootLayer.children).hasSize(65)
        assertThat(fakeFixture.visited).containsExactlyElementsOf(
            listOf(fakeFixture.mockRoot) + fakeFixture.mockChildren
        )
    }

    @Test
    fun `M skip occlusion reads W occludedChildIndices() { too many siblings }`() {
        // Given
        val mockColorMapper: DrawableToColorMapper = mock()
        val testedDetector = ViewOcclusionDetector(mockColorMapper, mock(), mock())
        val fakeChildren = List(65) { mock<View>() }

        // When
        val result = testedDetector.occludedChildIndices(ViewGroupDrawingOrder(fakeChildren, true))

        // Then
        assertThat(result).isEmpty()
        verifyNoInteractions(mockColorMapper, *fakeChildren.toTypedArray())
    }

    @ParameterizedTest
    @ValueSource(classes = [SurfaceView::class, TextureView::class])
    fun `M disable worker culling W copyWindow() { covered subtree contains a separate surface }`(
        fakeSurfaceClass: Class<out View>
    ) {
        // Given
        val fakeFixture = Fixture(fakeScopeValue, childCount = 2)
        val mockBack: ViewGroup = mock {
            on { isShown } doReturn true
            on { width } doReturn 100
            on { height } doReturn 100
            on { scaleX } doReturn 1f
            on { scaleY } doReturn 1f
        }
        val mockSurface = Mockito.mock(fakeSurfaceClass)
        mockSurface.stub {
            on { isShown } doReturn true
            on { width } doReturn 100
            on { height } doReturn 100
        }
        whenever(mockBack.childCount).thenReturn(1)
        whenever(mockBack.getChildAt(0)).thenReturn(mockSurface)
        whenever(fakeFixture.mockRoot.getChildAt(0)).thenReturn(mockBack)
        fakeFixture.makeOpaque(fakeFixture.mockChildren[1])

        // When
        val copy = fakeFixture.copyWindow() as WindowCopyResult.Present

        // Then
        assertThat(copy.window.isDrawingOrderReliable).isFalse()
        assertThat(fakeFixture.visited).doesNotContain(mockBack, mockSurface)
    }

    private class Fixture(
        private val fakeScopeValue: String,
        childCount: Int = 3,
        private val sdkInt: Int = 28,
        screenDensity: Float = 1f
    ) {
        val mockRoot: CustomOrderViewGroup = mockViewWithGeometry()
        val mockChildren = List(childCount) { mockViewWithGeometry<View>() }
        val visited = mutableListOf<View>()
        val mockBoundsResolver: ViewBoundsResolver = mock()
        private val mockIdentifierResolver: ViewIdentifierResolver = mock()
        private val mockDrawableToColorMapper: DrawableToColorMapper = mock()

        init {
            val mockResources: Resources = mock()
            whenever(mockResources.displayMetrics).thenReturn(DisplayMetrics().apply { density = screenDensity })
            mockRoot.stub {
                on { resources } doReturn mockResources
                on { this.childCount } doReturn mockChildren.size
                on { clipChildren } doReturn true
            }
            (listOf(mockRoot) + mockChildren).forEachIndexed { index, view ->
                whenever(mockIdentifierResolver.resolveViewId(view)).thenReturn(index.toLong())
                val size = (100 / screenDensity).toLong()
                whenever(mockBoundsResolver.resolveViewGlobalBounds(view, screenDensity))
                    .thenReturn(GlobalBounds(0, 0, size, size))
            }
            mockChildren.forEachIndexed { index, child -> whenever(mockRoot.getChildAt(index)).thenReturn(child) }
        }

        private inline fun <reified T : View> mockViewWithGeometry(): T = mock {
            on { isShown } doReturn true
            on { scaleX } doReturn 1f
            on { scaleY } doReturn 1f
            on { width } doReturn 100
            on { height } doReturn 100
        }

        fun customOrder(vararg indices: Int) {
            whenever(mockRoot.isChildrenDrawingOrderEnabled).thenReturn(true)
            indices.forEachIndexed { position, index ->
                whenever(mockRoot.getChildDrawingOrder(mockChildren.size, position)).thenReturn(index)
                whenever(mockRoot.getChildDrawingOrder(position)).thenReturn(index)
            }
        }

        @Suppress("DEPRECATION")
        fun makeOpaque(view: View) {
            val mockDrawable: ColorDrawable = mock()
            whenever(mockDrawable.opacity).thenReturn(PixelFormat.OPAQUE)
            view.stub {
                on { background } doReturn mockDrawable
                on { alpha } doReturn 1f
                on { scaleX } doReturn 1f
                on { scaleY } doReturn 1f
            }
            whenever(mockDrawableToColorMapper.mapDrawableToColor(any(), any())).thenReturn(Color.BLACK)
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
                viewBoundsResolver = mockBoundsResolver,
                viewIdentifierResolver = mockIdentifierResolver,
                drawableToColorMapper = mockDrawableToColorMapper,
                drawingOrderResolver = ViewGroupDrawingOrderResolver(mock(), sdkInt)
            )
        }

        fun copyWindow(): WindowCopyResult {
            val fakeIdentityFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope(fakeScopeValue))
            val fakeContext = CaptureGenerationContext(1L, 0L, Long.MAX_VALUE, CaptureTimeProvider { 0L })
            return testedTraversal.traverseWindow(
                mockRoot,
                fakeIdentityFactory.window("window"),
                fakeIdentityFactory,
                fakeContext
            )
        }

        fun traverseWindow(): WindowWalkResult.Present {
            val fakeContext = CaptureGenerationContext(1L, 0L, Long.MAX_VALUE, CaptureTimeProvider { 0L })
            return copyWindow().buildWindow(fakeContext) as WindowWalkResult.Present
        }
    }

    // Widen protected SDK hooks so tests can control custom ordering, including the enabled flag.
    internal abstract class CustomOrderViewGroup(context: Context) : ViewGroup(context) {
        public override fun isChildrenDrawingOrderEnabled(): Boolean = super.isChildrenDrawingOrderEnabled()
        public override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int = drawingPosition
    }
}
