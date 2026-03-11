/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.integration.tests.BaseTest
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.core.integration.tests.utils.DatadogRestApiClientImpl
import com.datadog.android.rum.model.ViewEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RumViewUpdateTest : BaseTest() {

    private lateinit var sdkCore: SdkCore
    private lateinit var datadogApiClient: DatadogRestApiClientImpl
    private var sdkStopped = false
    private val viewEventsList = mutableListOf<ViewEvent>()

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        val clientToken = requireNotNull(args.getString("DD_CLIENT_TOKEN")) {
            "Missing instrumentation argument: DD_CLIENT_TOKEN"
        }
        val rumAppId = requireNotNull(args.getString("DD_RUM_APP_ID")) {
            "Missing instrumentation argument: DD_RUM_APP_ID"
        }
        val apiKey = requireNotNull(args.getString("DD_API_KEY")) {
            "Missing instrumentation argument: DD_API_KEY"
        }
        val appKey = requireNotNull(args.getString("DD_APP_KEY")) {
            "Missing instrumentation argument: DD_APP_KEY"
        }

        val config = Configuration.Builder(
            clientToken = clientToken,
            env = "test",
            variant = "debug"
        )
            .useSite(DatadogSite.LOCAL)
            .setBatchSize(BatchSize.SMALL)
            .setUploadFrequency(UploadFrequency.FREQUENT)
            .build()

        sdkCore = checkNotNull(
            Datadog.initialize(
                ApplicationProvider.getApplicationContext(),
                config,
                TrackingConsent.GRANTED
            )
        )

        Datadog.setVerbosity(android.util.Log.VERBOSE)

        Rum.enable(
            RumConfiguration
                .Builder(rumAppId)
                .setTelemetrySampleRate(100f)
                .trackUserInteractions()
                .trackLongTasks(250L)
                .trackNonFatalAnrs(true)
                .setViewEventMapper { viewEvent ->
                    synchronized(viewEventsList) {
                        viewEvent.context?.additionalProperties?.put("test_view_index", viewEventsList.size)
                        viewEventsList.add(viewEvent)
                    }
                    viewEvent
                }
                .trackBackgroundEvents(true)
                .trackAnonymousUser(true)
                .collectAccessibility(true)
                .build(),
            sdkCore
        )

        val httpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) { json() }
            defaultRequest {
                headers.append("DD-API-KEY", apiKey)
                headers.append("DD-APPLICATION-KEY", appKey)
            }
        }
        datadogApiClient = DatadogRestApiClientImpl(httpClient, "https://dd.datad0g.com")
    }

    @After
    fun tearDown() {
        if (!sdkStopped) {
            Datadog.stopInstance()
        }
    }

    @Test
    fun must_report2Actions_when_2ClickActionsAdded() {
        runBlocking {
            // Given
            val viewKey = UUID.randomUUID().toString()
            val viewName = "rum-view-update-test-${UUID.randomUUID()}"

            // When
            val rumMonitor = GlobalRumMonitor.get(sdkCore)
            rumMonitor.startView(
                key = viewKey,
                name = viewName,
            )
            delay(1000)
            rumMonitor.addAction(RumActionType.CUSTOM, "click1", emptyMap())
            delay(5000)
            rumMonitor.addAction(RumActionType.CUSTOM, "click2", emptyMap())
            delay(5000)
            rumMonitor.stopView(viewKey)
            delay(60000)

            // Then
            val deadline = System.currentTimeMillis() + POLLING_TIMEOUT_MS
            var actionCount = 0
            while (System.currentTimeMillis() < deadline) {
                val response = datadogApiClient.getRumViewEvent(viewName)
                val viewEvent = response.optionalResult
                    ?.get("data")?.jsonArray
                    ?.firstOrNull()?.jsonObject
                actionCount = viewEvent
                    ?.get("attributes")?.jsonObject
                    ?.get("action")?.jsonObject
                    ?.get("count")?.jsonPrimitive?.int
                    ?: 0
                if (viewEvent != null) break
                delay(POLLING_INTERVAL_MS)
            }

            assertThat(actionCount).isEqualTo(1)
        }
    }

    companion object {
        private const val POLLING_TIMEOUT_MS = 300_000L
        private const val POLLING_INTERVAL_MS = 5_000L
    }
}
