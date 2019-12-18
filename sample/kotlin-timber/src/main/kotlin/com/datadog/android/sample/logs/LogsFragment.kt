/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample.logs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.sample.R
import timber.log.Timber

class LogsFragment :
    Fragment(),
    View.OnClickListener {

    private var interactionsCount = 0
    private lateinit var viewModel: LogsViewModel

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_logs, container, false)
        rootView.findViewById<View>(R.id.log_warning).setOnClickListener(this)
        rootView.findViewById<View>(R.id.log_error).setOnClickListener(this)
        rootView.findViewById<View>(R.id.log_critical).setOnClickListener(this)
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(LogsViewModel::class.java)
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        interactionsCount++
        when (v.id) {
            R.id.log_warning -> Timber.w("User triggered a warning")
            R.id.log_error -> Timber.e(
                IllegalStateException("Oops"),
                "User triggered an error"
            )
            R.id.log_critical -> Timber.wtf(
                UnsupportedOperationException("Oops"),
                "User triggered a critical event"
            )
        }
    }

    //endregion

    companion object {
        fun newInstance(): LogsFragment {
            return LogsFragment()
        }
    }
}
