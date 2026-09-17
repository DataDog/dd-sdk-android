/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.rum

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.api.SdkCore
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.integration.tests.network.DatadogRestApiClient
import com.datadog.android.core.integration.tests.network.DatadogRestApiClientImpl
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum._RumInternalProxy
import com.datadog.android.rum.configuration.RumViewEventWriteConfig
import com.datadog.android.rum.model.ViewEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before

abstract class BaseRumViewTest {

    internal lateinit var sdkCore: SdkCore
    internal lateinit var datadogApiClient: DatadogRestApiClient
    internal val viewEventsList = mutableListOf<ViewEvent>()

    @Before
    fun setUpBase() {
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
            variant = "debug",
            service = "test-service"
        )
            .useSite(DatadogSite.STAGING)
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
        Datadog.setUserInfo(
            id = "test-user-id",
            name = "Test User",
            email = "test@example.com",
            sdkCore = sdkCore
        )

        val rumConfig = RumConfiguration
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
            .apply {
                _RumInternalProxy.setRumViewEventWriteConfig(
                    builder = this@apply,
                    config = RumViewEventWriteConfig.FullViewOnlyAtStart
                )
            }
            .build()

        Rum.enable(rumConfig, sdkCore)

        datadogApiClient = DatadogRestApiClientImpl(
            httpClient = HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(json = Json { ignoreUnknownKeys = true })
                }
                defaultRequest {
                    headers.append("DD-API-KEY", apiKey)
                    headers.append("DD-APPLICATION-KEY", appKey)
                }
            },
            baseUrl = "https://dd.datad0g.com"
        )
    }

    @After
    fun tearDownBase() {
        Datadog.stopInstance()
        viewEventsList.clear()
        if (::datadogApiClient.isInitialized) {
            datadogApiClient.close()
        }
    }
}
