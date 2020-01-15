/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import android.os.Handler
import android.os.HandlerThread
import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.net.DataUploader
import com.datadog.android.log.internal.net.NetworkInfoProvider
import com.datadog.android.log.internal.system.SystemInfoProvider

internal class DataUploadHandlerThread(
    threadName: String,
    private val reader: Reader,
    private val dataUploader: DataUploader,
    private val networkInfoProvider: NetworkInfoProvider,
    private val systemInfoProvider: SystemInfoProvider
) : HandlerThread(threadName) {

    private lateinit var handler: Handler

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        handler = Handler(looper)
        val runnable =
            DataUploadRunnable(
                handler,
                reader,
                dataUploader,
                networkInfoProvider,
                systemInfoProvider
            )
        handler.postDelayed(runnable, DataUploadRunnable.DEFAULT_DELAY)
    }
}
