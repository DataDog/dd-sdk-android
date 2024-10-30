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
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import com.datadog.android.trace.opentelemetry.OtelTracerProvider
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlin.time.Duration.Companion.seconds

/**
 * A class to create a local server used to redirect local calls to remote urls.
 */
@OptIn(DelicateCoroutinesApi::class)
public class LocalServer {

    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

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
            service = SERVICE_NAME
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

        val tracesConfig = TraceConfiguration.Builder().build()
        Trace.enable(tracesConfig)
        logger = Logger.Builder(instance)
            .setLogcatLogsEnabled(true)
            .build()
    }

    /**
     * Start redirecting calls to the given url.
     * @param redirectedUrl the url to redirect to
     */
    @Suppress("MagicNumber")
    fun start(redirectedUrl: String) {
        logger.i("Starting the server")
        engine = embeddedServer(Netty, PORT) {
            val tracerProvider = OtelTracerProvider.Builder().setService(SERVICE_NAME).build()
            val tracer = tracerProvider.get("ktor")

//            install(ContentNegotiation) { gson() }
            install(SSE)
            routing {
                get(GET_PATH) {
                    logger.i(
                        "Redirecting request",
                        attributes = mapOf(
                            "redirection.from" to LOCAL_URL,
                            "redirection.to" to redirectedUrl
                        )
                    )
                    val redirectSpan = tracer.spanBuilder("redirect").startSpan()
                    redirectSpan.setAttribute("redirection.from", LOCAL_URL)
                    redirectSpan.setAttribute("redirection.to", redirectedUrl)
                    call.respondRedirect(redirectedUrl, false)
                    redirectSpan.end()
                }
                sse(SSE_PATH) {
                    val sseFlow = flow {
                        statuses.forEach {
                            emit(SseEvent(data = it))
                            delay(5.seconds)
                        }
                    }.shareIn(GlobalScope, SharingStarted.WhileSubscribed(5_000))

                    sseFlow.collect { sseEvent ->
                        send(ServerSentEvent(data = sseEvent.toJson()))
                    }
                }
            }
        }.start(wait = false)
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
     * Returns the URL to this local server for a GET request.
     * @return the url
     */
    fun getUrl(): String {
        return LOCAL_URL + GET_PATH
    }

    /**
     * Returns the URL to this local server for an SSE request.
     * @return the url
     */
    fun sseUrl(): String {
        return LOCAL_URL + SSE_PATH
    }

    companion object {

        private const val SERVICE_NAME = "com.datadog.android.vendor.sample"
        private const val DATADOG_INSTANCE_ID = "com.datadog.android.vendor.sample"

        private const val HOST = "127.0.0.1"
        private const val PORT = 8080
        private const val GET_PATH = "/page"
        private const val SSE_PATH = "/events"
        private const val LOCAL_URL = "http://$HOST:$PORT"

        private const val SHUTDOWN_MS = 500L
        private const val STOP_TIMEOUT_MS = 1500L

        private val statuses = arrayOf("foo", "bar", "baz", "spam", "eggs", "bacon", "blip", "plop")
    }
}
