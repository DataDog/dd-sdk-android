/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.webview

import androidx.lifecycle.ViewModel
import com.datadog.android.vendor.sample.LocalServer

internal class WebViewModel(
    val localServer: LocalServer
) : ViewModel() {

    val url: String = localServer.getUrl()

    fun onResume() {
        localServer.start(
            "https://datadoghq.dev/browser-sdk-test-playground/webview-support/"
        )
    }

    fun onPause() {
        localServer.stop()
    }
}
