/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.CompoundButton
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.ViewUtils
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.internal.utils.StringUtils

internal abstract class CheckableCompoundButtonMapper<T : CompoundButton>(
    textWireframeMapper: TextWireframeMapper,
    stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierResolver = UniqueIdentifierResolver,
    viewUtils: ViewUtils = ViewUtils()
) : CheckableTextViewMapper<T>(
    textWireframeMapper,
    stringUtils,
    uniqueIdentifierGenerator,
    viewUtils
) {

    // region CheckableTextViewMapper

    override fun resolveCheckableBounds(view: T, pixelsDensity: Float): GlobalBounds {
        val viewGlobalBounds = resolveViewGlobalBounds(view, pixelsDensity)
        var checkBoxHeight = DEFAULT_CHECKABLE_HEIGHT_IN_PX
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            view.buttonDrawable?.let {
                checkBoxHeight = it.intrinsicHeight.toLong()
            }
        }
        // minus the padding
        checkBoxHeight -= MIN_PADDING_IN_PX * 2
        val textSize = view.textSize.toLong()
        // to solve the current font issues on the player side we lower the original font
        // size with 1 unit. We will need to normalize the current checkbox size
        // to this new size
        checkBoxHeight = (checkBoxHeight * (textSize - 1) / textSize)
            .densityNormalized(pixelsDensity)
        return GlobalBounds(
            x = viewGlobalBounds.x + MIN_PADDING_IN_PX.densityNormalized(pixelsDensity),
            y = viewGlobalBounds.y + (viewGlobalBounds.height - checkBoxHeight) / 2,
            width = checkBoxHeight,
            height = checkBoxHeight
        )
    }

    // endregion

    companion object {
        internal const val MIN_PADDING_IN_PX = 20L
        internal const val DEFAULT_CHECKABLE_HEIGHT_IN_PX = 84L
    }
}
