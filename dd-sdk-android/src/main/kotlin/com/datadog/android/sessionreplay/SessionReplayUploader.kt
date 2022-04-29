/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import com.datadog.android.core.internal.utils.devLogger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionReplayUploader(val okHttpClient: OkHttpClient, dataFolder: File) {
    private val scheduledThreadPoolExecutor = ScheduledThreadPoolExecutor(1)
    private var wasStarted = AtomicBoolean(false)

    fun startUploading() {
        if (!wasStarted.getAndSet(true)) {
            scheduleUpload()
        }
    }

    private fun scheduleUpload() {
        scheduledThreadPoolExecutor.schedule(uploadRunnable, 5, TimeUnit.SECONDS)
    }

    private val uploadRunnable = {
        dataFolder.listFiles()?.forEach {
            uploadFile(it)
        }
        scheduleUpload()
    }

    private fun uploadFile(file: File) {
        val data = file.inputStream().use {
            it.readBytes()
        }
        val request = if (file.absolutePath.contains("jpeg")) {
            Request.Builder()
                .addHeader("Content-Type", "text/plain")
                .post(RequestBody.create(null, data))
                .url("http://10.0.2.2:3000/save-image")
                .build()
        } else {
            Request.Builder()
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(null, data))
                .url("http://10.0.2.2:3000/save-view-tree")
                .build()
        }
        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                devLogger.e(SessionReplay.javaClass.simpleName, e)
                file.delete()
            }

            override fun onResponse(call: Call, response: Response) {
                devLogger.v(SessionReplay.javaClass.simpleName + ":" + response.message())
                file.delete()
            }
        })
    }
}