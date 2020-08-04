/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.crash

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
import com.datadog.android.sample.R

class CrashFragment :
    Fragment(),
    View.OnClickListener {

    private lateinit var viewModel: CrashViewModel
    private lateinit var spinner: AppCompatSpinner
    private val mainThreadHandler = Handler(Looper.getMainLooper())

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val currentContext = context ?: return null

        val rootView = inflater.inflate(R.layout.fragment_crash, container, false)

        rootView.findViewById<View>(R.id.action_java_crash).setOnClickListener(this)
        rootView.findViewById<View>(R.id.action_ndk_crash).setOnClickListener(this)
        rootView.findViewById<View>(R.id.action_anr).setOnClickListener(this)

        spinner = rootView.findViewById(R.id.signal_type_spinner)
        val arrayAdapter = ArrayAdapter(
            currentContext,
            android.R.layout.simple_spinner_item,
            NATIVE_SIGNALS
        )
        arrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = arrayAdapter

        return rootView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(CrashViewModel::class.java)
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        when (v.id) {
            R.id.action_java_crash -> triggerCrash()
            R.id.action_ndk_crash -> triggerNdkCrash()
            R.id.action_anr -> triggerANR()
        }
    }

    //endregion

    // region Internal

    private fun triggerCrash(): Int {
        val className = javaClass.simpleName
        val i = className.length - className.toUpperCase().length
        return className.length / i
    }

    private fun triggerNdkCrash() {
        val signal = (spinner.selectedItem as? NativeSignal)?.signal ?: SIGILL
        simulateNdkCrash(signal)
    }

    private fun triggerANR() {
        mainThreadHandler.postDelayed({ Thread.sleep(100000) }, 1)
    }

    // endregion

    // region Native

    private external fun simulateNdkCrash(signal: Int)

    // endregion

    private data class NativeSignal(val signal: Int, val label: String) {
        override fun toString(): String {
            return label
        }
    }

    companion object {

        const val SIGILL = 4 // "Illegal instruction
        const val SIGABRT = 6 // "Abort program"
        const val SIGSEGV = 11 // "Segmentation violation (invalid memory reference)"

        private val NATIVE_SIGNALS = listOf(
            NativeSignal(SIGSEGV, "Invalid Memory"),
            NativeSignal(SIGABRT, "Abort Program"),
            NativeSignal(SIGILL, "Illegal Instruction")
        )
    }
}
