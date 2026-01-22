/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.profiling

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.datadog.android.profiling.Profiling
import com.datadog.android.sample.R

@Suppress("MagicNumber")
internal class ProfilingFragment : Fragment() {

    private lateinit var button1s: Button
    private lateinit var button10s: Button
    private lateinit var button60s: Button

    private lateinit var progressBar1s: ProgressBar
    private lateinit var progressBar10s: ProgressBar
    private lateinit var progressBar60s: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var currentProfilingRunnable: Runnable? = null

    private val heavyCPUWork = HeavyCPUWork()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profiling, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        button1s = view.findViewById(R.id.button_profile_1s)
        button10s = view.findViewById(R.id.button_profile_10s)
        button60s = view.findViewById(R.id.button_profile_60s)

        progressBar1s = view.findViewById(R.id.progress_bar_1s)
        progressBar10s = view.findViewById(R.id.progress_bar_10s)
        progressBar60s = view.findViewById(R.id.progress_bar_60s)

        // Set click listeners
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            button1s.setOnClickListener {
                startProfiling(requireContext(), duration = 1, progressBar = progressBar1s)
            }
            button10s.setOnClickListener {
                startProfiling(requireContext(), duration = 10, progressBar = progressBar10s)
            }
            button60s.setOnClickListener {
                startProfiling(requireContext(), duration = 60, progressBar = progressBar60s)
            }
        } else {
            Toast.makeText(
                requireContext(),
                "Profiling is only working for API 35 and above",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun startProfiling(context: Context, duration: Int, progressBar: ProgressBar) {
        // Cancel any ongoing profiling
        currentProfilingRunnable?.let { handler.removeCallbacks(it) }

        // Reset all progress bars
        resetAllProgressBars()

        // Disable all buttons during profiling
        setAllButtonsEnabled(false)

        // Start heavy CPU work for profiling
        heavyCPUWork.start()

        Profiling.start(context)

        // Track progress
        val startTime = System.currentTimeMillis()
        val durationMs = duration * 1000L
        val updateInterval = 50L // Update every 50ms for smooth animation

        val profilingRunnable = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = ((elapsed.toFloat() / durationMs) * 100).toInt().coerceIn(0, 100)

                progressBar.progress = progress

                if (elapsed < durationMs) {
                    // Continue updating progress
                    handler.postDelayed(this, updateInterval)
                } else {
                    // Profiling complete
                    progressBar.progress = 100

                    // Stop heavy CPU work
                    heavyCPUWork.stop()

                    Profiling.stop()

                    // Re-enable all buttons
                    setAllButtonsEnabled(true)

                    handler.post {
                        progressBar.progress = 0
                    }

                    currentProfilingRunnable = null
                }
            }
        }

        currentProfilingRunnable = profilingRunnable
        handler.post(profilingRunnable)
    }

    private fun resetAllProgressBars() {
        progressBar1s.progress = 0
        progressBar10s.progress = 0
        progressBar60s.progress = 0
    }

    private fun setAllButtonsEnabled(enabled: Boolean) {
        button1s.isEnabled = enabled
        button10s.isEnabled = enabled
        button60s.isEnabled = enabled
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Clean up any pending callbacks
        currentProfilingRunnable?.let { handler.removeCallbacks(it) }
        // Stop heavy CPU work if still running
        heavyCPUWork.stop()
    }
}
