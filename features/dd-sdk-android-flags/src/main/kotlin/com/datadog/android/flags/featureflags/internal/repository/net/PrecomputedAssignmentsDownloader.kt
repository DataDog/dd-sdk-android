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
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * This class follows the Factory + Executor pattern used by DataOkHttpUploader:
 * - Request construction is delegated to PrecomputedAssignmentsRequestFactory
 * - This class focuses solely on request execution and response handling
 *
 * @param internalLogger Logger for error and debug messages
 * @param flagsContext Context containing SDK configuration and authentication
 * @param requestFactory Factory for creating precomputed assignments requests
 */
internal class PrecomputedAssignmentsDownloader(
    private val internalLogger: InternalLogger,
    private val flagsContext: FlagsContext,
    private val requestFactory: PrecomputedAssignmentsRequestFactory
) : FlagsNetworkManager {

    internal lateinit var callFactory: OkHttpCallFactory

    internal class OkHttpCallFactory(factory: () -> OkHttpClient) : Call.Factory {
        val okhttpClient by lazy(factory)

        override fun newCall(request: Request): Call = okhttpClient.newCall(request)
    }

    init {
        setupOkHttpClient()
    }

    /**
     * Downloads precomputed flag assignments for the given evaluation context.
     *
     * This method follows the Factory + Executor pattern:
     * 1. Create request using factory (handles request building)
     * 2. Execute request (handles network operations)
     *
     * @param context The evaluation context for flag evaluation
     * @return The response body as a string, or null if download fails
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun downloadPrecomputedFlags(context: EvaluationContext): String? {
        // Step 1: Create request using factory
        val request = requestFactory.create(context, flagsContext) ?: run {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to create the request for precomputed flags" }
            )
            return null
        }

        // Step 2: Execute request with proper error handling
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
        private val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
    }
}
