/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.os.Handler
import android.os.Looper

internal class LogHandler(looper: Looper) : Handler(looper) {

    private val runnable = LogUploadRunnable(this)

    init {
        postDelayed(runnable, INITIAL_DELAY_MS)
    }

    companion object {
        const val INITIAL_DELAY_MS = 5000L
    }
}
