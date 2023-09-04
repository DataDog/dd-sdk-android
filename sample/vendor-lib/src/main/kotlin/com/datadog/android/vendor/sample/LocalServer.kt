/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.vendor.sample

import android.content.Context
import android.util.Log
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.log.Logger
import com.datadog.android.log.Logs
import com.datadog.android.log.LogsConfiguration
import com.datadog.android.privacy.TrackingConsent
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.gson.gson
import io.ktor.response.respondRedirect
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * A class to create a local server used to redirect local calls to remote urls.
 */
public class LocalServer {

    private var engine: ApplicationEngine? = null

    private lateinit var logger: Logger

    /**
     * Initialise the server.
     * @param context the application context
     */
    fun init(context: Context) {
        Datadog.setVerbosity(Log.VERBOSE)
        val configuration = Configuration.Builder(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            env = "prod",
            service = "com.datadog.android.vendor.sample"
        )
            .useSite(DatadogSite.US1)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()

        Datadog.initialize(
            DATADOG_INSTANCE_ID,
            context,
            configuration,
            TrackingConsent.GRANTED
        )
        val instance = Datadog.getInstance(DATADOG_INSTANCE_ID)
        instance.setUserInfo(id = context.packageName)

        val logsConfig = LogsConfiguration.Builder()
            .build()
        Logs.enable(logsConfig, instance)

        logger = Logger.Builder(instance)
            .setLogcatLogsEnabled(true)
            .build()
    }

    /**
     * Start redirecting calls to the given url.
     * @param redirectedUrl the url to redirect to
     */
    fun start(redirectedUrl: String) {
        logger.i("Starting the server")
        engine = embeddedServer(Netty, PORT) {
            install(ContentNegotiation) { gson() }
            routing {
                get(PATH) {
                    logger.i(
                        "Redirecting request",
                        attributes = mapOf(
                            "redirection.from" to LOCAL_URL,
                            "redirection.to" to redirectedUrl
                        )
                    )
                    call.respondRedirect(redirectedUrl, false)
                }
            }
        }
        engine?.start(wait = false)
    }

    /**
     * Stop the redirection.
     */
    fun stop() {
        logger.i("Stopping the server")
        Thread {
            engine?.stop(SHUTDOWN_MS, STOP_TIMEOUT_MS)
            logger.i("Server stopped")
        }.start()
    }

    /**
     * Returns the URL to this local server.
     * @return the url
     */
    fun getUrl(): String {
        return LOCAL_URL
    }

    companion object {

        private const val DATADOG_INSTANCE_ID = "com.datadog.android.vendor.sample"

        private const val HOST = "127.0.0.1"
        private const val PORT = 8080
        private const val PATH = "/"
        private const val LOCAL_URL = "http://$HOST:$PORT/"

        private const val SHUTDOWN_MS = 500L
        private const val STOP_TIMEOUT_MS = 1500L
    }
}
