/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.ui.profiling

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.datadog.benchmark.sample.ui.profiling.workload.AllocationWorkload
import com.datadog.benchmark.sample.ui.profiling.workload.CpuBurstWorkload
import com.datadog.benchmark.sample.ui.profiling.workload.DispatchWorkload
import com.datadog.benchmark.sample.ui.profiling.workload.IoWorkload
import com.datadog.benchmark.sample.ui.profiling.workload.Workload
import com.datadog.benchmark.sample.ui.profiling.workload.WorkloadController
import com.datadog.sample.benchmark.R
import com.datadog.sample.benchmark.databinding.FragmentProfilingBinding
import com.google.android.material.button.MaterialButton
import java.io.File

internal class ProfilingScenarioFragment : Fragment() {

    private var _binding: FragmentProfilingBinding? = null
    private val binding get() = _binding!!

    private var controller: WorkloadController? = null
    private var activeWorkload: WorkloadBinding? = null

    private class WorkloadBinding(
        val button: (FragmentProfilingBinding) -> MaterialButton,
        val displayName: String,
        val factory: (File) -> Workload
    )

    private val workloads = listOf(
        WorkloadBinding({ it.btnWorkloadCpu }, "CPU Burst") { CpuBurstWorkload() },
        WorkloadBinding({ it.btnWorkloadAllocation }, "Allocation") { AllocationWorkload() },
        WorkloadBinding({ it.btnWorkloadIo }, "IO") { cacheDir -> IoWorkload(cacheDir) },
        WorkloadBinding({ it.btnWorkloadDispatch }, "Dispatch") { DispatchWorkload() }
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfilingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        controller = WorkloadController()
        workloads.forEach { workload ->
            workload.button(binding).setOnClickListener {
                onButtonTapped(workload)
            }
        }
        renderButtonStates()
    }

    override fun onPause() {
        stopActiveWorkload()
        super.onPause()
    }

    override fun onDestroyView() {
        stopActiveWorkload()
        controller = null
        _binding = null
        super.onDestroyView()
    }

    private fun onButtonTapped(workload: WorkloadBinding) {
        val ctrl = controller ?: return
        when (activeWorkload) {
            null -> {
                ctrl.start(workload.factory(requireContext().cacheDir))
                activeWorkload = workload
            }
            workload -> {
                ctrl.stop()
                activeWorkload = null
            }
        }
        renderButtonStates()
    }

    private fun stopActiveWorkload() {
        if (activeWorkload != null) {
            controller?.stop()
            activeWorkload = null
            renderButtonStates()
        }
    }

    private fun renderButtonStates() {
        val currentBinding = _binding ?: return
        val active = activeWorkload
        workloads.forEach { workload ->
            val button = workload.button(currentBinding)
            val isActive = (active == workload)
            button.text = if (isActive) {
                getString(R.string.profiling_btn_workload_stop, workload.displayName)
            } else {
                getString(R.string.profiling_btn_workload_start, workload.displayName)
            }
            button.isEnabled = (active == null) || isActive
        }
    }
}
