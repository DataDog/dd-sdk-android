/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.vitals

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import timber.log.Timber
import java.security.SecureRandom

@Suppress("MagicNumber", "TooManyFunctions")
internal class VitalsViewModel : ViewModel() {

    private val rng = SecureRandom()
    private val piComputer = PiDigitComputerBBP()
    private val handler = Handler(Looper.getMainLooper())
    private val bitmapList = mutableListOf<Bitmap>()

    private var isResumed = false
    private var isCpuUsageEnabled = false
    private var isMemoryUsageEnabled = false
    private var isStressTestEnabled = false
    private var isForegroundTasksEnabled = false

    private val foregroundRunnable = object : Runnable {
        override fun run() {
            // stay below the long task threshold
            Thread.sleep(80)
            handler.postDelayed(this, 1)
        }
    }

    fun resume() {
        isResumed = true
        if (isCpuUsageEnabled) {
            startCpuThread()
        }
        if (isForegroundTasksEnabled) {
            startForegroundTasks()
        }
    }

    fun pause() {
        isResumed = false
        bitmapList.forEach {
            it.recycle()
        }
        bitmapList.clear()
    }

    fun runLongTask() {
        val duration = rng.nextInt(250) + 100
        Thread.sleep(duration.toLong())
    }

    fun runFrozenFrame() {
        val duration = rng.nextInt(500) + 700
        Thread.sleep(duration.toLong())
    }

    fun toggleHeavyComputation(enabled: Boolean) {
        isCpuUsageEnabled = enabled
        if (enabled) startCpuThread()
    }

    fun toggleForegroundTasks(enabled: Boolean) {
        isForegroundTasksEnabled = enabled
        if (enabled) startForegroundTasks()
    }

    fun toggleMemory(enabled: Boolean) {
        isMemoryUsageEnabled = enabled
        if (enabled) {
            fillUpMemory()
        }
    }

    fun toggleStressTest(enabled: Boolean) {
        isStressTestEnabled = enabled
        if (enabled) {
            stressTestRumEvents()
        }
    }

    private fun startCpuThread() {
        Thread {
            while (isResumed && isCpuUsageEnabled) {
                val n = rng.nextInt(4096) + 1024
                piComputer.computePi(n)
            }
        }.start()
    }

    private fun startForegroundTasks() {
        handler.post(foregroundRunnable)
    }

    @SuppressLint("TimberArgCount")
    private fun fillUpMemory() {
        Thread {
            loop@ for (i in 0..128) {
                try {
                    val bitmap = Bitmap.createBitmap(3840, 2160, Bitmap.Config.ARGB_8888)
                    bitmapList.add(bitmap)
                } catch (e: OutOfMemoryError) {
                    Timber.e("Unable to allocate more bitmaps…")
                    break@loop
                }
                Thread.sleep(10)
            }
            Timber.i("Allocated ${bitmapList.size} bitmaps")
        }.start()
    }

    private fun stressTestRumEvents() {
        val fflags = (0..128).associate {
            "ff.flag_$it" to rng.nextInt(4096)
        }
        Thread {
            while (isResumed && isStressTestEnabled) {
//                fflags.forEach { (k, v) ->
//                    GlobalRumMonitor.get().addFeatureFlagEvaluation(k, v)
//                }
                GlobalRumMonitor.get().addFeatureFlagEvaluations(fflags)
                GlobalRumMonitor.get().addAction(
                    RumActionType.CUSTOM,
                    "custom action"
                )
                Thread.sleep(1)
            }
        }.start()
    }
}
