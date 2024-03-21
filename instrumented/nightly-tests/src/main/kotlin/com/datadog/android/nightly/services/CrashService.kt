/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.services

import android.app.Service
import android.os.Bundle
import com.datadog.android.api.SdkCore
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.rum.GlobalRumMonitor

internal abstract class CrashService : Service() {

    protected fun initRum(sdkCore: SdkCore, extras: Bundle?) {
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        extras?.let { bundle ->
            bundle.keySet().forEach {
                // TODO RUM-503 Bundle#get is deprecated, but there is no replacement for it.
                // Issue is opened in the Google Issue Tracker.
                @Suppress("DEPRECATION")
                rumMonitor.addAttribute(it, bundle[it])
            }
        }
        rumMonitor.startView(this, this.javaClass.simpleName.toString())
        // this is a hack to write RUM view to the disk, so that file exists by the time NDK crashes
        rumMonitor.addTiming("foo")
    }

    protected val rumApplicationId = BuildConfig.NIGHTLY_TESTS_RUM_APP_ID

    companion object {
        const val CRASH_HANDLER_DISABLED_SCENARIO = "crash_handler_disabled_scenario"
        const val RUM_DISABLED_SCENARIO = "rum_disabled_scenario"
        const val RUM_ENABLED_SCENARIO = "rum_enabled_scenario"
        const val ENCRYPTION_ENABLED_SCENARIO = "rum_encryption_enabled_scenario"
    }
}
