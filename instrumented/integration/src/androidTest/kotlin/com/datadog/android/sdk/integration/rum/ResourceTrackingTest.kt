/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.rum

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.datadog.android.api.instrumentation.network.HttpRequestInfo
import com.datadog.android.api.instrumentation.network.HttpResponseInfo
import com.datadog.android.okhttp.DatadogInterceptor
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.RumResourceAttributesProvider
import com.datadog.android.sdk.integration.RuntimeConfig
import com.datadog.android.sdk.okhttp.RecordingDispatcher
import com.datadog.android.sdk.rules.HandledRequest
import com.datadog.android.sdk.rules.RumMockServerActivityTestRule
import com.datadog.android.sdk.utils.exhaustiveAttributes
import com.datadog.android.sdk.utils.isRumUrl
import com.datadog.tools.unit.ConditionWatcher
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@LargeTest
internal class ResourceTrackingTest {

    @get:Rule
    val mockServerRule = RumMockServerActivityTestRule(
        ActivityTrackingPlaygroundActivity::class.java,
        keepRequests = true,
        trackingConsent = TrackingConsent.GRANTED
    )

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var extraAttributes: Map<String, Any?>

    @Before
    fun setUp() {
        val useNewApi = mockServerRule.forge.aBool()
        extraAttributes = mockServerRule.forge.exhaustiveAttributes()
        okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                DatadogInterceptor.Builder(emptyMap())
                    .setRumResourceAttributesProvider(object : RumResourceAttributesProvider {
                        @Deprecated("Use the variant with HttpRequestInfo/HttpResponseInfo instead")
                        override fun onProvideAttributes(
                            request: Request,
                            response: Response?,
                            throwable: Throwable?
                        ): Map<String, Any?> = if (useNewApi) emptyMap() else extraAttributes

                        override fun onProvideAttributes(
                            request: HttpRequestInfo,
                            response: HttpResponseInfo?,
                            throwable: Throwable?
                        ): Map<String, Any?> = if (useNewApi) extraAttributes else emptyMap()
                    }).build()
            )
            .build()
    }

    @Test
    fun verifyAttributesAreSentWhenRequestIsSuccessful() {
        val resourceUrl = "${mockServerRule.getConnectionUrl()}/nyan-cat.gif"

        okHttpClient.newCall(
            Request.Builder().url(resourceUrl).build()
        ).execute()

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            val resourceRequests =
                mockServerRule.getRequests(RuntimeConfig.rumEndpointUrl)
                    .rumEventsBy { it.has("resource") && it.get("type").asString == "resource" }

            resourceRequests.verifyEventMatches(
                listOf(
                    ExpectedResourceEvent(
                        url = resourceUrl,
                        statusCode = 200,
                        extraAttributes = extraAttributes
                    )
                )
            )
            true
        }.doWait(timeoutMs = RumTest.FINAL_WAIT_MS)
    }

    @Test
    fun verifyAttributesAreSentWhenRequestHasException() {
        val resourceUrl = mockServerRule.getConnectionUrl() +
            RecordingDispatcher.CONNECTION_ISSUE_PATH

        assertThrows(IOException::class.java) {
            okHttpClient.newCall(
                Request.Builder().url(resourceUrl).build()
            ).execute()
        }

        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        ConditionWatcher {
            val resourceRequests =
                mockServerRule.getRequests(RuntimeConfig.rumEndpointUrl)
                    .rumEventsBy { it.has("error") && it.get("type").asString == "error" }

            resourceRequests.verifyEventMatches(
                listOf(
                    ExpectedErrorEvent(
                        url = resourceUrl,
                        extraAttributes = extraAttributes,
                        isCrash = false,
                        source = ErrorSource.NETWORK
                    )
                )
            )
            true
        }.doWait(timeoutMs = RumTest.FINAL_WAIT_MS)
    }

    private fun List<HandledRequest>.rumEventsBy(
        predicate: (rawEvent: JsonObject) -> Boolean
    ): List<JsonObject> {
        return filter { it.url?.isRumUrl() ?: false }
            .flatMap { rumPayloadToJsonList(it.textBody!!) }
            .filter { predicate.invoke(it) }
    }
}
