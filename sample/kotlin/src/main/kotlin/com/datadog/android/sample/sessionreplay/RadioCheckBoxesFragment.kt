/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.sessionreplay

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.fragment.app.Fragment
import com.datadog.android.sample.R

internal class RadioCheckBoxesFragment : Fragment(R.layout.fragment_radio_checkbox_components) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val defaultDisabledChecked = view.findViewById<CheckBox>(R.id.checkbox_disabled_checked)
        val defaultDisabledNotChecked = view.findViewById<CheckBox>(R.id.checkbox_disabled_not_checked)
        view.findViewById<CheckBox>(R.id.default_checkbox).apply {
            setOnCheckedChangeListener { _, isChecked ->
                defaultDisabledChecked.isEnabled = isChecked
                defaultDisabledNotChecked.isEnabled = isChecked
            }
        }
        val appCompatDisabledChecked = view.findViewById<CheckBox>(R.id.app_compat_checkbox_disabled_checked)
        val appCompatDisabledNotChecked = view.findViewById<CheckBox>(R.id.app_compat_checkbox_disabled_unchecked)
        view.findViewById<CheckBox>(R.id.app_compat_checkbox)
            .apply {
                setOnCheckedChangeListener { _, isChecked ->
                    appCompatDisabledChecked.isEnabled = isChecked
                    appCompatDisabledNotChecked.isEnabled = isChecked
                }
            }

        val materialDisabledChecked = view.findViewById<CheckBox>(R.id.material_checkbox_disabled_checked)
        val materialDisabledNotChecked = view.findViewById<CheckBox>(R.id.material_checkbox_disabled_not_checked)
        view.findViewById<CheckBox>(R.id.material_checkbox)
            .apply {
                setOnCheckedChangeListener { _, isChecked ->
                    materialDisabledChecked.isEnabled = isChecked
                    materialDisabledNotChecked.isEnabled = isChecked
                }
            }
    }
}
