/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import com.datadog.android.sample.R
import com.datadog.android.sample.SynchronousLoadedFragment

internal class SlidersSteppersFragment : SynchronousLoadedFragment() {

    @Suppress("MagicNumber")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_sliders_steppers_components, container, false)
        val numberPickerWithCustomValues: NumberPicker =
            root.findViewById(R.id.number_picker_with_custom_values)
        val pickerWithDisplayedValuesMinIndex = 20
        val pickerWithDisplayedValuesMaxIndex = 24
        numberPickerWithCustomValues.minValue = pickerWithDisplayedValuesMinIndex
        numberPickerWithCustomValues.maxValue = pickerWithDisplayedValuesMaxIndex
        numberPickerWithCustomValues.displayedValues =
            arrayOf("january", "february", "march", "june", "july")
        val defaultNumberPicker: NumberPicker =
            root.findViewById(R.id.default_number_picker)
        val defaultPickerMinIndex = 0
        val defaultPickerMaxIndex = 10
        defaultNumberPicker.minValue = defaultPickerMinIndex
        defaultNumberPicker.maxValue = defaultPickerMaxIndex
        return root
    }
}
