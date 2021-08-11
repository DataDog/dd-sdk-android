/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.services

import android.app.Service
import android.os.Bundle
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.nightly.BuildConfig
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor

internal abstract class CrashService : Service() {

    protected fun initRum(extras: Bundle?) {
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
        extras?.let { bundle ->
            bundle.keySet().forEach {
                GlobalRum.addAttribute(it, bundle[it])
            }
        }
        GlobalRum.get().startView(this, this.javaClass.simpleName.toString())
    }

    protected fun getCredentials() = Credentials(
        clientToken = BuildConfig.NIGHTLY_TESTS_TOKEN,
        envName = "instrumentation",
        variant = "",
        rumApplicationId = BuildConfig.NIGHTLY_TESTS_RUM_APP_ID
    )

    companion object {
        const val CRASH_HANDLER_DISABLED_SCENARIO = "crash_handler_disabled_scenario"
        const val RUM_DISABLED_SCENARIO = "rum_disabled_scenario"
        const val RUM_ENABLED_SCENARIO = "rum_enabled_scenario"
    }
}
