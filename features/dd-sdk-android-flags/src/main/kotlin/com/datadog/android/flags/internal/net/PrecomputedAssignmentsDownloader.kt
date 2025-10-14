/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.model.EvaluationContext
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

/**
 * Downloads precomputed flag assignments from Datadog Feature Flags service.
 *
 * @param callFactory Factory for creating HTTP calls
 * @param internalLogger Logger for error and debug messages
 * @param flagsContext Context containing SDK configuration and authentication
 * @param requestFactory Factory for creating precomputed assignments requests
 */
internal class PrecomputedAssignmentsDownloader(
    private val callFactory: Call.Factory,
    private val internalLogger: InternalLogger,
    private val flagsContext: FlagsContext,
    private val requestFactory: PrecomputedAssignmentsRequestFactory
) : PrecomputedAssignmentsReader {
    override fun readPrecomputedFlags(context: EvaluationContext): String? {
        val request = requestFactory.create(context, flagsContext) ?: return null

        return executeDownloadRequest(request)
    }

    @Suppress("TooGenericExceptionCaught")
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
    } catch (e: Throwable) {
        internalLogger.log(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.MAINTAINER,
            { "Unexpected error while downloading flags" },
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
}
