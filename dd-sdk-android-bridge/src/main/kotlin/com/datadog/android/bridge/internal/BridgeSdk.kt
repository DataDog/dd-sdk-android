/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.bridge.internal

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog as DatadogSDK
import com.datadog.android.DatadogConfig
import com.datadog.android.bridge.DdSdk
import com.datadog.android.bridge.DdSdkConfiguration
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor

internal class BridgeSdk(context: Context) : DdSdk {

    internal val appContext: Context = context.applicationContext

    override fun initialize(configuration: DdSdkConfiguration) {
        val configBuilder = DatadogConfig.Builder(
            clientToken = configuration.clientToken,
            envName = configuration.env,
            applicationId = configuration.applicationId ?: NULL_UUID
        )

        DatadogSDK.setVerbosity(Log.VERBOSE)
        DatadogSDK.initialize(appContext, configBuilder.build())
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    companion object {
        internal const val NULL_UUID = "00000000-0000-0000-0000-000000000000"
    }
}
