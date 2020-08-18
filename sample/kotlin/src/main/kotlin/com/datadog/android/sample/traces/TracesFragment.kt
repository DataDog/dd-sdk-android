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
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication

class TracesFragment : Fragment(), View.OnClickListener {

    lateinit var viewModel: TracesViewModel
    lateinit var progressBarAsync: ProgressBar
    lateinit var progressBarRequest: ProgressBar
    lateinit var requestStatus: ImageView

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_traces, container, false)
        rootView.findViewById<Button>(R.id.start_async_operation).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.start_request).setOnClickListener(this)
        progressBarAsync = rootView.findViewById(R.id.spinner_async)
        progressBarRequest = rootView.findViewById(R.id.spinner_request)
        requestStatus = rootView.findViewById(R.id.request_status)
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val factory = SampleApplication.getViewModelFactory(context!!)
        viewModel = ViewModelProviders.of(this, factory).get(TracesViewModel::class.java)
    }

    override fun onDetach() {
        super.onDetach()
        viewModel.stopAsyncOperations()
        progressBarAsync.visibility = View.INVISIBLE
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
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
                    })
            }
            R.id.start_request -> {
                progressBarRequest.visibility = View.VISIBLE
                requestStatus.visibility = View.INVISIBLE
                viewModel.startRequest(
                    onResponse = {
                        requestStatus.setImageResource(R.drawable.ic_check_circle_green_24dp)
                        requestStatus.visibility = View.VISIBLE
                        progressBarRequest.visibility = View.INVISIBLE
                    },
                    onException = {
                        requestStatus.setImageResource(R.drawable.ic_error_red_24dp)
                        requestStatus.visibility = View.VISIBLE
                        progressBarRequest.visibility = View.INVISIBLE
                    },
                    onCancel = {
                        requestStatus.setImageResource(R.drawable.ic_cancel_red_24dp)
                        requestStatus.visibility = View.VISIBLE
                        progressBarRequest.visibility = View.INVISIBLE
                    })
            }
        }
    }

    // endregion

    companion object {
        fun newInstance(): TracesFragment {
            return TracesFragment()
        }
    }
}
