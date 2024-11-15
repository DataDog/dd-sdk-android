/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.withinComposeBenchmarkSpan
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal class RootSemanticsNodeMapper(
    private val colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val semanticsNodeMapper: Map<Role, SemanticsNodeMapper> = mapOf(
        // TODO RUM-6189 Add Mappers for each Semantics Role
        Role.RadioButton to RadioButtonSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Tab to TabSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Button to ButtonSemanticsNodeMapper(colorStringFormatter, semanticsUtils),
        Role.Image to ImageSemanticsNodeMapper(colorStringFormatter)
    ),
    // Text doesn't have a role in semantics, so it should be a fallback mapper.
    private val textSemanticsNodeMapper: TextSemanticsNodeMapper = TextSemanticsNodeMapper(
        colorStringFormatter
    ),
    private val containerSemanticsNodeMapper: ContainerSemanticsNodeMapper = ContainerSemanticsNodeMapper(
        colorStringFormatter,
        semanticsUtils
    )
) {

    internal fun createComposeWireframes(
        semanticsNode: SemanticsNode,
        density: Float,
        mappingContext: MappingContext,
        privacy: SessionReplayPrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
        withinComposeBenchmarkSpan(ROOT_NODE_SPAN_NAME, true) {
            createComposerWireframes(
                semanticsNode = semanticsNode,
                wireframes = wireframes,
                parentUiContext = UiContext(
                    parentContentColor = null,
                    density = density,
                    privacy = privacy,
                    imageWireframeHelper = mappingContext.imageWireframeHelper
                ),
                asyncJobStatusCallback = asyncJobStatusCallback
            )
        }
        return wireframes
    }

    private fun createComposerWireframes(
        semanticsNode: SemanticsNode,
        wireframes: MutableList<MobileSegment.Wireframe>,
        parentUiContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        val mapper = getSemanticsNodeMapper(semanticsNode)
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
                createComposerWireframes(it, wireframes, currentUiContext, asyncJobStatusCallback)
            }
        }
    }

    private fun getSemanticsNodeMapper(
        semanticsNode: SemanticsNode
    ): SemanticsNodeMapper {
        val role = semanticsNode.config.getOrNull(SemanticsProperties.Role)
        return semanticsNodeMapper[role] ?: if (isTextNode(semanticsNode)) {
            textSemanticsNodeMapper
        } else {
            containerSemanticsNodeMapper
        }
    }

    private fun isTextNode(semanticsNode: SemanticsNode): Boolean {
        // Some text semantics nodes don't have an explicit `Role` but the text exists in the config
        return semanticsNode.config.getOrNull(SemanticsProperties.Text)?.isNotEmpty() == true
    }

    companion object {
        private const val ROOT_NODE_SPAN_NAME = "RootNode"
    }
}
