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
import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R
import com.datadog.android.sample.SampleApplication
import com.datadog.android.sample.SynchronousLoadedFragment

internal class TracesFragment : SynchronousLoadedFragment(), View.OnClickListener {

    lateinit var viewModel: TracesViewModel
    lateinit var progressBarAsync: ProgressBar
    lateinit var progressBarCoroutine: ProgressBar
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
        rootView.findViewById<Button>(R.id.start_coroutine_operation).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.start_request).setOnClickListener(this)
        rootView.findViewById<Button>(R.id.start_404_request).setOnClickListener(this)
        progressBarAsync = rootView.findViewById(R.id.spinner_async)
        progressBarCoroutine = rootView.findViewById(R.id.spinner_coroutine)
        progressBarRequest = rootView.findViewById(R.id.spinner_request)
        requestStatus = rootView.findViewById(R.id.request_status)
        return rootView
    }

    @Suppress("UnsafeCallOnNullableType") // not an issue in the sample
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val factory = SampleApplication.getViewModelFactory(requireContext())
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
                    }
                )
            }
            R.id.start_coroutine_operation -> {
                progressBarCoroutine.visibility = View.VISIBLE
                progressBarCoroutine.isIndeterminate = true
                viewModel.startCoroutineOperation(
                    onDone = {
                        progressBarCoroutine.visibility = View.INVISIBLE
                    }
                )
            }
            R.id.start_request -> {
                setInProgress()
                viewModel.startRequest(
                    onResponse = {
                        setCompleteStatus(R.drawable.ic_check_circle_green_24dp)
                    },
                    onException = {
                        setCompleteStatus(R.drawable.ic_error_red_24dp)
                    },
                    onCancel = {
                        setCompleteStatus(R.drawable.ic_cancel_red_24dp)
                    }
                )
            }
            R.id.start_404_request -> {
                setInProgress()
                viewModel.start404Request(
                    onResponse = {
                        setCompleteStatus(R.drawable.ic_check_circle_green_24dp)
                    },
                    onException = {
                        setCompleteStatus(R.drawable.ic_error_red_24dp)
                    },
                    onCancel = {
                        setCompleteStatus(R.drawable.ic_cancel_red_24dp)
                    }
                )
            }
        }
    }

    private fun setCompleteStatus(@DrawableRes requestStatusDrawableId: Int) {
        requestStatus.setImageResource(requestStatusDrawableId)
        requestStatus.visibility = View.VISIBLE
        progressBarRequest.visibility = View.INVISIBLE
    }

    private fun setInProgress() {
        progressBarRequest.visibility = View.VISIBLE
        requestStatus.visibility = View.INVISIBLE
    }

    // endregion

    companion object {
        fun newInstance(): TracesFragment {
            return TracesFragment()
        }
    }
}
