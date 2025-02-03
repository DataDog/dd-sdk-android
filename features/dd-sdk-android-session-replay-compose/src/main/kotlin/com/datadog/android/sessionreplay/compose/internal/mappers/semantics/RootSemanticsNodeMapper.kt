/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.annotation.UiThread
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.core.graphics.toRect
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.withinComposeBenchmarkSpan
import com.datadog.android.sessionreplay.internal.TouchPrivacyManager
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
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
        colorStringFormatter
    ),
    private val textFieldSemanticsNodeMapper: TextFieldSemanticsNodeMapper = TextFieldSemanticsNodeMapper(
        colorStringFormatter
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
        asyncJobStatusCallback: AsyncJobStatusCallback
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
                mappingContext = mappingContext
            )
        }
        return wireframes
    }

    @UiThread
    private fun createComposerWireframes(
        semanticsNode: SemanticsNode,
        touchPrivacyManager: TouchPrivacyManager,
        wireframes: MutableList<MobileSegment.Wireframe>,
        parentUiContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        mappingContext: MappingContext
    ) {
        // If Hidden node is detected, add placeholder wireframe and return
        if (semanticsUtils.isNodeHidden(semanticsNode)) {
            composeHiddenMapper.map(
                semanticsNode,
                parentUiContext,
                asyncJobStatusCallback
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
                asyncJobStatusCallback = asyncJobStatusCallback
            )
            var currentUiContext = parentUiContext
            semanticsWireframe?.let {
                wireframes.addAll(it.wireframes)
                currentUiContext = it.uiContext ?: currentUiContext
            }
            val children = semanticsNode.children
            children.forEach {
                createComposerWireframes(
                    semanticsNode = it,
                    touchPrivacyManager = touchPrivacyManager,
                    wireframes = wireframes,
                    parentUiContext = currentUiContext,
                    asyncJobStatusCallback = asyncJobStatusCallback,
                    mappingContext = mappingContext
                )
            }
        }
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
