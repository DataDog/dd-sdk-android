/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.compose.internal.mappers.semantics

import androidx.compose.ui.semantics.SemanticsNode
import com.datadog.android.sessionreplay.TextAndInputPrivacy
import com.datadog.android.sessionreplay.compose.internal.data.SemanticsWireframe
import com.datadog.android.sessionreplay.compose.internal.data.UiContext
import com.datadog.android.sessionreplay.compose.internal.utils.SemanticsUtils
import com.datadog.android.sessionreplay.compose.internal.utils.transformCapturedText
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.utils.ColorStringFormatter

internal open class TextSemanticsNodeMapper(
    colorStringFormatter: ColorStringFormatter,
    private val semanticsUtils: SemanticsUtils = SemanticsUtils()
) : AbstractSemanticsNodeMapper(colorStringFormatter, semanticsUtils) {

    override fun map(
        semanticsNode: SemanticsNode,
        parentContext: UiContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ): SemanticsWireframe {
        val wireframes = mutableListOf<MobileSegment.Wireframe>()
        val textAndInputPrivacy = semanticsUtils.getTextAndInputPrivacyOverride(semanticsNode)
            ?: parentContext.textAndInputPrivacy
        val textWireframe = resolveTextWireFrame(parentContext, semanticsNode, textAndInputPrivacy)
        val backgroundWireframes = resolveModifierWireframes(semanticsNode)
        wireframes.addAll(backgroundWireframes)
        textWireframe?.let {
            wireframes.add(it)
        }
        return SemanticsWireframe(
            wireframes = wireframes.toList(),
            parentContext.copy(textAndInputPrivacy = textAndInputPrivacy)
        )
    }

    protected fun resolveTextWireFrame(
        parentContext: UiContext,
        semanticsNode: SemanticsNode,
        textAndInputPrivacy: TextAndInputPrivacy
    ): MobileSegment.Wireframe.TextWireframe? {
        val textLayoutInfo = semanticsUtils.resolveTextLayoutInfo(semanticsNode)
        val capturedText = textLayoutInfo?.text?.let {
            transformCapturedText(it, textAndInputPrivacy)
        }
        val bounds = resolveBounds(semanticsNode)
        return capturedText?.let { text ->
            MobileSegment.Wireframe.TextWireframe(
                id = semanticsNode.id.toLong(),
                x = bounds.x,
                y = bounds.y,
                width = bounds.width,
                height = bounds.height,
                text = text,
                textStyle = resolveTextStyle(parentContext, textLayoutInfo),
                textPosition = resolveTextAlign(textLayoutInfo)
            )
        }
    }

    protected fun resolveTextStyle(
        parentContext: UiContext,
        textLayoutInfo: TextLayoutInfo?
    ): MobileSegment.TextStyle {
        return textLayoutInfo?.let {
            resolveTextLayoutInfoToTextStyle(parentContext, it)
        } ?: defaultTextStyle
    }
}
