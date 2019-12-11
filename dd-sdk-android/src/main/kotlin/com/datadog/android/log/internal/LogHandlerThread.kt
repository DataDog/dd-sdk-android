/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import android.os.HandlerThread
import com.datadog.android.log.internal.net.LogUploader

internal class LogHandlerThread(
    private val logReader: LogReader,
    private val logUploader: LogUploader
) : HandlerThread(THREAD_NAME) {

    private lateinit var handler: Handler

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        handler = Handler(looper)
        val runnable = LogUploadRunnable(handler, logReader, logUploader)
        handler.postDelayed(runnable, LogUploadRunnable.DEFAULT_DELAY)
    }

    companion object {
        private const val THREAD_NAME = "ddog"
    }
}
