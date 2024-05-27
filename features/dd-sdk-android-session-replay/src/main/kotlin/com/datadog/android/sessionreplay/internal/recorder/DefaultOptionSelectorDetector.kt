/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.recorder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatSpinner
import com.datadog.android.sessionreplay.recorder.OptionSelectorDetector

internal class DefaultOptionSelectorDetector : OptionSelectorDetector {
    override fun isOptionSelector(view: ViewGroup): Boolean {
        val viewClassName = view.javaClass.canonicalName ?: ""

        @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
        val isAppCompatSpinner = AppCompatSpinner::class.java.isAssignableFrom(view::class.java)
        return viewClassName in OPTION_SELECTORS_CLASS_NAMES_SET ||
            isAppCompatSpinner
    }

    companion object {
        private const val DROP_DOWN_LIST_CLASS_NAME = "android.widget.DropDownListView"
        private const val APPCOMPAT_DROP_DOWN_LIST_CLASS_NAME =
            "androidx.appcompat.widget.DropDownListView"
        private const val MATERIAL_TIME_PICKER_CLASS_NAME =
            "com.google.android.material.timepicker.TimePickerView"
        private const val MATERIAL_CALENDAR_GRID_CLASS_NAME =
            "com.google.android.material.datepicker.MaterialCalendarGridView"
        private val OPTION_SELECTORS_CLASS_NAMES_SET = setOf(
            DROP_DOWN_LIST_CLASS_NAME,
            APPCOMPAT_DROP_DOWN_LIST_CLASS_NAME,
            MATERIAL_TIME_PICKER_CLASS_NAME,
            MATERIAL_CALENDAR_GRID_CLASS_NAME
        )
    }
}
