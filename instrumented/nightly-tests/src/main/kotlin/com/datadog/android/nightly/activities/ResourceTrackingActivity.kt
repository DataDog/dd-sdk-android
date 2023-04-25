/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.nightly.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.nightly.R
import com.datadog.android.okhttp.DatadogInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal open class ResourceTrackingActivity : AppCompatActivity() {

    open val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            DatadogInterceptor(
                traceSampler = RateBasedSampler(HUNDRED_PERCENT)
            )
        )
        .build()

    open val randomUrl: String = RANDOM_URL
    private val countDownLatch = CountDownLatch(1)

    // region activity lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tracking_strategy_activity)
    }

    override fun onResume() {
        super.onResume()
        okHttpClient.newCall(Request.Builder().url(randomUrl).build()).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    countDownLatch.countDown()
                }

                override fun onResponse(call: Call, response: Response) {
                    countDownLatch.countDown()
                }
            }
        )
        countDownLatch.await(2, TimeUnit.MINUTES)
    }

    // endregion

    companion object {
        internal const val HOST = "picsum.photos"
        internal const val RANDOM_URL = "https://$HOST/800/450"
    }
}
