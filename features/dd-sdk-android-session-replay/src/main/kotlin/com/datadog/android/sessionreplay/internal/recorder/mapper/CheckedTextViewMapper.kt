/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.CheckedTextView
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal open class CheckedTextViewMapper(
    textWireframeMapper: TextViewMapper,
    private val stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils
) : CheckableTextViewMapper<CheckedTextView>(
    textWireframeMapper,
    stringUtils,
    uniqueIdentifierGenerator,
    viewUtils
) {

    // region CheckableTextViewMapper

    override fun resolveCheckableColor(view: CheckedTextView): String {
        view.checkMarkTintList?.let {
            return stringUtils.formatColorAndAlphaAsHexa(
                it.defaultColor,
                OPAQUE_ALPHA_VALUE
            )
        }
        return stringUtils.formatColorAndAlphaAsHexa(view.currentTextColor, OPAQUE_ALPHA_VALUE)
    }

    override fun resolveCheckableBounds(view: CheckedTextView, pixelsDensity: Float): GlobalBounds {
        val viewGlobalBounds = resolveViewGlobalBounds(view, pixelsDensity)
        val textViewPaddingRight =
            view.totalPaddingRight.toLong().densityNormalized(pixelsDensity)
        var checkBoxHeight = 0L
        val checkMarkDrawable = view.checkMarkDrawable
        if (checkMarkDrawable != null && checkMarkDrawable.intrinsicHeight > 0) {
            val height = checkMarkDrawable.intrinsicHeight -
                view.totalPaddingTop -
                view.totalPaddingBottom
            checkBoxHeight = height.toLong().densityNormalized(pixelsDensity)
        }

        return GlobalBounds(
            x = viewGlobalBounds.x + viewGlobalBounds.width - textViewPaddingRight,
            y = viewGlobalBounds.y,
            width = checkBoxHeight,
            height = checkBoxHeight

        )
    }

    // endregion
}
