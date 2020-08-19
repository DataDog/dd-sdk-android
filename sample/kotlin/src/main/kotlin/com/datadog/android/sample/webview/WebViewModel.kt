/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.webview

import androidx.lifecycle.ViewModel
import com.datadog.android.sample.server.LocalServer

class WebViewModel : ViewModel() {

    val localServer = LocalServer()
    val url: String = localServer.getUrl()

    fun onResume() {
        localServer.start("https://www.datadoghq.com/")
    }

    fun onPause() {
        localServer.stop()
    }
}
