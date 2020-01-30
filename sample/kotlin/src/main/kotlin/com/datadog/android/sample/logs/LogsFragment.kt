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
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.MainActivity
import com.datadog.android.sample.R
import io.opentracing.Scope
import io.opentracing.Span
import io.opentracing.util.GlobalTracer

class LogsFragment :
    Fragment(),
    View.OnClickListener {

    private var interactionsCount = 0
    private lateinit var viewModel: LogsViewModel

    lateinit var mainScope: Scope
    lateinit var mainSpan: Span

    private val logger: Logger by lazy {
        Logger.Builder()
            .setServiceName("android-sample-kotlin")
            .setLoggerName("logs_fragment")
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
        rootView.findViewById<View>(R.id.log_warning).setOnClickListener(this)
        rootView.findViewById<View>(R.id.log_error).setOnClickListener(this)
        rootView.findViewById<View>(R.id.log_critical).setOnClickListener(this)
        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(LogsViewModel::class.java)
    }

    override fun onResume() {
        val tracer = GlobalTracer.get()
        val mainActivitySpan = (activity as MainActivity).mainSpan
        mainSpan = tracer
            .buildSpan("LogsFragment").asChildOf(mainActivitySpan).start()
        mainScope = tracer.activateSpan(mainSpan)
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        mainScope.close()
        mainSpan.finish()
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        interactionsCount++
        when (v.id) {
            R.id.log_warning -> logger.w("User triggered a warning")
            R.id.log_error -> logger.e(
                "User triggered an error",
                IllegalStateException("Oops")
            )
            R.id.log_critical -> logger.wtf(
                "User triggered a critical event",
                UnsupportedOperationException("Oops")
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
