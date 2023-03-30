/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder.mapper

import android.os.Build
import android.widget.NumberPicker
import androidx.annotation.RequiresApi
import com.datadog.android.sessionreplay.utils.StringUtils
import com.datadog.android.sessionreplay.utils.UniqueIdentifierGenerator
import com.datadog.android.sessionreplay.utils.ViewUtils

@RequiresApi(Build.VERSION_CODES.Q)
internal open class MaskAllNumberPickerMapper(
    stringUtils: StringUtils = StringUtils,
    viewUtils: ViewUtils = ViewUtils,
    uniqueIdentifierGenerator: UniqueIdentifierGenerator = UniqueIdentifierGenerator
) : NumberPickerMapper(stringUtils, viewUtils, uniqueIdentifierGenerator) {

    override fun resolveLabelValue(numberPicker: NumberPicker, index: Int): String {
        return DEFAULT_MASKED_TEXT_VALUE
    }

    companion object {
        internal const val DEFAULT_MASKED_TEXT_VALUE = "xxx"
    }
}
