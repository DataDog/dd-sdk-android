/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.rules

import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android._InternalProxy
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.sdk.integration.network.utils.TestEchoWebServer
import com.datadog.android.trace.DatadogTracing
import com.datadog.android.trace.GlobalDatadogTracer
import com.datadog.android.trace.Trace
import com.datadog.android.trace.TraceConfiguration
import fr.xgouchet.elmyr.Forge
import org.junit.rules.ExternalResource

/**
 * JUnit Rule for network instrumentation integration tests.
 *
 * Manages [com.datadog.android.sdk.integration.network.utils.TestEchoWebServer] and Datadog SDK initialization.
 */
internal class NetworkInstrumentationTestRule : ExternalResource() {

    private var sdkCore: SdkCore? = null
    private val mockWebServer = TestEchoWebServer()

    val forge = Forge()

    /**
     * Base URL for HTTP requests to the mock server.
     */
    val baseUrl: String
        get() = mockWebServer.baseUrl

    override fun before() {
        mockWebServer.start()
        setupDatadogSdk()
    }

    override fun after() {
        GlobalDatadogTracer.clear()
        Datadog.stopInstance()
        mockWebServer.shutdown()

        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .cacheDir
            .deleteRecursively()
    }

    private fun setupDatadogSdk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val sdkConfig = Configuration.Builder(
            clientToken = FAKE_CLIENT_TOKEN,
            env = FAKE_ENV
        )
            .apply { _InternalProxy.allowClearTextHttp(this) }
            .build()

        sdkCore = checkNotNull(
            Datadog.initialize(context, sdkConfig, TrackingConsent.GRANTED)
        ) { "Failed to initialize Datadog SDK" }

        Trace.enable(
            TraceConfiguration.Builder()
                .build()
        )

        GlobalDatadogTracer.registerIfAbsent(
            DatadogTracing.newTracerBuilder()
                .withPartialFlushMinSpans(1)
                .build()
        )
    }

    companion object {
        private const val FAKE_CLIENT_TOKEN = "fake-token"
        private const val FAKE_ENV = "integration-test"
    }
}
