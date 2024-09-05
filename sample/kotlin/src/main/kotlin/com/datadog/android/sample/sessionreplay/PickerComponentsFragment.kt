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
import android.widget.EditText
import android.widget.ImageButton
import com.datadog.android.sample.R
import com.datadog.android.sample.SynchronousLoadedFragment
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import java.util.Locale

internal class PickerComponentsFragment : SynchronousLoadedFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_picker_view_components, container, false)
        val setTimeTextView = root.findViewById<EditText>(R.id.set_time_text_view)
        val setDateTextView = root.findViewById<EditText>(R.id.set_date_text_view)
        root.findViewById<ImageButton>(R.id.set_time_button).setOnClickListener {
            activity?.supportFragmentManager?.let {
                MaterialTimePicker.Builder().build().apply {
                    addOnPositiveButtonClickListener {
                        val hour = this.hour
                        val minute = this.minute
                        setTimeTextView.setText(String.format(Locale.US, "%s : %s", hour, minute))
                    }
                }.show(it, null)
            }
        }
        root.findViewById<ImageButton>(R.id.set_date_button).setOnClickListener {
            activity?.supportFragmentManager?.let {
                MaterialDatePicker.Builder.datePicker().build().apply {
                    addOnPositiveButtonClickListener {
                        setDateTextView.setText(this.headerText)
                    }
                }.show(it, null)
            }
        }
        return root
    }
}
