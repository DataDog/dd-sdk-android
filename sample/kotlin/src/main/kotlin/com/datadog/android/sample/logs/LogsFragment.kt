/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.logs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.R
import com.datadog.android.sample.SynchronousLoadedFragment

@Suppress("DEPRECATION")
internal class LogsFragment :
    SynchronousLoadedFragment(),
    View.OnClickListener {

    private var interactionsCount = 0
    private lateinit var viewModel: LogsViewModel

    private lateinit var messageInput: EditText
    private lateinit var levelSpinner: Spinner

    @Suppress("CheckInternal")
    private val logger: Logger by lazy {
        Logger.Builder()
            .setName("logs_fragment")
            .setLogcatLogsEnabled(true)
            .build()
            .apply {
                addTag("flavor", BuildConfig.FLAVOR)
                addTag("build_type", BuildConfig.BUILD_TYPE)
            }
    }

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_logs, container, false)
        messageInput = rootView.findViewById(R.id.message)
        levelSpinner = rootView.findViewById(R.id.level_spinner)
        rootView.findViewById<View>(R.id.send_log).setOnClickListener(this)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.log_levels,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            levelSpinner.adapter = adapter
        }

        return rootView
    }

    @Deprecated("Deprecated in parent Java class")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(LogsViewModel::class.java)
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        interactionsCount++
        val message = messageInput.text.toString()
        val level = levelSpinner.selectedItemPosition + Log.VERBOSE
        val exception = if (level >= Log.ERROR) {
            UnsupportedOperationException("Oops")
        } else {
            null
        }
        logger.log(level, message, exception)
    }

    //endregion
}
