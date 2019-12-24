/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import android.os.HandlerThread
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.log.internal.net.LogUploader
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.system.SystemInfoProvider

internal class LogHandlerThread(
    private val reader: Reader,
    private val logUploader: LogUploader,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider
) : HandlerThread(THREAD_NAME) {

    private lateinit var handler: Handler

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        handler = Handler(looper)
        val runnable = LogUploadRunnable(
            handler,
            reader,
            logUploader,
            networkInfoProvider,
            systemInfoProvider
        )
        handler.postDelayed(runnable, LogUploadRunnable.DEFAULT_DELAY)
    }

    companion object {
        private const val THREAD_NAME = "ddog"
    }
}
