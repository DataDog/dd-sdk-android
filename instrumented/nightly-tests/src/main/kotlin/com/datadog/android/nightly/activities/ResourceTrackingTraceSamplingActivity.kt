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
import com.datadog.android.nightly.server.LocalServer
import com.datadog.android.okhttp.DatadogInterceptor
import fr.xgouchet.elmyr.Forge
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class ResourceTrackingTraceSamplingActivity : AppCompatActivity() {

    private val localServer: LocalServer by lazy { LocalServer() }

    private val forge = Forge()

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            DatadogInterceptor(
                firstPartyHosts = listOf(LocalServer.HOST),
                // 75% of the RUM resources sent should have traces included
                traceSampler = @Suppress("MagicNumber") RateBasedSampler(75f)
            )
        )
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tracking_strategy_activity)
    }

    override fun onResume() {
        super.onResume()
        val countDownLatch = CountDownLatch(REQUEST_COUNT)
        localServer.start { it.respond(HttpStatusCode.OK, "{}") }
        repeat(REQUEST_COUNT) {
            okHttpClient
                .newCall(
                    Request.Builder()
                        .url(localServer.getUrl() + "#${forge.anAlphabeticalString()}")
                        .build()
                )
                .enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            countDownLatch.countDown()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            countDownLatch.countDown()
                        }
                    }
                )
        }
        countDownLatch.await(2, TimeUnit.MINUTES)
    }

    override fun onPause() {
        localServer.stop()
        super.onPause()
    }

    companion object {
        internal const val REQUEST_COUNT = 100
    }
}
