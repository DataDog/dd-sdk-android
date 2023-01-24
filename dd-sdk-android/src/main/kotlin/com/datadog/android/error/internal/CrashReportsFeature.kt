/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:Suppress("DEPRECATION")

package com.datadog.android.error.internal

import android.content.Context
import com.datadog.android.plugin.DatadogPlugin
import com.datadog.android.v2.api.Feature
import com.datadog.android.v2.api.SdkCore
import java.util.concurrent.atomic.AtomicBoolean

internal class CrashReportsFeature(
    internal val plugins: List<DatadogPlugin>
) : Feature {

    internal val initialized = AtomicBoolean(false)
    internal var originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    private lateinit var sdkCore: SdkCore

    // region Feature

    override val name: String = CRASH_FEATURE_NAME
    override fun onInitialize(sdkCore: SdkCore, appContext: Context) {
        this.sdkCore = sdkCore

        setupExceptionHandler(appContext)
        initialized.set(true)
    }

    fun stop() {
        resetOriginalExceptionHandler()
        initialized.set(false)
    }

    // endregion

    // region Internal

    private fun setupExceptionHandler(
        appContext: Context
    ) {
        originalUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
        DatadogExceptionHandler(
            sdkCore = sdkCore,
            appContext = appContext
        ).register()
    }

    private fun resetOriginalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalUncaughtExceptionHandler)
    }

    // endregion

    companion object {
        internal const val CRASH_FEATURE_NAME = "crash"
    }
}
