/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.vitals

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.ViewModel
import java.security.SecureRandom

class VitalsViewModel : ViewModel() {

    val rng = SecureRandom()
    private val piComputer = PiDigitComputerBBP()
    private val handler = Handler(Looper.getMainLooper())
    private val bitmapList = mutableListOf<Bitmap>()

    var isResumed = false
    var isCpuUsageEnabled = false
    var isMemoryUsageEnabled = false
    var isForegroundTasksEnabled = false

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
        val random = SecureRandom()
        val duration = random.nextInt(250) + 100
        Thread.sleep(duration.toLong())
    }

    fun runFrozenFrame() {
        val random = SecureRandom()
        val duration = random.nextInt(500) + 700
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

    private fun fillUpMemory() {
        Thread {
            loop@ for (i in 0..128) {
                try {
                    val bitmap = Bitmap.createBitmap(3840, 2160, Bitmap.Config.ARGB_8888)
                    bitmapList.add(bitmap)
                } catch (e: OutOfMemoryError) {
                    Log.e("Vitals", "Unable to allocte more bitmaps…")
                    break@loop
                }
                Thread.sleep(10)
            }
            Log.i("Vitals", "Allocated ${bitmapList.size} bitmaps")
        }.start()
    }
}
