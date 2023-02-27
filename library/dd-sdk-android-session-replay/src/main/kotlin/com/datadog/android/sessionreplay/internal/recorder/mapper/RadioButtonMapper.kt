/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.RadioButton
import com.datadog.android.sessionreplay.internal.recorder.ViewUtils
import com.datadog.android.sessionreplay.internal.utils.StringUtils
import com.datadog.android.sessionreplay.model.MobileSegment

internal open class RadioButtonMapper(
    textWireframeMapper: TextWireframeMapper,
    stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierResolver =
        UniqueIdentifierResolver,
    viewUtils: ViewUtils = ViewUtils()
) : CompoundButtonMapper<RadioButton>(
    textWireframeMapper,
    stringUtils,
    uniqueIdentifierGenerator,
    viewUtils
) {

    override fun resolveCheckableShapeStyle(view: RadioButton, checkBoxColor: String):
        MobileSegment.ShapeStyle? {
        return if (view.isChecked) {
            MobileSegment.ShapeStyle(
                backgroundColor = checkBoxColor,
                view.alpha,
                cornerRadius = CORNER_RADIUS
            )
        } else {
            MobileSegment.ShapeStyle(
                backgroundColor = null,
                view.alpha,
                cornerRadius = CORNER_RADIUS
            )
        }
    }

    companion object {
        internal const val CORNER_RADIUS = 10
    }
}
