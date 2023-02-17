/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.input

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R
import com.google.android.material.timepicker.MaterialTimePicker
import java.util.Locale

internal class PickerComponentsFragment : Fragment() {

    private lateinit var setTimeTextView: EditText
    private val materialTimePickerDialog: MaterialTimePicker by lazy {
        MaterialTimePicker.Builder().build()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_picker_view_components, container, false)
        setTimeTextView = root.findViewById(R.id.set_time_text_view)
        root.findViewById<ImageButton>(R.id.set_time_button).setOnClickListener {
            activity?.supportFragmentManager?.let {
                materialTimePickerDialog.show(it, null)
            }
        }

        materialTimePickerDialog.addOnPositiveButtonClickListener {
            val hour = materialTimePickerDialog.hour
            val minute = materialTimePickerDialog.minute
            setTimeTextView.setText(String.format(Locale.ENGLISH, "%s : %s", hour, minute))
        }
        return root
    }
}
