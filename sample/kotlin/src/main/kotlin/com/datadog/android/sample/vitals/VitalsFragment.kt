/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.vitals

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.android.sample.R
import com.google.android.material.textfield.TextInputEditText

internal class VitalsFragment :
    Fragment(),
    View.OnClickListener,
    CompoundButton.OnCheckedChangeListener {

    private lateinit var viewModel: VitalsViewModel
    private lateinit var progressView: ProgressBar
    private lateinit var badView: BadView
    private lateinit var operationName: TextInputEditText
    private lateinit var operationKey: TextInputEditText

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_vitals, container, false)
        rootView.findViewById<View>(R.id.vital_long_task).setOnClickListener(this)
        rootView.findViewById<View>(R.id.vital_frozen_frame).setOnClickListener(this)

        rootView.findViewById<CheckBox>(R.id.vital_cpu).setOnCheckedChangeListener(this)
        rootView.findViewById<CheckBox>(R.id.vital_slow_frame_rate).setOnCheckedChangeListener(this)
        rootView.findViewById<CheckBox>(R.id.vital_memory).setOnCheckedChangeListener(this)
        rootView.findViewById<CheckBox>(R.id.vital_stress_test).setOnCheckedChangeListener(this)
        rootView.findViewById<Button>(R.id.fo_start).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.fo_stop_successfully).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.fo_stop_unsuccessfully).setOnClickListener(this)

        badView = rootView.findViewById(R.id.vital_slow_view)
        progressView = rootView.findViewById(R.id.progress)

        operationName = rootView.findViewById(R.id.fo_name_edit_text)
        operationKey = rootView.findViewById(R.id.fo_operation_key_edit_text)
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(VitalsViewModel::class.java)
    }

    override fun onResume() {
        super.onResume()
        viewModel.resume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        val operationKey = operationKey.text.toString().ifEmpty { null }
        when (v.id) {
            R.id.vital_long_task -> viewModel.runLongTask()
            R.id.vital_frozen_frame -> viewModel.runFrozenFrame()
            R.id.fo_start -> viewModel.startFeatureOperation(
                operationName.text.toString(),
                operationKey
            )

            R.id.fo_stop_successfully -> viewModel.stopFeatureOperation(
                operationName.text.toString(),
                operationKey
            )

            R.id.fo_stop_unsuccessfully -> viewModel.stopFeatureOperation(
                operationName.text.toString(),
                operationKey,
                FailureReason.ERROR
            )
        }
    }

    // endregion

    // region CompoundButton.OnCheckedChangeListener

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        when (buttonView.id) {
            R.id.vital_cpu -> {
                viewModel.toggleHeavyComputation(isChecked)
            }

            R.id.vital_slow_frame_rate -> {
                badView.setSlow(isChecked)
                viewModel.toggleForegroundTasks(isChecked)
            }

            R.id.vital_memory -> {
                viewModel.toggleMemory(isChecked)
            }

            R.id.vital_stress_test -> {
                viewModel.toggleStressTest(isChecked)
            }
        }
    }

    //endregion
}
