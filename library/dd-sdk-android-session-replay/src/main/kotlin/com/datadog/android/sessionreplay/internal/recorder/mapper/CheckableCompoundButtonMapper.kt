/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.CompoundButton
import com.datadog.android.sessionreplay.internal.recorder.GlobalBounds
import com.datadog.android.sessionreplay.internal.recorder.densityNormalized
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal abstract class CheckableCompoundButtonMapper<T : CompoundButton>(
    textWireframeMapper: TextViewMapper,
    stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils
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
        checkBoxHeight = checkBoxHeight.densityNormalized(pixelsDensity)
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
