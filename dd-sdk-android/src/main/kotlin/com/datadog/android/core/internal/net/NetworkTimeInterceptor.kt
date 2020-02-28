/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.core.internal.time.MutableTimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that will update a [MutableTimeProvider].
 *
 * This class uses the [Date] header in the response to update the time provider offset
 * (using an NTP like mechanism).
 *
 * @param timeProvider the [MutableTimeProvider] to update
 */
internal class NetworkTimeInterceptor(
    private val timeProvider: MutableTimeProvider
) : Interceptor {

    private val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

    // region Interceptor

    /**
     * Observes, modifies, or short-circuits requests going out and the responses coming back in.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val start = timeProvider.getDeviceTimestamp()
        val response = chain.proceed(request)
        val end = timeProvider.getDeviceTimestamp()

        val serverDateStr = response.header("date").orEmpty()
        val serverDate = try {
            formatter.parse(serverDateStr)
        } catch (e: ParseException) {
            sdkLogger.w("invalid date received \"$serverDateStr\"")
            null
        }

        if (serverDate != null) {
            sdkLogger.v("updating offset with server time $serverDate")
            val localTimestamp = (start + end) / 2
            val offset = serverDate.time - localTimestamp
            timeProvider.updateOffset(offset)
        }

        return response
    }

    // endregion
}
