/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import android.graphics.Rect
import android.view.View
import androidx.annotation.UiThread
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.graphics.toRect
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.ImagePrivacy
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.withinComposeBenchmarkSpan
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.PixelCaptureEligibility
import com.datadog.android.sessionreplay.recorder.WireframeSlot
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class RootSemanticsNodeMapper(
    private val colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val semanticsNodeMapper: Map<Role, SemanticsNodeMapper> = mapOf(
        Role.RadioButton to RadioButtonSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Tab to TabSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Button to ButtonSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Image to ImageSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Checkbox to CheckboxSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Switch to SwitchSemanticsNodeMapper(colorStringFormatter, semanticsUtils)
    ),
    // Text doesn't have a role in semantics, so it should be a fallback mapper.
    private val textSemanticsNodeMapper: TextSemanticsNodeMapper = TextSemanticsNodeMapper(
        colorStringFormatter,
        semanticsUtils
    ),
    private val textFieldSemanticsNodeMapper: TextFieldSemanticsNodeMapper = TextFieldSemanticsNodeMapper(
        colorStringFormatter,
        semanticsUtils
    ),
    private val containerSemanticsNodeMapper: ContainerSemanticsNodeMapper = ContainerSemanticsNodeMapper(
        colorStringFormatter,
        semanticsUtils
    ),
    private val composeHiddenMapper: ComposeHiddenMapper = ComposeHiddenMapper(
        colorStringFormatter,
        semanticsUtils
    ),
    private val sliderSemanticsNodeMapper: SliderSemanticsNodeMapper = SliderSemanticsNodeMapper(
        colorStringFormatter,
        semanticsUtils
    )
) {

    @UiThread
    internal fun createComposeWireframes(
        semanticsNode: SemanticsNode,
        density: Float,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger,
        // The host AndroidComposeView/ComposeView — passed to View.draw for isolated
        // composable capture (no overlying views contaminate the result).
        hostView: View? = null
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
        withinComposeBenchmarkSpan(ROOT_NODE_SPAN_NAME, true) {
            createComposerWireframes(
                semanticsNode = semanticsNode,
                wireframes = wireframes,
                touchPrivacyManager = mappingContext.touchPrivacyManager,
                parentUiContext = UiContext(
                    parentContentColor = null,
                    density = density,
                    imagePrivacy = mappingContext.imagePrivacy,
                    textAndInputPrivacy = mappingContext.textAndInputPrivacy,
                    imageWireframeHelper = mappingContext.imageWireframeHelper
                ),
                asyncJobStatusCallback = asyncJobStatusCallback,
                mappingContext = mappingContext,
                internalLogger = internalLogger,
                density = density,
                hostView = hostView
            )
        }
        return wireframes
    }

    @Suppress("ReturnCount")
    @UiThread
    private fun createComposerWireframes(
        semanticsNode: SemanticsNode,
        touchPrivacyManager: TouchPrivacyManager,
        wireframes: MutableList<MobileSegment.Wireframe>,
        parentUiContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        mappingContext: MappingContext,
        internalLogger: InternalLogger,
        density: Float,
        hostView: View?
    ) {
        if (semanticsUtils.isNodePositionUnavailable(semanticsNode)) {
            // If we cant get the real position, we skip the node.
            // This is to prevent visual artifacts of leaf nodes at 0,0 in the replays.
            return
        }

        // If Hidden node is detected, add placeholder wireframe and return
        if (semanticsUtils.isNodeHidden(semanticsNode)) {
            composeHiddenMapper.map(
                semanticsNode,
                parentUiContext,
                asyncJobStatusCallback,
                internalLogger
            )?.let {
                wireframes.addAll(it.wireframes)
            }
            return
        }

        val interopView = semanticsUtils.getInteropView(semanticsNode)

        if (interopView != null) {
            val interopViewWireframes =
                mappingContext.interopViewCallback.map(interopView, mappingContext)
            wireframes.addAll(interopViewWireframes)
            return
        }

        val mapper = getSemanticsNodeMapper(semanticsNode)
        updateTouchOverrideAreas(
            touchPrivacyManager = touchPrivacyManager,
            semanticsNode = semanticsNode
        )
        withinComposeBenchmarkSpan(
            mapper::class.java.simpleName,
            isContainer = mapper is ContainerSemanticsNodeMapper
        ) {
            val semanticsWireframe = mapper.map(
                semanticsNode = semanticsNode,
                parentContext = parentUiContext,
                asyncJobStatusCallback = asyncJobStatusCallback,
                internalLogger = internalLogger
            )
            var currentUiContext = parentUiContext
            val producedWireframes = semanticsWireframe?.wireframes.orEmpty()
            val children = semanticsNode.children

            // Dark-spot detection. If this is a leaf node (no children) and the
            // semantic mapper produced no wireframes, the composable is likely doing
            // custom Canvas drawing with no semantic equivalent. Attempt a PixelCopy crop
            // of the exact node bounds from the last captured full-window frame.
            if (producedWireframes.isEmpty() && children.isEmpty()) {
                // Resolve the privacy settings that actually apply to THIS node — honoring any
                // composable-level override (Modifier tagging this node or an ancestor), not
                // just the global default. semanticsWireframe.uiContext, when present, already
                // reflects that resolution; fall back to the inherited parent value.
                val effectiveImagePrivacy = semanticsWireframe?.uiContext?.imagePrivacy
                    ?: parentUiContext.imagePrivacy
                val effectiveTextAndInputPrivacy = semanticsWireframe?.uiContext?.textAndInputPrivacy
                    ?: parentUiContext.textAndInputPrivacy
                val pixelWireframe = tryPixelCopyCropForNode(
                    semanticsNode = semanticsNode,
                    hostView = hostView,
                    density = density,
                    mappingContext = mappingContext,
                    imagePrivacy = effectiveImagePrivacy,
                    textAndInputPrivacy = effectiveTextAndInputPrivacy,
                    asyncJobStatusCallback = asyncJobStatusCallback,
                    wireframes = wireframes
                )
                if (pixelWireframe != null) {
                    wireframes.add(pixelWireframe)
                    return // Pixel-captured — children are included in pixels, skip recursion
                }
            }

            wireframes.addAll(producedWireframes)
            currentUiContext = semanticsWireframe?.uiContext ?: currentUiContext
            children.forEach {
                createComposerWireframes(
                    semanticsNode = it,
                    touchPrivacyManager = touchPrivacyManager,
                    wireframes = wireframes,
                    parentUiContext = currentUiContext,
                    asyncJobStatusCallback = asyncJobStatusCallback,
                    mappingContext = mappingContext,
                    internalLogger = internalLogger,
                    density = density,
                    hostView = hostView
                )
            }
        }
    }

    /**
     * Registers a pending crop for this Compose dark-spot node via [PixelCropCallback.registerPendingCrop].
     *
     * The decision between PixelCopy (full fidelity) and View.draw isolation (no overlay
     * contamination) is deferred to post-traversal [processPendingCrops] once all wireframe
     * bounds are known. An [MobileSegment.Wireframe.ImageWireframe] stub (isEmpty=true) is
     * returned immediately and populated asynchronously.
     *
     * Returns null (triggering the dark-spot detection fallback) if no [PixelCropCallback] is
     * available, no host view is provided, the node has no drawable area, or
     * [PixelCaptureEligibility] disallows capture given the current privacy settings — shared
     * with the View-side [com.datadog.android.sessionreplay.internal.recorder.mapper.PixelCopyFallbackMapper].
     */
    private fun tryPixelCopyCropForNode(
        semanticsNode: SemanticsNode,
        hostView: View?,
        density: Float,
        mappingContext: MappingContext,
        imagePrivacy: ImagePrivacy,
        textAndInputPrivacy: TextAndInputPrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        wireframes: MutableList<MobileSegment.Wireframe>
    ): MobileSegment.Wireframe? {
        val pixelCropCallback = mappingContext.pixelCropCallback ?: return null
        hostView ?: return null

        val bounds = semanticsNode.boundsInRoot
        if (bounds.width <= 0f || bounds.height <= 0f) return null

        val nodeId = semanticsNode.id.toLong()
        val globalBounds = semanticsUtils.resolveInnerBounds(semanticsNode)

        if (!PixelCaptureEligibility.isEligible(
                textAndInputPrivacy = textAndInputPrivacy,
                imagePrivacy = imagePrivacy,
                boundsDp = globalBounds
            )
        ) {
            return null
        }

        // Isolation clip rect: composable's bounds within the host view's coordinate space.
        // boundsInRoot is already relative to the AndroidComposeView / ComposeView.
        val isolationClipRect = Rect(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )
        if (isolationClipRect.width() <= 0 || isolationClipRect.height() <= 0) return null

        // Window-pixel rect for the PixelCopy crop path.
        val hostViewLocation = IntArray(2)
        hostView.getLocationInWindow(hostViewLocation)
        val windowRect = Rect(
            hostViewLocation[0] + bounds.left.toInt(),
            hostViewLocation[1] + bounds.top.toInt(),
            hostViewLocation[0] + bounds.right.toInt(),
            hostViewLocation[1] + bounds.bottom.toInt()
        )

        val imageWireframe = MobileSegment.Wireframe.ImageWireframe(
            id = nodeId,
            x = globalBounds.x,
            y = globalBounds.y,
            width = globalBounds.width,
            height = globalBounds.height,
            isEmpty = true
        )

        // The caller adds the returned wireframe at this index immediately after this call
        // returns — capture it now so the placeholder swap can target the right slot later.
        val slotIndex = wireframes.size

        // registerPendingCrop calls jobStarted() and defers the capture decision
        // (PixelCopy vs isolation vs placeholder) until processPendingCrops has the full
        // wireframe picture.
        pixelCropCallback.registerPendingCrop(
            nodeId = nodeId,
            windowRect = windowRect,
            dpBounds = globalBounds,
            isolationView = hostView,
            isolationClipRect = isolationClipRect,
            wireframe = imageWireframe,
            wireframeSlot = WireframeSlot { wireframes[slotIndex] = it },
            asyncJobStatusCallback = asyncJobStatusCallback
        )

        return imageWireframe
    }

    private fun getSemanticsNodeMapper(
        semanticsNode: SemanticsNode
    ): SemanticsNodeMapper {
        val role = semanticsNode.config.getOrNull(SemanticsProperties.Role)
        return semanticsNodeMapper[role] ?: when {
            isTextFieldNode(semanticsNode) -> textFieldSemanticsNodeMapper
            isTextNode(semanticsNode) -> textSemanticsNodeMapper
            isSliderNode(semanticsNode) -> sliderSemanticsNodeMapper
            else -> containerSemanticsNodeMapper
        }
    }

    private fun isTextNode(semanticsNode: SemanticsNode): Boolean {
        // Some text semantics nodes don't have an explicit `Role` but the text exists in the config
        return semanticsNode.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
    }

    private fun isTextFieldNode(semanticsNode: SemanticsNode): Boolean {
        return semanticsNode.config.contains(SemanticsActions.SetText)
    }

    private fun isSliderNode(semanticsNode: SemanticsNode): Boolean {
        return semanticsUtils.getProgressBarRangeInfo(semanticsNode) != null
    }

    @UiThread
    private fun updateTouchOverrideAreas(
        semanticsNode: SemanticsNode,
        touchPrivacyManager: TouchPrivacyManager
    ) {
        semanticsUtils.getTouchPrivacyOverride(semanticsNode)?.let { touchPrivacy ->
            val viewArea = semanticsNode.boundsInRoot.toAndroidRectF().toRect()
            touchPrivacyManager.addTouchOverrideArea(viewArea, touchPrivacy)
        }
    }

    companion object {
        private const val ROOT_NODE_SPAN_NAME = "RootNode"
    }
}
