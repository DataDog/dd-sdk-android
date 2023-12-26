/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.RadioButton
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

internal class MaskRadioButtonMapper(
    textWireframeMapper: TextViewMapper,
    stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator,
    viewUtils: ViewUtils = ViewUtils
) : RadioButtonMapper(
    textWireframeMapper,
    stringUtils,
    uniqueIdentifierGenerator,
    viewUtils
) {

    override fun resolveCheckedShapeStyle(view: RadioButton, checkBoxColor: String): MobileSegment.ShapeStyle {
        return MobileSegment.ShapeStyle(
            backgroundColor = null,
            view.alpha,
            cornerRadius = CORNER_RADIUS
        )
    }
}
