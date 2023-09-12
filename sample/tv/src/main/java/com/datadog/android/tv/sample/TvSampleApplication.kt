/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tv.sample

import android.app.Application
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.sampling.RateBasedSampler
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.okhttp.DatadogEventListener
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.okhttp.trace.TracingInterceptor
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.timber.DatadogTree
import com.datadog.android.tv.sample.net.OkHttpDownloader
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.schabi.newpipe.extractor.NewPipe
import timber.log.Timber
import kotlin.random.Random

/**
 * The main [Application] for the sample TV project.
 */
class TvSampleApplication : Application() {

    lateinit var okHttpClient: OkHttpClient

    override fun onCreate() {
        super.onCreate()
        initializeDatadog()
        initializeTimber()
        initializeOkHttp()
        initializeNewPipe()
    }

    private fun initializeDatadog() {
        Datadog.setVerbosity(Log.VERBOSE)
        Datadog.initialize(
            this,
            createDatadogConfiguration(),
            TrackingConsent.GRANTED
        )

        val rumConfig = createRumConfiguration()
        Rum.enable(rumConfig)

        val logsConfig = LogsConfiguration.Builder().build()
        Logs.enable(logsConfig)

        GlobalRumMonitor.get().debug = true
    }

    private fun createRumConfiguration(): RumConfiguration {
        return RumConfiguration.Builder(BuildConfig.DD_RUM_APPLICATION_ID)
            .useViewTrackingStrategy(
                ActivityViewTrackingStrategy(true)
            )
            .setTelemetrySampleRate(FULL_SAMPLING_RATE)
            .trackUserInteractions()
            .build()
    }

    private fun createDatadogConfiguration(): Configuration {
        return Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = "test",
            variant = ""
        )
            .useSite(DatadogSite.US1)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()
    }

    @Suppress("TooGenericExceptionCaught", "CheckInternal")
    private fun initializeTimber() {
        val logger = Logger.Builder()
            .setName("timber")
            .setNetworkInfoEnabled(true)
            .setLogcatLogsEnabled(true)
            .build()

        Timber.plant(DatadogTree(logger))
    }

    private fun initializeOkHttp() {
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(DatadogInterceptor(traceSampler = RateBasedSampler(FULL_SAMPLING_RATE)))
            .addNetworkInterceptor(
                TracingInterceptor(
                    traceSampler = RateBasedSampler(
                        FULL_SAMPLING_RATE
                    )
                )
            )
            .addInterceptor {
                val request = it.request()
                val url = request.url
                val host = url.host
                if (host.endsWith("googlevideo.coma") ) {
                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_2)
                        .code(500)
                        .message("Error")
                        .body("".toResponseBody())
                        .build()
                } else {
                    it.proceed(request)
                }
            }
            .eventListenerFactory(DatadogEventListener.Factory())
            .build()
    }

    private fun initializeNewPipe() {
        NewPipe.init(OkHttpDownloader(okHttpClient))
    }

    companion object {
        private const val FULL_SAMPLING_RATE = 100f
    }
}
