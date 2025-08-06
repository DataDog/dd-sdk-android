/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.profiling

import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import com.datadog.android.rum.profiling.Profiler
import com.datadog.android.rum.profiling.dumpTrace
import com.datadog.android.sample.R
import com.datadog.android.sample.data.db.room.RoomDataSource
import java.io.File
import java.net.URL
import java.net.URLConnection
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@Suppress("DEPRECATION")
internal class ProfilingFragment :
    Fragment(),
    View.OnClickListener {

    private var interactionsCount = 0

    private lateinit var levelSpinner: Spinner

    private val uiHandler = Handler(Looper.getMainLooper())

    private val roomDataSource: RoomDataSource by lazy {
        RoomDataSource(requireContext())
    }

    // region Fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_profiling, container, false)
        levelSpinner = rootView.findViewById(R.id.level_spinner)
        rootView.findViewById<View>(R.id.send_log).setOnClickListener(this)

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.profiling_types,
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
    }

    // endregion

    // region View.OnClickListener

    override fun onClick(v: View) {
        interactionsCount++
        val profilingType = levelSpinner.selectedItemPosition
        when (profilingType) {
            0 -> startSimplePerfScenario()
            1 -> startLocalJvmScenario()
            2 -> startDebugProfilingScenario()
            3 -> startRandomHeavyOperations()
            else -> startNoTracingScenario()
        }
    }

    //endregion

    private fun startRandomHeavyOperations() {
        Profiler.startProfilingManagerProfiling(requireContext(), 2)
        // let's start a heavy file write operation
        val outputFile = File("${requireContext().getExternalFilesDir(null)}/heavy_operations.txt")
        if (!outputFile.exists()) {
            outputFile.createNewFile()
        }
        // let's write some big data to the file
        val data = StringBuilder()
        for (i in 0 until 100000) {
            data.append("This is a heavy operation line number $i\n")
        }
        outputFile.writeText(data.toString())
        outputFile.delete()
        // let's do some CPU work
        CpuWorkUtils().doCpuWork()
        // let's sleep for a while to simulate a heavy operation
        try {
            Thread.sleep(5000) // sleep for 5 seconds
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        // let's perform an url connection to simulate a network operation and wait for it to finish
        val url = URL("https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_month.geojson")
        val connection: URLConnection = url.openConnection()
        connection.connectTimeout = 5000 // set timeout to 5 seconds
        connection.readTimeout = 5000 // set read timeout to 5 seconds
        try {
            connection.connect()
            val inputStream = connection.getInputStream()
            inputStream.bufferedReader().use { reader ->
                val stringBuilder = StringBuilder()
                while (reader.readLine() != null) {
                    stringBuilder.append(reader.readLine())
                }
            }
        } catch (e: Exception) {
            Log.e("ProfilingFragment", "Error during network operation", e)
        } catch (e: Exception) {
            Log.e("ProfilingFragment", "Error during network operation", e)
        }
        Profiler.stopProfilingManagerProfiling()
    }


    private fun startSimplePerfScenario() {
        Profiler.startSimpleperfProfiling(
            requireContext(),
            8
        )
        val outputMetricsFilePath = "${requireContext().getExternalFilesDir(null)}/simpleperf"
        uiHandler.postDelayed(SimplePerfProfileCpuWorkRunnable(outputMetricsFilePath, uiHandler), 8)
    }

    private fun startLocalJvmScenario() {
        val frequency = 1000 // 500 Hz
        Profiler.startProfiling(
            requireContext(),
            1000L / frequency
        )
        val outputMetricsFilePath =
            "${requireContext().getExternalFilesDir(null)}/localprofiler_${frequency}hz"
        uiHandler.postDelayed(LocalProfileCpuWorkRunnable(outputMetricsFilePath, uiHandler), 8)
    }

    private fun startDebugProfilingScenario() {
        val interval = TimeUnit.MILLISECONDS.toMicros(2).toInt()
        val traceFilePath = "${requireContext().getExternalFilesDir(null)}/debugprofiling_trace"
        val traceFile = File(traceFilePath)

        /* Debug.startMethodTracingSampling(
             traceFilePath,
             20 * 1024 * 1024,
             interval
         )
         Log.i("ProfilingFragment", "Starting debug profiling with interval: $interval")
         val outputMetricsFilePath =
             "${requireContext().getExternalFilesDir(null)}/debugprofiling"
         uiHandler.postDelayed(
             DebugProfileCpuWorkRunnable(
                 traceFilePath,
                 outputMetricsFilePath,
                 uiHandler
             ), 8
         )*/
        dumpTrace(requireContext(), traceFilePath + ".trace")
    }

    private fun startNoTracingScenario() {
        val outputMetricsFilePath = "${requireContext().getExternalFilesDir(null)}/noprofiling"
        uiHandler.postDelayed(NoTracingCpuWorkRunnable(outputMetricsFilePath, uiHandler), 8)
    }

    class SimplePerfProfileCpuWorkRunnable(outputMetricsFilePath: String, uiHandler: Handler) :
        BaseCpuWorkRunnable(outputMetricsFilePath, uiHandler) {
        override fun stopProfiling() {
            Profiler.stopSimpleperfProfiling()
        }
    }

    class NoTracingCpuWorkRunnable(outputMetricsFilePath: String, uiHandler: Handler) :
        BaseCpuWorkRunnable(outputMetricsFilePath, uiHandler) {
        override fun stopProfiling() {
            // No-op
        }
    }

    abstract class BaseCpuWorkRunnable(val outputMetricsFilePath: String, val uiHandler: Handler) : Runnable {
        private var executionCount = 0
        private val metrics = LinkedList<Long>()

        override fun run() {
            if (executionCount >= TEST_COUNT) {
                stopProfiling()
                val outputFile = File(outputMetricsFilePath)
                dumpMetrics(outputFile)
                uiHandler.removeCallbacks(this)
                return
            }
            executeCpuWork()
            executionCount++
            uiHandler.postDelayed(this, 8)
        }

        protected fun dumpMetrics(outputFile: File) {
            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }
            val metricsAsString = metrics.joinToString(",")
            outputFile.writeText(metricsAsString)
            metrics.clear()
            Log.i(
                "ProfilingFragment",
                "Metrics[${metrics.size}] dumped to ${outputFile.absolutePath}"
            )
        }

        protected fun executeCpuWork() {
            measureNanoTime {
                CpuWorkUtils().doCpuWork()
            }.let {
                metrics.add(it)
            }
        }

        abstract fun stopProfiling()

    }

    class LocalProfileCpuWorkRunnable(outputMetricsFilePath: String, uiHandler: Handler) :
        BaseCpuWorkRunnable(outputMetricsFilePath, uiHandler) {
        override fun stopProfiling() {
            Profiler.stopProfiling()
        }
    }

    class DebugProfileCpuWorkRunnable(
        private val traceFilePath: String,
        outputMetricsFilePath: String, uiHandler: Handler
    ) :
        BaseCpuWorkRunnable(outputMetricsFilePath, uiHandler) {
        override fun stopProfiling() {
            Log.i("ProfilingFragment", "Stopping debug profiling")
            Debug.stopMethodTracing()
        }
    }


    companion object {
        private val TEST_EXECUTION_TIME_IN_MS = TimeUnit.SECONDS.toNanos(10)
        private val TEST_COUNT = 300
    }
}
