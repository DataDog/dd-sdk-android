/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.logs

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.widget.AppCompatSpinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.datadog.android.log.Logger
import com.datadog.android.sample.BuildConfig
import com.datadog.android.sample.R
import com.datadog.android.sample.service.LogsForegroundService

class LogsFragment :
    Fragment(),
    View.OnClickListener {

    private var interactionsCount = 0
    private lateinit var viewModel: LogsViewModel
    private lateinit var spinner: AppCompatSpinner

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
        rootView.findViewById<View>(R.id.start_foreground_service).setOnClickListener(this)
        rootView.findViewById<View>(R.id.simulate_ndk_crash).setOnClickListener(this)
        rootView.findViewById<View>(R.id.simulate_anr).setOnClickListener(this)
        spinner = rootView.findViewById(R.id.signal_type_spinner)
        context?.let { context ->
            spinner.adapter =
                ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_item,
                    listOf(
                        NdkCrashType(SIGSEGV, "Invalid Memory"),
                        NdkCrashType(SIGABRT, "Abort Program"),
                        NdkCrashType(SIGILL, "Illegal Instruction")
                    )
                ).also {
                    it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
        }
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
            R.id.log_warning -> logger.w("User triggered a warning")
            R.id.log_error -> logger.e(
                "User triggered an error",
                IllegalStateException("Oops")
            )
            R.id.log_critical -> logger.wtf(
                "User triggered a critical event",
                UnsupportedOperationException("Oops")
            )
            R.id.start_foreground_service -> {
                val serviceIntent = Intent(context, LogsForegroundService::class.java)
                activity?.startService(serviceIntent)
            }
            R.id.simulate_ndk_crash -> {
                val signal = (spinner.selectedItem as? NdkCrashType)?.signal ?: SIGILL
                simulateNdkCrash(signal)
            }
            R.id.simulate_anr -> {
               simulateAnr()
            }
        }
    }

    //endregion

    // region Internal

    fun simulateAnr() {
        val main = Handler(Looper.getMainLooper())
        main.postDelayed(Runnable {
            Thread.sleep(100000)
        }, 1)
    }

    // endregion

    // region NDK

    external fun simulateNdkCrash(signal: Int)

    // endregion

    private data class NdkCrashType(val signal: Int, val label: String) {
        override fun toString(): String {
            return label
        }
    }

    companion object {

        // NDK Crash signals
        const val SIGILL = 4 // "Illegal instruction
        const val SIGABRT = 6 // "Abort program"
        const val SIGSEGV = 11 // "Segmentation violation (invalid memory reference)"
        const val SIGQUIT = 3  // Application Non Responsive (ANR)

        fun newInstance(): LogsFragment {
            return LogsFragment()
        }
    }
}
