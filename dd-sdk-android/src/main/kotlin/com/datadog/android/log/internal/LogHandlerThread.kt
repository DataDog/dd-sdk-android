/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.HandlerThread

internal class LogHandlerThread : HandlerThread(THREAD_NAME) {

    internal lateinit var handler: LogHandler

    override fun onLooperPrepared() {
        super.onLooperPrepared()
        handler = LogHandler(looper)
    }

    companion object {
        private const val THREAD_NAME = "ddog"
    }
}
