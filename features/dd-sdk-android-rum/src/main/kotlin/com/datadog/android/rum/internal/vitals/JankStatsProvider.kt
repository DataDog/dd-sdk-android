/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.vitals

import android.view.Window
import androidx.annotation.UiThread
import androidx.metrics.performance.JankStats
import com.datadog.android.api.InternalLogger

internal interface JankStatsProvider {

    @UiThread
    fun createJankStatsAndTrack(
        window: Window,
        listener: JankStats.OnFrameListener,
        internalLogger: InternalLogger
    ): JankStats?

    companion object {

        val DEFAULT = object : JankStatsProvider {
            @UiThread
            override fun createJankStatsAndTrack(
                window: Window,
                listener: JankStats.OnFrameListener,
                internalLogger: InternalLogger
            ): JankStats? {
                return try {
                    JankStats.createAndTrack(window, listener)
                } catch (e: IllegalStateException) {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { "Unable to attach JankStats to the current window" },
                        e
                    )
                    null
                }
            }
        }
    }
}
