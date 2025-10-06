/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.model.EvaluationContext
import okhttp3.Call
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

internal class DefaultFlagsNetworkManager(
    private val internalLogger: InternalLogger,
    private val flagsContext: FlagsContext,
    private val endpointsHelper: EndpointsHelper = EndpointsHelper(flagsContext, internalLogger)
) : FlagsNetworkManager {
    internal lateinit var callFactory: OkHttpCallFactory

    internal class OkHttpCallFactory(factory: () -> OkHttpClient) : Call.Factory {
        val okhttpClient by lazy(factory)

        override fun newCall(request: Request): Call = okhttpClient.newCall(request)
    }

    init {
        setupOkHttpClient()
    }

    @Suppress("ReturnCount")
    override fun downloadPrecomputedFlags(context: EvaluationContext): String? {
        val url = endpointsHelper.getFlaggingEndpoint() ?: return null
        val headers = buildHeaders()
        val body = buildRequestBody(context) ?: return null
        return download(url = url, headers = headers, body = body)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun download(url: String, headers: Headers, body: RequestBody): String? {
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
                { "Unable to find host ${flagsContext.site.intakeEndpoint}; we will retry later." },
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
    private fun executeDownloadRequest(request: Request): String? = try {
        val response = callFactory.newCall(request).execute()
        @Suppress("UnsafeThirdPartyFunctionCall") // wrapped in try/catch
        handleResponse(response)
    } catch (e: Exception) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Error downloading flags" },
            e
        )
        null
    }

    private fun handleResponse(response: Response): String? = if (response.isSuccessful) {
        val body = response.body
        var responseBodyToReturn: String? = null

        if (body != null) {
            responseBodyToReturn = body.string()
        }

        responseBodyToReturn
    } else {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to download flags: ${response.code}" }
        )

        null
    }

    private fun buildHeaders(): Headers {
        val headersBuilder = Headers.Builder()

        try {
            headersBuilder
                .add(HEADER_CLIENT_TOKEN, flagsContext.clientToken)
                .add(HEADER_CONTENT_TYPE, CONTENT_TYPE_VND_JSON)

            flagsContext.applicationId?.let {
                headersBuilder.add(HEADER_APPLICATION_ID, it)
            }
        } catch (e: IllegalArgumentException) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Failed to build HTTP headers: invalid header values" },
                e
            )
        }

        return headersBuilder.build()
    }

    @Suppress("TodoWithoutTask")
    // TODO modify to real fields
    private fun buildRequestBody(context: EvaluationContext): RequestBody? = try {
        val attributeObj = buildStringifiedAttributes(context)

        val subject = JSONObject()
            .put("targeting_key", context.targetingKey)
            .put("targeting_attributes", attributeObj)
        val env = buildEnvPayload()
        val attributes = JSONObject()
            .put("env", env)
            .put("subject", subject)
        val data = JSONObject()
            .put("type", "precompute-assignments-request")
            .put("attributes", attributes)
        val body = JSONObject()
            .put("data", data)

        // String.toRequestBody() can internally throw IOException/ArrayIndexOutOfBoundsException,
        // but not in this context with a valid JSON string from JSONObject.toString()
        @Suppress("UnsafeThirdPartyFunctionCall")
        body.toString().toRequestBody()
    } catch (e: JSONException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to create request body: JSON error" },
            e
        )
        null
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // call wrapped in try/catch
    private fun buildStringifiedAttributes(context: EvaluationContext): JSONObject {
        val contextJson = JSONObject()
        context.attributes.forEach { (key, value) ->
            contextJson.put(key, value)
        }
        return contextJson
    }

    @Suppress("UnsafeThirdPartyFunctionCall") // call wrapped in try/catch
    private fun buildEnvPayload(): JSONObject =
        JSONObject()
            .put("dd_env", "prod")

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
        private val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
    }
}
