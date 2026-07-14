/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.content.Context
import android.view.View
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.platform.AndroidComposeView
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.internal.async.RecordedDataQueueRefs
import com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCaptureFallbackMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WebViewWireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.InteropViewCallback
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.SystemInformation
import com.datadog.android.sessionreplay.recorder.mapper.TextViewMapper
import com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.utils.GlobalBounds
import com.datadog.android.sessionreplay.utils.ImageWireframeHelper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class CompositionTreeBuilderTest {

    private lateinit var testedBuilder: CompositionTreeBuilder

    @Mock
    lateinit var mockViewIdentifierResolver: ViewIdentifierResolver

    @Mock
    lateinit var mockViewBoundsResolver: ViewBoundsResolver

    @Mock
    lateinit var mockTextViewMapper: TextViewMapper<TextView>

    @Mock
    lateinit var mockWebViewMapper: WebViewWireframeMapper

    @Mock
    lateinit var mockViewWireframeMapper: WireframeMapper<View>

    @Mock
    lateinit var mockPixelCaptureFallbackMapper: PixelCaptureFallbackMapper

    @Mock
    lateinit var mockTouchPrivacyManager: TouchPrivacyManager

    @Mock
    lateinit var mockImageWireframeHelper: ImageWireframeHelper

    @Mock
    lateinit var mockViewUtilsInternal: ViewUtilsInternal

    @Mock
    lateinit var mockRecordedDataQueueRefs: RecordedDataQueueRefs

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Forgery
    lateinit var fakeSystemInformation: SystemInformation

    @Forgery
    lateinit var fakeGlobalBounds: GlobalBounds

    @Forgery
    lateinit var fakeMappingContext: MappingContext

    private var nextId = 0L

    @BeforeEach
    fun `set up`() {
        whenever(mockViewUtilsInternal.isNotVisible(any())).thenReturn(false)
        whenever(mockViewUtilsInternal.isSystemNoise(any())).thenReturn(false)
        whenever(
            mockViewIdentifierResolver.resolveChildUniqueIdentifier(
                any(),
                eq(CompositionTreeBuilder.COMPOSITION_LAYER_KEY_NAME)
            )
        ).thenAnswer { nextId++ }
        whenever(mockViewBoundsResolver.resolveViewGlobalBounds(any(), any())).thenReturn(fakeGlobalBounds)

        testedBuilder = CompositionTreeBuilder(
            viewIdentifierResolver = mockViewIdentifierResolver,
            viewBoundsResolver = mockViewBoundsResolver,
            textViewMapper = mockTextViewMapper,
            webViewMapper = mockWebViewMapper,
            viewWireframeMapper = mockViewWireframeMapper,
            pixelCaptureFallbackMapper = mockPixelCaptureFallbackMapper,
            touchPrivacyManager = mockTouchPrivacyManager,
            imageWireframeHelper = mockImageWireframeHelper,
            viewUtilsInternal = mockViewUtilsInternal
        )
    }

    private fun buildOutput(rootView: View): CompositionTreeBuilder.Output {
        return buildOutput(listOf(rootView))
    }

    private fun buildOutput(rootViews: List<View>): CompositionTreeBuilder.Output {
        return testedBuilder.build(
            rootViews = rootViews,
            systemInformation = fakeSystemInformation,
            textAndInputPrivacy = TextAndInputPrivacy.MASK_SENSITIVE_INPUTS,
            imagePrivacy = ImagePrivacy.MASK_NONE,
            recordedDataQueueRefs = mockRecordedDataQueueRefs,
            internalLogger = mockInternalLogger
        )
    }

    @Test
    fun `M map via textViewMapper W build() {leaf is a TextView}`(forge: Forge) {
        // Given
        val mockChild = forge.aMockTextView()
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.TextWireframe>() }
        whenever(
            mockTextViewMapper.map(eq(mockChild), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        verifyNoInteractions(mockPixelCaptureFallbackMapper)
    }

    @Test
    fun `M pixel-capture the leaf W build() {leaf is not a TextView}`(forge: Forge) {
        // Given — mirrors any leaf with no dedicated mapper and no simple background shortcut
        // (see isSimpleContainerView), including Jetpack Compose content. ImageView specifically
        // (not a plain View) — a bare View mock would now legitimately take the
        // viewWireframeMapper shortcut instead, since it can never draw more than its background.
        val mockChild = forge.aMockView<ImageView>()
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.ImageWireframe>() }
        whenever(
            mockPixelCaptureFallbackMapper.map(eq(mockChild), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        verifyNoInteractions(mockTextViewMapper)
    }

    @Test
    fun `M map via webViewMapper W build() {leaf is a WebView}`(forge: Forge) {
        // Given — a pixel capture can't see WebView content at all (it composites through a path
        // View.draw never touches), so it must go through the dedicated mapper instead, same as
        // the default pipeline's TreeViewTraversal does.
        val mockChild = forge.aMockView<WebView>()
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.WebviewWireframe>() }
        whenever(
            mockWebViewMapper.map(eq(mockChild), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockChild)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        verifyNoInteractions(mockTextViewMapper)
        verifyNoInteractions(mockPixelCaptureFallbackMapper)
    }

    @Test
    fun `M treat WebView as a leaf W build() {WebView reports children}`(forge: Forge) {
        // Given — WebView is itself a ViewGroup, but its real content isn't rendered through any
        // child the traversal could recurse into, the same reasoning as isComposeHostView: it
        // must always take the leaf path regardless of ViewGroup.getChildCount.
        val mockWebView = forge.aMockView<WebView>()
        whenever(mockWebView.childCount).thenReturn(1)
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.WebviewWireframe>() }
        whenever(
            mockWebViewMapper.map(eq(mockWebView), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockWebView)

        // When
        val output = buildOutput(mockRoot)

        // Then — captured as one leaf wireframe, never turned into its own CompositionLayer
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        val rootChildren = output.compositionTree!!.root.children
        assertThat(rootChildren).hasSize(1)
        assertThat(rootChildren.single().type).isEqualTo(MobileSegment.Type.WIREFRAME)
    }

    @Test
    fun `M map via viewWireframeMapper W build() {leaf is exactly a plain View}`(forge: Forge) {
        // Given — a real instance, not a Mockito mock: a mock's javaClass is always a
        // dynamically-generated subclass, which would never satisfy the exact-class check this
        // behavior depends on (see the class doc for why that check must be exact, not `is`).
        val plainView = View(mock<Context>())
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.ShapeWireframe>() }
        whenever(
            mockViewWireframeMapper.map(eq(plainView), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(plainView)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        verifyNoInteractions(mockPixelCaptureFallbackMapper)
    }

    @Test
    fun `M map via viewWireframeMapper W build() {leaf is exactly a FrameLayout with no children}`(
        forge: Forge
    ) {
        // Given — childCount 0 is FrameLayout's real, default behavior here (nothing added to
        // it), so this also exercises childReferences() routing it to the leaf path normally,
        // not via a forced override the way WebView/Compose hosts need.
        val plainFrameLayout = FrameLayout(mock<Context>())
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.ShapeWireframe>() }
        whenever(
            mockViewWireframeMapper.map(eq(plainFrameLayout), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(plainFrameLayout)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        verifyNoInteractions(mockPixelCaptureFallbackMapper)
    }

    @Test
    fun `M fall through to pixel capture W build() {viewWireframeMapper resolves nothing, image background}`(
        forge: Forge
    ) {
        // Given — viewWireframeMapper returns an empty list for a background it can't reduce to
        // a color (an image or vector drawable — see DrawableToColorMapper) or no background at
        // all. Accepting that at face value would silently blank out real content a pixel
        // capture could show correctly, so it must fall through instead of being trusted blindly.
        val plainView = View(mock<Context>())
        whenever(
            mockViewWireframeMapper.map(eq(plainView), any(), any(), eq(mockInternalLogger))
        ).thenReturn(emptyList())
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.ImageWireframe>() }
        whenever(
            mockPixelCaptureFallbackMapper.map(eq(plainView), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(plainView)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
    }

    @Test
    fun `M pixel-capture the leaf W build() {leaf is a View subclass, not exactly View}`(forge: Forge) {
        // Given — a subclass of a "simple" class could override onDraw to paint real custom
        // content of its own; this must NOT take the viewWireframeMapper shortcut, even though
        // it "is" a View, or that content would be silently replaced with just its background.
        val customView = CustomDrawingView(mock<Context>())
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.ImageWireframe>() }
        whenever(
            mockPixelCaptureFallbackMapper.map(eq(customView), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(customView)

        // When
        val output = buildOutput(mockRoot)

        // Then
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        verifyNoInteractions(mockViewWireframeMapper)
    }

    private class CustomDrawingView(context: Context) : View(context)

    @Test
    fun `M build a nested layer W build() {native ViewGroup with children}`(forge: Forge) {
        // Given
        val mockChild = forge.aMockTextView()
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.TextWireframe>() }
        whenever(
            mockTextViewMapper.map(eq(mockChild), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockContainer = forge.aMockView<LinearLayout>()
        whenever(mockContainer.childCount).thenReturn(1)
        whenever(mockContainer.getChildAt(0)).thenReturn(mockChild)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockContainer)

        // When
        val output = buildOutput(mockRoot)

        // Then — the container became its own nested layer instead of being flattened, and the
        // child TextView's wireframe is referenced from that nested layer, not from root directly
        val rootChildren = output.compositionTree!!.root.children
        assertThat(rootChildren).hasSize(1)
        assertThat(rootChildren.single().type).isEqualTo(MobileSegment.Type.LAYER)

        val nestedLayer = output.compositionTree.layers!!.single { it.id == rootChildren.single().id }
        assertThat(nestedLayer.children).hasSize(1)
        assertThat(nestedLayer.children.single().type).isEqualTo(MobileSegment.Type.WIREFRAME)
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
    }

    @Test
    fun `M pixel-capture the whole view W build() {leaf is an AndroidComposeView with a child}`(forge: Forge) {
        // Given — AndroidComposeView always carries an internal AndroidViewsHandler child even
        // when nothing is drawn through it, so childCount alone can't route it to buildLayer the
        // way it correctly does for ordinary ViewGroups: it must always take the leaf path.
        val composeView = AndroidComposeView(mock<Context>())
        val fakeChildWireframes: List<MobileSegment.Wireframe> =
            forge.aList(size = 1) { getForgery<MobileSegment.Wireframe.ImageWireframe>() }
        whenever(
            mockPixelCaptureFallbackMapper.map(eq(composeView), any(), any(), eq(mockInternalLogger))
        ).thenReturn(fakeChildWireframes)

        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(composeView)

        // When
        val output = buildOutput(mockRoot)

        // Then — captured as one leaf wireframe, never turned into its own CompositionLayer
        assertThat(output.wireframes).containsExactlyElementsOf(fakeChildWireframes)
        assertThat(output.compositionTree!!.layers.orEmpty()).noneMatch { it.children.isEmpty() }
        val rootChildren = output.compositionTree.root.children
        assertThat(rootChildren).hasSize(1)
        assertThat(rootChildren.single().type).isEqualTo(MobileSegment.Type.WIREFRAME)
    }

    @Test
    fun `M return an empty list W interopViewCallback map() {this pipeline never handles Compose interop}`(
        forge: Forge
    ) {
        // Given — capture the MappingContext build() constructs internally, via a leaf that
        // falls through to the pixel-capture fallback (same MappingContext instance flows
        // unchanged through the whole recursive build). ImageView, not a plain View — see the
        // other leaf-routing tests for why a bare View mock would take a different path now.
        val mockPlainChild = forge.aMockView<ImageView>()
        val mockRoot = forge.aMockView<FrameLayout>()
        whenever(mockRoot.childCount).thenReturn(1)
        whenever(mockRoot.getChildAt(0)).thenReturn(mockPlainChild)

        var capturedInteropViewCallback: InteropViewCallback? = null
        whenever(
            mockPixelCaptureFallbackMapper.map(eq(mockPlainChild), any(), any(), eq(mockInternalLogger))
        ).thenAnswer {
            capturedInteropViewCallback = it.getArgument<MappingContext>(1).interopViewCallback
            emptyList<MobileSegment.Wireframe>()
        }

        buildOutput(mockRoot)

        // When — this pipeline is not expected to ever encounter one, but the callback must not
        // throw if it somehow does
        val interopView = forge.aMockTextView()
        val result = capturedInteropViewCallback!!.map(interopView, fakeMappingContext)

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M synthesize a wrapper root W build() {multiple windows}`(forge: Forge) {
        // Given — two independent windows, each with no children of their own
        val mockFirstWindow = forge.aMockView<FrameLayout>()
        val mockSecondWindow = forge.aMockView<FrameLayout>()

        // When
        val output = buildOutput(listOf(mockFirstWindow, mockSecondWindow))

        // Then — neither window's own id becomes the tree's root; a synthetic wrapper does
        val compositionTree = output.compositionTree!!
        assertThat(compositionTree.root.id).isEqualTo(CompositionTreeBuilder.SYNTHETIC_ROOT_LAYER_ID)
        assertThat(compositionTree.root.children).hasSize(2)
        assertThat(compositionTree.root.children).allMatch { it.type == MobileSegment.Type.LAYER }

        val childIds = compositionTree.root.children.map { it.id }
        val layerIds = compositionTree.layers!!.map { it.id }
        assertThat(layerIds).hasSize(2)
        assertThat(layerIds).containsExactlyInAnyOrderElementsOf(childIds)
    }

    @Test
    fun `M return no composition tree W build() {no windows}`() {
        // When
        val output = buildOutput(emptyList())

        // Then
        assertThat(output.compositionTree).isNull()
        assertThat(output.wireframes).isEmpty()
    }

    @Test
    fun `M keep the single window as root W build() {single window, no synthetic wrapper}`(forge: Forge) {
        // Given
        val mockRoot = forge.aMockView<FrameLayout>()

        // When
        val output = buildOutput(mockRoot)

        // Then — no synthetic wrapper introduced when there's only one window
        val compositionTree = output.compositionTree!!
        assertThat(compositionTree.root.id).isNotEqualTo(CompositionTreeBuilder.SYNTHETIC_ROOT_LAYER_ID)
        assertThat(compositionTree.layers.orEmpty().map { it.id }).doesNotContain(compositionTree.root.id)
    }

    @Test
    fun `M drop hidden windows W build() {root-level visibility filtering}`(forge: Forge) {
        // Given — isNotVisible is stubbed false globally in setUp(); override it for one window
        val mockVisibleWindow = forge.aMockView<FrameLayout>()
        val mockHiddenWindow = forge.aMockView<FrameLayout>()
        whenever(mockViewUtilsInternal.isNotVisible(mockHiddenWindow)).thenReturn(true)

        // When
        val output = buildOutput(listOf(mockHiddenWindow, mockVisibleWindow))

        // Then — the hidden window never reached buildLayer, so only one window remains and it
        // becomes the root directly, with no synthetic wrapper
        val compositionTree = output.compositionTree!!
        assertThat(compositionTree.root.id).isNotEqualTo(CompositionTreeBuilder.SYNTHETIC_ROOT_LAYER_ID)
    }
}
