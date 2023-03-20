/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.EditText
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal open class EditTextViewMapper(
    private val textWireframeMapper: TextWireframeMapper,
    private val uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils
) : BaseWireframeMapper<EditText, MobileSegment.Wireframe>(viewUtils = viewUtils) {

    override fun map(view: EditText, systemInformation: SystemInformation):
        List<MobileSegment.Wireframe> {
        val mainWireframeList = textWireframeMapper.map(view, systemInformation)
        resolveUnderlineWireframe(view, systemInformation.screenDensity)?.let { wireframe ->
            return mainWireframeList + wireframe
        }
        return mainWireframeList
    }

    private fun resolveUnderlineWireframe(parent: View, pixelsDensity: Float): MobileSegment.Wireframe? {
        val backgroundTintList = resolveBackgroundTintList(parent)
        return if (backgroundTintList != null) {
            resolveUnderlineWireframe(backgroundTintList, parent, pixelsDensity)
        } else {
            null
        }
    }

    private fun resolveUnderlineWireframe(
        backgroundTintList: ColorStateList,
        parent: View,
        pixelsDensity: Float
    ): MobileSegment.Wireframe? {
        val identifier = uniqueIdentifierGenerator.resolveChildUniqueIdentifier(
            parent,
            UNDERLINE_KEY_NAME
        ) ?: return null
        val viewGlobalBounds = resolveViewGlobalBounds(parent, pixelsDensity)
        val fieldUnderlineColor = colorAndAlphaAsStringHexa(
            backgroundTintList.defaultColor,
            OPAQUE_ALPHA_VALUE
        )
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

    private fun resolveBackgroundTintList(view: View): ColorStateList? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.backgroundTintList
        } else {
            null
        }
    }

    companion object {
        internal const val UNDERLINE_HEIGHT_IN_PIXELS = 1L
        internal const val UNDERLINE_KEY_NAME = "underline"
    }
}
