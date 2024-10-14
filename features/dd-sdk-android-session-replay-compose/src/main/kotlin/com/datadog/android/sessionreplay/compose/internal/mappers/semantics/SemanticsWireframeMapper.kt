/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.recorder.MappingContext
import com.datadog.android.sessionreplay.recorder.mapper.BaseWireframeMapper
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter
import com.datadog.android.sessionreplay.utils.DrawableToColorMapper
import com.datadog.android.sessionreplay.utils.ViewBoundsResolver
import com.datadog.android.sessionreplay.utils.ViewIdentifierResolver

internal class SemanticsWireframeMapper(
    viewIdentifierResolver: ViewIdentifierResolver,
    colorStringFormatter: ColorStringFormatter,
    viewBoundsResolver: ViewBoundsResolver,
    drawableToColorMapper: DrawableToColorMapper,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils(),
    private val semanticsNodeMapper: Map<Role, SemanticsNodeMapper> = mapOf(
        // TODO RUM-6189 Add Mappers for each Semantics Role
        Role.Button to ButtonSemanticsNodeMapper(colorStringFormatter),
        Role.Image to ImageSemanticsNodeMapper(colorStringFormatter)
    ),
    // Text doesn't have a role in semantics, so it should be a fallback mapper.
    private val textSemanticsNodeMapper: TextSemanticsNodeMapper = TextSemanticsNodeMapper(colorStringFormatter)
) : BaseWireframeMapper<ComposeView>(
    viewIdentifierResolver,
    colorStringFormatter,
    viewBoundsResolver,
    drawableToColorMapper
) {
    override fun map(
        view: ComposeView,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback,
        internalLogger: InternalLogger
    ): List<MobileSegment.Wireframe> {
        val density = mappingContext.systemInformation.screenDensity.let { if (it == 0.0f) 1.0f else it }
        val privacy = mappingContext.privacy
        return semanticsUtils.findRootSemanticsNode(view)?.let { node ->
            createComposeWireframes(node, density, mappingContext, privacy, asyncJobStatusCallback)
        } ?: emptyList()
    }

    private fun getSemanticsNodeMapper(
        semanticsNode: SemanticsNode
    ): SemanticsNodeMapper {
        val role = semanticsNode.config.getOrNull(SemanticsProperties.Role)
        return semanticsNodeMapper[role] ?: textSemanticsNodeMapper
    }

    private fun createComposeWireframes(
        semanticsNode: SemanticsNode,
        density: Float,
        mappingContext: MappingContext,
        privacy: SessionReplayPrivacy,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): List<MobileSegment.Wireframe> {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
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
        return wireframes
    }

    private fun createComposerWireframes(
        semanticsNode: SemanticsNode,
        wireframes: MutableList<MobileSegment.Wireframe>,
        parentUiContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ) {
        getSemanticsNodeMapper(semanticsNode).map(
            semanticsNode = semanticsNode,
            parentContext = parentUiContext,
            asyncJobStatusCallback = asyncJobStatusCallback
        )?.wireframe?.let {
            wireframes.add(it)
        }
        val children = semanticsNode.children
        children.forEach {
            createComposerWireframes(it, wireframes, parentUiContext, asyncJobStatusCallback)
        }
    }
}
