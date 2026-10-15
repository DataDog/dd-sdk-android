/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.composition

import android.content.res.Resources
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import com.datadog.android.internal.sessionreplay.composition.CapturedBounds
import com.datadog.android.internal.sessionreplay.composition.CapturedChild
import com.datadog.android.internal.sessionreplay.composition.CapturedClip
import com.datadog.android.internal.sessionreplay.composition.CapturedLayer
import com.datadog.android.internal.sessionreplay.composition.CapturedLayerKind
import com.datadog.android.internal.sessionreplay.composition.CapturedModifier
import com.datadog.android.internal.sessionreplay.composition.CapturedWireframe
import com.datadog.android.internal.sessionreplay.composition.RumViewIdentityScope
import com.datadog.android.sessionreplay.R
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewData
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapper
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperRegistry
import com.datadog.android.sessionreplay.internal.composition.mapper.CapturedViewMapperResult
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class CompositionForegroundTest {
    private val fakeFactory = DefaultCapturedIdentityFactory(RumViewIdentityScope("scope"))
    private val fakeWindow = fakeFactory.window("window")
    private val fakeGeneration = CaptureGenerationContext(1L, 0L, 100L, CaptureTimeProvider { 0L })
    private val mockBoundsResolver: ViewBoundsResolver = mock()
    private val mockIdentifierResolver: ViewIdentifierResolver = mock()
    private val fakeForegroundCapture = ViewForegroundCapture(
        mock(),
        colorResolver = ForegroundColorResolver { 0x80112233.toInt() }
    )
    private val fakeBackgroundMapper = CapturedViewMapper<View> { _, context ->
        CapturedViewMapperResult.Wireframes(
            listOf(
                CapturedWireframe.Shape(
                    context.identityFactory.shapeWireframe(context.ownerIdentity),
                    checkNotNull(context.bounds)
                )
            )
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M paint foreground after children with or without background W buildWindow`(fakeBackground: Boolean) {
        // Given
        val stubRoot = group()
        val stubChild = view()
        whenever(stubRoot.foreground).thenReturn(mock<ColorDrawable>())
        whenever(stubRoot.alpha).thenReturn(0.5f)
        children(stubRoot, stubChild)
        val fakeMapper = CapturedViewMapper<View> { view, context ->
            if (view === stubRoot &&
                !fakeBackground
            ) {
                CapturedViewMapperResult.None
            } else {
                fakeBackgroundMapper.map(view, context)
            }
        }
        val fakeCopy = copy(stubRoot, fakeMapper)
        val fakeForeground = checkNotNull(fakeCopy.window.views.first().foreground)
        val fakeChildIdentity = fakeCopy.window.views.last().identity
        // The construction phase must depend only on the copy.
        clearInvocations(stubRoot, stubChild)

        // When
        val fakeResult = checkNotNull(CapturedNativeSnapshotBuilder().buildWindow(fakeCopy.window, fakeGeneration))

        // Then
        val fakeExpectedChildren = buildList {
            if (fakeBackground) add(CapturedChild.Wireframe(fakeFactory.shapeWireframe(fakeWindow)))
            add(CapturedChild.Layer(fakeChildIdentity))
            add(CapturedChild.Layer(fakeForeground.identity))
        }
        assertThat(fakeResult.rootLayer.children).containsExactlyElementsOf(fakeExpectedChildren)
        assertThat(fakeResult.rootLayer.modifiers).containsExactly(CapturedModifier.Opacity(0.5))
        val fakeForegroundLayer = fakeResult.layers.single { it.identity == fakeForeground.identity }
        assertThat(
            fakeForegroundLayer.children
        ).containsExactly(CapturedChild.Wireframe(fakeForeground.wireframe.identity))
        assertThat(fakeForegroundLayer.modifiers).isEmpty()
        assertThat(fakeResult.wireframes.last()).isEqualTo(fakeForeground.wireframe)
        assertThat(fakeResult.wireframes.map { it.identity }).doesNotHaveDuplicates()
        assertThat(
            DefaultCapturedTreeValidator().validate(snapshot(fakeResult))
        ).isEqualTo(CaptureValidationResult.Valid)
        verifyNoInteractions(stubRoot, stubChild)
    }

    @Test
    fun `M keep own clipping distinct from child padding W buildWindow`() {
        // Given
        val stubRoot = group()
        val stubChild = view()
        children(stubRoot, stubChild)
        whenever(stubRoot.foreground).thenReturn(mock<ColorDrawable>())
        whenever(stubRoot.paddingLeft).thenReturn(10)
        whenever(stubRoot.clipToPadding).thenReturn(true)
        whenever(stubRoot.clipBounds).thenReturn(Rect(5, 0, 100, 100))
        whenever(mockBoundsResolver.resolveViewPaddedBounds(stubRoot, 1f)).thenReturn(GlobalBounds(10, 0, 90, 100))
        // Use copied values so this case tests clipping independently of the raster path.
        val fakeCopy = copy(stubRoot)
        val fakeRoot = fakeCopy.window.views.first()
        val fakeForegroundIdentity = fakeFactory.layer(fakeRoot.identity, "foreground")
        val fakeForeground = CapturedForeground(
            fakeForegroundIdentity,
            CapturedWireframe.Shape(fakeFactory.shapeWireframe(fakeForegroundIdentity), fakeRoot.bounds)
        )
        val fakeWindowCopy = fakeCopy.window.copy(
            views = fakeCopy.window.views.mapIndexed { index, view ->
                if (index == 0) view.copy(foreground = fakeForeground) else view
            }
        )

        // When
        val fakeResult = checkNotNull(CapturedNativeSnapshotBuilder().buildWindow(fakeWindowCopy, fakeGeneration))

        // Then
        assertThat(fakeResult.wireframes.single { it.identity == fakeForeground.wireframe.identity }.clip)
            .isEqualTo(CapturedClip(left = 5))
        assertThat(
            fakeResult.wireframes.single {
                it.identity ==
                    fakeFactory.shapeWireframe(fakeWindowCopy.views[1].identity)
            }.clip
        )
            .isEqualTo(CapturedClip(left = 10))
    }

    @Test
    fun `M retain foreground when opaque descendants cover the background W buildWindow`() {
        // Given
        val fakeBounds = CapturedBounds(0, 0, 100, 100)
        val fakeChildIdentity = fakeFactory.view(fakeWindow, "child")
        val fakeForegroundIdentity = fakeFactory.layer(fakeWindow, "foreground")
        val fakeForeground = CapturedForeground(
            fakeForegroundIdentity,
            CapturedWireframe.Shape(fakeFactory.shapeWireframe(fakeForegroundIdentity), fakeBounds)
        )
        val mockRootData: CapturedViewData = mock()
        whenever(mockRootData.isOcclusionBounded).thenReturn(true)
        val mockChildData: CapturedViewData = mock()
        whenever(mockChildData.isOcclusionBounded).thenReturn(true)
        whenever(mockChildData.hasOpaqueBackground).thenReturn(true)
        whenever(mockChildData.map(any())).thenReturn(
            CapturedViewMapperResult.Wireframes(
                listOf(CapturedWireframe.Shape(fakeFactory.shapeWireframe(fakeChildIdentity), fakeBounds))
            )
        )
        val fakeRoot = CapturedNativeView(
            fakeWindow, -1, fakeBounds, 1.0, mockRootData, true, null, null,
            pixelBounds = GlobalBounds(0, 0, 100, 100), isGeometryReliable = true, foreground = fakeForeground
        )
        val fakeChild = fakeRoot.copy(
            identity = fakeChildIdentity,
            parentIndex = 0,
            viewData = mockChildData,
            foreground = null
        )

        // When
        val fakeResult = checkNotNull(
            CapturedNativeSnapshotBuilder().buildWindow(
                CapturedNativeWindow(1f, listOf(fakeRoot, fakeChild)),
                fakeGeneration
            )
        )

        // Then
        verify(mockRootData, never()).map(any())
        assertThat(fakeResult.wireframes).containsExactly(
            CapturedWireframe.Shape(fakeFactory.shapeWireframe(fakeChildIdentity), fakeBounds),
            fakeForeground.wireframe
        )
        assertThat(fakeResult.rootLayer.children).containsExactly(
            CapturedChild.Layer(fakeChildIdentity),
            CapturedChild.Layer(fakeForegroundIdentity)
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M skip independent foreground for hidden or terminal views W traverseWindow`(fakeHidden: Boolean) {
        // Given
        val stubRoot = group()
        whenever(stubRoot.foreground).thenReturn(mock<ColorDrawable>())
        whenever(stubRoot.getTag(R.id.datadog_hidden)).thenReturn(fakeHidden)
        val fakeTerminal = CapturedViewMapper<View> { _, context ->
            CapturedViewMapperResult.Wireframes(
                listOf(
                    CapturedWireframe.Shape(
                        context.identityFactory.shapeWireframe(context.ownerIdentity),
                        checkNotNull(context.bounds)
                    )
                ),
                isTerminal = true
            )
        }

        // When
        val fakeCopy = copy(stubRoot, fakeTerminal)

        // Then
        assertThat(fakeCopy.window.views.single().foreground).isNull()
        assertThat(fakeCopy.window.pendingPixelCaptures).isEmpty()
    }

    private fun copy(root: View, mapper: CapturedViewMapper<View> = fakeBackgroundMapper): WindowCopyResult.Present =
        AndroidWindowTraversal(
            mapperRegistry = CapturedViewMapperRegistry(emptyList(), mapper, mock()),
            internalLogger = mock(),
            viewIdentifierResolver = mockIdentifierResolver,
            viewBoundsResolver = mockBoundsResolver,
            foregroundCapture = fakeForegroundCapture
        ).traverseWindow(root, fakeWindow, fakeFactory, fakeGeneration) as WindowCopyResult.Present

    private fun view(): View = mock<View>().also(::defaults)
    private fun group(): ViewGroup = mock<ViewGroup>().also {
        defaults(it)
        whenever(it.clipChildren).thenReturn(true)
    }

    private fun defaults(view: View) {
        whenever(mockIdentifierResolver.resolveViewId(view)).thenReturn(System.identityHashCode(view).toLong())
        whenever(view.isShown).thenReturn(true)
        whenever(view.alpha).thenReturn(1f)
        whenever(view.scaleX).thenReturn(1f)
        whenever(view.scaleY).thenReturn(1f)
        whenever(view.width).thenReturn(100)
        whenever(view.height).thenReturn(100)
        whenever(view.foregroundGravity).thenReturn(Gravity.FILL)
        whenever(mockBoundsResolver.resolveViewGlobalBounds(view, 1f)).thenReturn(GlobalBounds(0, 0, 100, 100))
        val stubResources: Resources = mock()
        whenever(stubResources.displayMetrics).thenReturn(DisplayMetrics().apply { density = 1f })
        whenever(view.resources).thenReturn(stubResources)
    }

    private fun children(parent: ViewGroup, vararg children: View) {
        whenever(parent.childCount).thenReturn(children.size)
        children.forEachIndexed { index, child ->
            whenever(parent.getChildAt(index)).thenReturn(child)
            whenever(child.parent).thenReturn(parent)
        }
    }

    private fun snapshot(window: CapturedWindow) = CapturedFullSnapshot(
        1L,
        fakeWindow.scope,
        CapturedLayer(
            fakeFactory.screenRoot(),
            CapturedLayerKind.SYNTHETIC_SCREEN_ROOT,
            window.rootLayer.bounds,
            listOf(CapturedChild.Layer(window.rootLayer.identity))
        ),
        window.layers,
        window.wireframes
    )
}
