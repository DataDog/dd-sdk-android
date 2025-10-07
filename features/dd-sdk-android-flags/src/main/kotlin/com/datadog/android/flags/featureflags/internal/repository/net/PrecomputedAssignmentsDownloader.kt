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
import java.util.concurrent.TimeUnit

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param sdkCore SDK core for accessing shared HTTP client infrastructure
 * @param internalLogger Logger for error and debug messages
 * @param flagsContext Context containing SDK configuration and authentication
 * @param requestFactory Factory for creating precomputed assignments requests
 */
internal class PrecomputedAssignmentsDownloader(
    sdkCore: com.datadog.android.core.InternalSdkCore,
    private val internalLogger: InternalLogger,
    private val flagsContext: FlagsContext,
    private val requestFactory: PrecomputedAssignmentsRequestFactory
) : FlagsNetworkManager {

    private val callFactory: Call.Factory = sdkCore.createOkHttpCallFactory {
        callTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        writeTimeout(NETWORK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    override fun downloadPrecomputedFlags(context: EvaluationContext): String? {
        val request = requestFactory.create(context, flagsContext) ?: run {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unable to create the request for precomputed flags" }
            )
            return null
        }

        return try {
            executeDownloadRequest(request)
        } catch (e: Throwable) {
            internalLogger.log(
                InternalLogger.Level.ERROR,
                InternalLogger.Target.MAINTAINER,
                { "Unexpected error executing request." },
                e
            )
            null
        }
    }

    private fun executeDownloadRequest(request: Request): String? = try {
        val response = callFactory.newCall(request).execute()
        @Suppress("UnsafeThirdPartyFunctionCall") // wrapped in try/catch
        handleResponse(response)
    } catch (e: IllegalStateException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Invalid state while downloading flags" },
            e
        )
        null
    } catch (e: IllegalArgumentException) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Invalid argument while downloading flags" },
            e
        )
        null
    }

    private fun handleResponse(response: Response): String? = if (response.isSuccessful) {
        response.body?.string()
    } else {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Failed to download flags: ${response.code}" }
        )

        null
    }

    companion object {
        private val NETWORK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(45)
    }
}
