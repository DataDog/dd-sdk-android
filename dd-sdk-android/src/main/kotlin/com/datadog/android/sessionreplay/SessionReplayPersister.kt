/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay

import android.graphics.Bitmap
import android.util.Base64
import com.datadog.android.sessionreplay.model.Segment
import com.google.gson.Gson
import com.google.gson.JsonElement
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal class SessionReplayPersister(val dataFolder: File,val sessionReplayVitals: SessionReplayVitals) {
    private val threadPoolExecutor = ThreadPoolExecutor(
        1,
        Runtime.getRuntime().availableProcessors(),
        TimeUnit.SECONDS.toMillis(5),
        TimeUnit.MILLISECONDS,
        LinkedBlockingDeque()
    )
    private var wasStarted = AtomicBoolean(false)
    private val gson = Gson()

    fun persist(bitmap: Bitmap, id: String) {
        prepare()
        threadPoolExecutor.execute {
            val bitmapFile = File(dataFolder, "${id}.jpeg")
            val quality = 100
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val imageAsBase64=
                Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            FileOutputStream(bitmapFile).use {
               it.write(imageAsBase64.toByteArray())
            }
            sessionReplayVitals.logVitals()
        }
    }

    fun persist(jsonTree: JsonElement) {
        prepare()
        threadPoolExecutor.execute {
            val file = File(dataFolder, System.nanoTime().toString())
            FileOutputStream(file).use {
                it.write(jsonTree.toString().toByteArray())
            }
            sessionReplayVitals.logVitals()
        }
    }

    fun persist(segment: Segment) {
        prepare()
        threadPoolExecutor.execute {
            val serializedSegment = gson.toJson(segment).toString()
            val file = File(dataFolder, System.nanoTime().toString())
            FileOutputStream(file).use {
                it.write(serializedSegment.toString().toByteArray())
            }
            sessionReplayVitals.logVitals()
        }
    }

    private fun prepare() {
        if (!wasStarted.getAndSet(true)) {
            threadPoolExecutor.execute {
                if(dataFolder.exists()){
                    dataFolder.listFiles()?.forEach { it.delete() }
                }
                else {
                    dataFolder.mkdirs()
                }
            }
        }
    }
}