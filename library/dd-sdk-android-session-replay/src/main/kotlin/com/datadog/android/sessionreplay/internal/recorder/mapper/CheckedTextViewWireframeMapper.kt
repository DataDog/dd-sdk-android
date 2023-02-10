/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.CheckedTextView
import com.datadog.android.sessionreplay.internal.recorder.ViewUtils
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.utils.StringUtils
import com.datadog.android.sessionreplay.model.MobileSegment

internal class CheckedTextViewWireframeMapper(
    private val textWireframeMapper: TextWireframeMapper,
    private val stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierResolver =
        UniqueIdentifierResolver,
    viewUtils: ViewUtils = ViewUtils()
) : CheckableWireframeMapper<CheckedTextView>(uniqueIdentifierGenerator, viewUtils) {

    override fun map(view: CheckedTextView, pixelsDensity: Float): List<MobileSegment.Wireframe> {
        val mainWireframeList = textWireframeMapper.map(view, pixelsDensity)
        resolveCheckableWireframe(view, pixelsDensity)?.let { wireframe ->
            return mainWireframeList + wireframe
        }
        return mainWireframeList
    }

    override fun resolveCheckBoxColor(view: CheckedTextView): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            view.checkMarkTintList?.let {
                return stringUtils.formatColorAndAlphaAsHexa(
                    it.defaultColor,
                    OPAQUE_ALPHA_VALUE
                )
            }
        }
        return stringUtils.formatColorAndAlphaAsHexa(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    override fun resolveCheckBoxSize(view: CheckedTextView, pixelsDensity: Float): Long {
        val checkMarkDrawable = view.checkMarkDrawable
        return if (checkMarkDrawable != null && checkMarkDrawable.intrinsicHeight > 0) {
            val height = checkMarkDrawable.intrinsicHeight -
                view.totalPaddingTop -
                view.totalPaddingBottom
            // to solve the current font issues on the player side we lower the original font
            // size with 1 unit. We will need to normalize the current checkbox size
            // to this new size
            (height * view.textSize / (view.textSize - 1)).toLong().densityNormalized(pixelsDensity)
        } else {
            0L
        }
    }
}
