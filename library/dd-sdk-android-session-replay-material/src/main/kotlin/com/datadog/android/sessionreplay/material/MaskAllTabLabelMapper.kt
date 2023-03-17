/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.material

import android.widget.TextView
import com.datadog.android.sessionreplay.internal.recorder.SystemInformation
import com.datadog.android.sessionreplay.internal.recorder.mapper.MaskAllTextViewMapper
import com.datadog.android.sessionreplay.internal.recorder.mapper.WireframeMapper
import com.datadog.android.sessionreplay.model.MobileSegment
import com.datadog.android.sessionreplay.utils.StringUtils

internal class MaskAllTabLabelMapper(
    private val maskAllTextViewMapper: MaskAllTextViewMapper = MaskAllTextViewMapper(),
    private val stringUtils: StringUtils = StringUtils
) :
    WireframeMapper<TextView, MobileSegment.Wireframe.TextWireframe> by maskAllTextViewMapper {

    override fun map(view: TextView, systemInformation: SystemInformation):
        List<MobileSegment.Wireframe.TextWireframe> {
        val maskedTextColor =
            stringUtils.formatColorAndAlphaAsHexa(view.textColors.defaultColor, OPAQUE_ALPHA)
        return maskAllTextViewMapper.map(view, systemInformation).map {
            val currentTextStyle = it.textStyle
            it.copy(textStyle = currentTextStyle.copy(color = maskedTextColor))
        }
    }

    companion object {
        internal const val OPAQUE_ALPHA = 255
    }
}
