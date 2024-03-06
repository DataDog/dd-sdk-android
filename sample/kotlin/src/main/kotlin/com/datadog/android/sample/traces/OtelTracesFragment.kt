/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.traces

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication

internal class OtelTracesFragment : Fragment(), View.OnClickListener {

    lateinit var viewModel: OtelTracesViewModel
    lateinit var progressBarAsync: ProgressBar

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_otel_traces, container, false)
        rootView.findViewById<Button>(R.id.start_async_operation).setOnClickListener(this)
        progressBarAsync = rootView.findViewById(R.id.spinner_async)
        return rootView
    }

    @Suppress("UnsafeCallOnNullableType") // not an issue in the sample
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val factory = SampleApplication.getViewModelFactory(requireContext())
        viewModel = ViewModelProviders.of(this, factory).get(OtelTracesViewModel::class.java)
    }

    override fun onDetach() {
        super.onDetach()
        viewModel.stopAsyncOperations()
        progressBarAsync.visibility = View.INVISIBLE
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.start_async_operation -> {
                progressBarAsync.visibility = View.VISIBLE
                viewModel.startAsyncOperation(
                    onProgress = {
                        progressBarAsync.progress = it
                    },
                    onDone = {
                        progressBarAsync.visibility = View.INVISIBLE
                    }
                )
            }
        }
    }

    // endregion
}
