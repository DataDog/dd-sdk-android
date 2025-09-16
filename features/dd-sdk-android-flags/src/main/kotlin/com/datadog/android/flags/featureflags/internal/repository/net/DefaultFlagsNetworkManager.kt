/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.ProviderContext
import com.datadog.android.flags.internal.model.FlagsContext
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

internal class DefaultFlagsNetworkManager(
    private val internalLogger: InternalLogger,
    private val flagsContext: FlagsContext,
    private val endpointsHelper: EndpointsHelper = EndpointsHelper(internalLogger)
) : FlagsNetworkManager {
    internal lateinit var callFactory: OkHttpCallFactory

    internal class OkHttpCallFactory(factory: () -> OkHttpClient) : Call.Factory {
        val okhttpClient by lazy(factory)

        override fun newCall(request: Request): Call {
            return okhttpClient.newCall(request)
        }
    }

    init {
        setupOkHttpClient()
    }

    override fun downloadPrecomputedFlags(context: ProviderContext): String? {
        val url = buildUrl() ?: return null
        val headers = buildHeaders()
        val body = buildRequestBody()
        return download(url = url, headers = headers, body = body.toRequestBody())
    }

    @Suppress("TooGenericExceptionCaught")
    private fun download(
        url: String,
        headers: Headers,
        body: RequestBody
    ): String? {
        val request = try {
            Request.Builder()
                .url(url)
                .headers(headers)
                .post(body)
                .build()
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                listOf(InternalLogger.Target.MAINTAINER),
                { "Unable to create the request" },
                e
            )
            return null
        }

        return try {
            executeDownloadRequest(request)
        } catch (e: UnknownHostException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to find host for site ${flagsContext.site}; we will retry later." },
                e
            )
            null
        } catch (e: IOException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to execute the request; we will retry later." },
                e
            )
            null
        } catch (e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to execute the request; we will retry later." },
                e
            )
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun executeDownloadRequest(request: Request): String? {
        return try {
            callFactory.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.string()
                } else {
                    internalLogger.log(
                        InternalLogger.Level.ERROR,
                        InternalLogger.Target.MAINTAINER,
                        { "Failed to download flags: ${response.code}" }
                    )
                    null
                }
            }
        } catch (e: Exception) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Error downloading flags" },
                e
            )
            null
        }
    }

    private fun buildUrl(): String? {
        val baseUrl = endpointsHelper.buildEndpointHost(site = flagsContext.site)
        return if (baseUrl.isNotEmpty()) {
            "https://$baseUrl$FLAGS_ENDPOINT"
        } else {
            internalLogger.log(
                level = InternalLogger.Level.ERROR,
                target = InternalLogger.Target.MAINTAINER,
                messageBuilder = { ERROR_FAILURE_BUILDING_URL }
            )

            null
        }
    }

    private fun buildHeaders(): Headers {
        val headersBuilder = Headers.Builder()

        headersBuilder
            .add(HEADER_CLIENT_TOKEN, flagsContext.clientToken)
            .add(HEADER_CONTENT_TYPE, CONTENT_TYPE_VND_JSON)

        flagsContext.applicationId?.let {
            headersBuilder.add(HEADER_APPLICATION_ID, it)
        }

        headersBuilder.build()

        return headersBuilder.build()
    }

    private fun buildRequestBody(): String {
        val stringifiedContext = buildStringifiedContext()

        val subject = JSONObject()
            .put("targeting_key", flagsContext.targetingKey)
            .put("targeting_attributes", stringifiedContext)
        val env = buildEnvPayload()
        val attributes = JSONObject()
            .put("env", env)
            .put("subject", subject)
        val data = JSONObject()
            .put("type", "precompute-assignments-request")
            .put("attributes", attributes)
        val body = JSONObject()
            .put("data", data)
        return body.toString()
    }

    private fun buildStringifiedContext(): JSONObject {
        // TODO replace
        return JSONObject()
            .put("attr1", "value1")
            .put("companyId", "1")
    }

    private fun buildEnvPayload(): JSONObject {
        val payload = JSONObject()
        payload.put("name", "prod")
        payload.put("dd_env", "prod")
        return payload
    }

    private fun setupOkHttpClient() {
        callFactory = OkHttpCallFactory {
            val builder = OkHttpClient.Builder()
            builder.callTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))

            builder.build()
        }
    }

    companion object {
        private const val HEADER_APPLICATION_ID = "dd-application-id"
        private const val HEADER_CLIENT_TOKEN = "dd-client-token"
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val CONTENT_TYPE_VND_JSON = "application/vnd.api+json"
        private const val FLAGS_ENDPOINT = "/precompute-assignments"
        private val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
        private const val ERROR_FAILURE_BUILDING_URL = "Failed to build url"
    }
}
