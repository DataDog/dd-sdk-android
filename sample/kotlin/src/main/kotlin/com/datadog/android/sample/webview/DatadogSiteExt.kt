/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.webview

import com.datadog.android.DatadogSite
import com.datadog.android.sample.BuildConfig
import timber.log.Timber

internal val BROWSER_SITE: String
    get() {
        return try {
            DatadogSite.valueOf(BuildConfig.DD_SITE_NAME)
        } catch (e: IllegalArgumentException) {
            Timber.e("Error setting site to ${BuildConfig.DD_SITE_NAME}")
            null
        }.browserSite()
    }

private fun DatadogSite?.browserSite(): String {
    return when (this) {
        DatadogSite.US1,
        DatadogSite.STAGING,
        null -> "datadoghq.com"

        DatadogSite.US3 -> "us3.datadoghq.com"
        DatadogSite.US5 -> "us5.datadoghq.com"
        DatadogSite.EU1 -> "datadoghq.eu"
        DatadogSite.AP1 -> "ap1.datadoghq.com"
        DatadogSite.US1_FED -> "ddog-gov.com"
    }
}
