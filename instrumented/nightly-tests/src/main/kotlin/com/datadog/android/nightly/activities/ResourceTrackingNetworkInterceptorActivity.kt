/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import com.datadog.android.nightly.server.LocalServer
import com.datadog.android.okhttp.rum.RumInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptor
import okhttp3.OkHttpClient

internal class ResourceTrackingNetworkInterceptorActivity : ResourceTrackingActivity() {

    private val localServer: LocalServer by lazy { LocalServer() }

    override val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(RumInterceptor(traceSamplingRate = HUNDRED_PERCENT))
            .addNetworkInterceptor(
                TracingInterceptor(
                    listOf(
                        HOST,
                        LocalServer.HOST
                    ),
                    traceSamplingRate = HUNDRED_PERCENT
                )
            )
            .build()
    }

    override val randomUrl: String by lazy {
        localServer.getUrl()
    }

    override fun onResume() {
        localServer.start(RANDOM_RESOURCE_WITH_REDIRECT_URL)
        super.onResume()
    }

    override fun onPause() {
        localServer.stop()
        super.onPause()
    }

    companion object {
        internal const val RANDOM_RESOURCE_WITH_REDIRECT_URL =
            "https://$HOST/20/20"
    }
}
