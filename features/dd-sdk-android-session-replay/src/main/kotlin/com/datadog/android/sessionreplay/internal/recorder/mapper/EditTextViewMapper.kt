/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.EditText
import com.datadog.android.sessionreplay.SessionReplayPrivacy
import com.datadog.android.sessionreplay.internal.AsyncJobStatusCallback
import com.datadog.android.sessionreplay.internal.recorder.MappingContext
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

/**
 * A [WireframeMapper] implementation to map a [EditText] component in case the
 * [SessionReplayPrivacy.ALLOW] rule was used in the configuration.
 * In this case the mapper will use the provided [textViewMapper] used for the current privacy
 * level and will only mask the [EditText] for which the input type is considered sensible
 * (password, email, address, postal address, numeric password) with the static mask: [***].
 */
internal open class EditTextViewMapper(
    internal val textViewMapper: TextViewMapper,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils,
    stringUtils: StringUtils = StringUtils
) : BaseWireframeMapper<EditText, MobileSegment.Wireframe>(
    viewUtils = viewUtils,
    stringUtils = stringUtils
) {

    override fun map(
        view: EditText,
        mappingContext: MappingContext,
        asyncJobStatusCallback: AsyncJobStatusCallback
    ):
        List<MobileSegment.Wireframe> {
        val mainWireframeList = textViewMapper.map(view, mappingContext, asyncJobStatusCallback)
        resolveUnderlineWireframe(view, mappingContext.systemInformation.screenDensity)
            ?.let { wireframe ->
                return mainWireframeList + wireframe
            }
        return mainWireframeList
    }

    private fun resolveUnderlineWireframe(
        parent: EditText,
        pixelsDensity: Float
    ): MobileSegment.Wireframe? {
        val identifier = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            parent,
            UNDERLINE_KEY_NAME
        ) ?: return null
        val viewGlobalBounds = resolveViewGlobalBounds(parent, pixelsDensity)
        val fieldUnderlineColor = resolveUnderlineColor(parent)
        return MobileSegment.Wireframe.ShapeWireframe(
            identifier,
            viewGlobalBounds.x,
            viewGlobalBounds.y + viewGlobalBounds.height - UNDERLINE_HEIGHT_IN_PIXELS,
            viewGlobalBounds.width,
            UNDERLINE_HEIGHT_IN_PIXELS,
            shapeStyle = MobileSegment.ShapeStyle(
                backgroundColor = fieldUnderlineColor,
                opacity = parent.alpha
            )
        )
    }

    private fun resolveUnderlineColor(view: EditText): String {
        view.backgroundTintList?.let {
            return colorAndAlphaAsStringHexa(
                it.defaultColor,
                OPAQUE_ALPHA_VALUE
            )
        }
        return colorAndAlphaAsStringHexa(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    companion object {
        internal const val UNDERLINE_HEIGHT_IN_PIXELS = 1L
        internal const val UNDERLINE_KEY_NAME = "underline"
    }
}
