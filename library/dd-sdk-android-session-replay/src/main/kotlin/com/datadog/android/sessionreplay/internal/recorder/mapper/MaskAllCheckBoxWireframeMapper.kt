/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.widget.CheckBox
import com.datadog.android.sessionreplay.internal.recorder.ViewUtils
import com.datadog.android.sessionreplay.internal.utils.StringUtils
import com.datadog.android.sessionreplay.model.MobileSegment

internal class MaskAllCheckBoxWireframeMapper(
    textWireframeMapper: TextWireframeMapper,
    stringUtils: StringUtils = StringUtils,
    uniqueIdentifierGenerator: UniqueIdentifierResolver =
        UniqueIdentifierResolver,
    viewUtils: ViewUtils = ViewUtils()
) : CheckBoxWireframeMapper(textWireframeMapper, stringUtils, uniqueIdentifierGenerator, viewUtils) {

    override fun resolveShapeStyle(view: CheckBox, checkBoxColor: String):
        MobileSegment.ShapeStyle? {
        return null
    }
}
