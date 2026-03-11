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
import com.datadog.android.core.configuration.BatchProcessingLevel
import com.datadog.android.core.configuration.BatchSize
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.UploadFrequency
import com.datadog.android.core.integration.tests.BaseTest
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumConfiguration
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RumViewUpdateTest : BaseTest() {

    private lateinit var sdkCore: SdkCore
    private lateinit var apiKey: String
    private lateinit var appKey: String
    private var sdkStopped = false

    private val okHttpClient = OkHttpClient()

    @Before
    fun setUp() {
        val args = InstrumentationRegistry.getArguments()
        val clientToken = requireNotNull(args.getString("DD_CLIENT_TOKEN")) {
            "Missing instrumentation argument: DD_CLIENT_TOKEN"
        }
        val rumAppId = requireNotNull(args.getString("DD_RUM_APP_ID")) {
            "Missing instrumentation argument: DD_RUM_APP_ID"
        }
        apiKey = requireNotNull(args.getString("DD_API_KEY")) {
            "Missing instrumentation argument: DD_API_KEY"
        }
        appKey = requireNotNull(args.getString("DD_APP_KEY")) {
            "Missing instrumentation argument: DD_APP_KEY"
        }

        val config = Configuration.Builder(
            clientToken = clientToken,
            env = "test",
            variant = "debug"
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

        Rum.enable(
            RumConfiguration
                .Builder(rumAppId)
                .setTelemetrySampleRate(100f)
                .trackUserInteractions()
                .trackLongTasks(250L)
                .trackNonFatalAnrs(true)
                .trackBackgroundEvents(true)
                .trackAnonymousUser(true)
                .collectAccessibility(true)
                .build(),
            sdkCore
        )
    }

    @After
    fun tearDown() {
        if (!sdkStopped) {
            Datadog.stopInstance()
        }
    }

    @Test
    fun must_report2Actions_when_2ClickActionsAdded() {
        // Given
        val viewKey = UUID.randomUUID().toString()
        val viewName = "RumViewUpdateTest/${UUID.randomUUID()}"
        val testStartTime = Instant.now().minusSeconds(60)

        // When
        val rumMonitor = GlobalRumMonitor.get(sdkCore)
        rumMonitor.startView(viewKey, viewName)
        rumMonitor.addAction(RumActionType.TAP, "click1", emptyMap())
        rumMonitor.addAction(RumActionType.TAP, "click2", emptyMap())
        rumMonitor.stopView(viewKey)

        Datadog.stopInstance()
        sdkStopped = true

        // Then
        val deadline = System.currentTimeMillis() + POLLING_TIMEOUT_MS
        var actionCount = 0
        while (System.currentTimeMillis() < deadline) {
            actionCount = searchRumActionCount(
                query = """@type:action @view.name:"$viewName"""",
                from = testStartTime,
                to = Instant.now().plusSeconds(POLLING_TIMEOUT_MS / 1000)
            )
            if (actionCount >= 2) break
            Thread.sleep(POLLING_INTERVAL_MS)
        }

        assertThat(actionCount).isEqualTo(2)
    }

    private fun searchRumActionCount(query: String, from: Instant, to: Instant): Int {
        val body = """{"filter":{"query":"$query","from":"$from","to":"$to"}}"""
        val request = Request.Builder()
            .url("https://api.datadoghq.com/api/v2/rum/events/search")
            .addHeader("DD-API-KEY", apiKey)
            .addHeader("DD-APPLICATION-KEY", appKey)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val responseBody = okHttpClient.newCall(request).execute().use { response ->
            checkNotNull(response.body).string()
        }

        return JsonParser.parseString(responseBody)
            .asJsonObject
            .getAsJsonArray("data")
            ?.size()
            ?: 0
    }

    companion object {
        private const val POLLING_TIMEOUT_MS = 300_000L
        private const val POLLING_INTERVAL_MS = 5_000L
    }
}
