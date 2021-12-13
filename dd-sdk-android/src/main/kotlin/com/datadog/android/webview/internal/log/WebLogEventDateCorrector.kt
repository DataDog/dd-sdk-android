/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.webview.internal.log

import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.sdkLogger
import com.datadog.android.log.internal.utils.buildLogDateFormat
import java.lang.IllegalArgumentException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

internal class WebLogEventDateCorrector(
    private val timeProvider: TimeProvider,
    private val simpleDateFormat: SimpleDateFormat =
        buildLogDateFormat()
) {

    fun correctDate(date: String): String? {
        return correctLogDate(date)
    }

    private fun correctLogDate(date: String): String? {

        synchronized(simpleDateFormat) {
            return try {
                val dateAsTimeInMillis = resolveTimeFromDate(date) ?: return null
                simpleDateFormat.format(dateAsTimeInMillis + timeProvider.getServerOffsetMillis())
            } catch (e: IllegalArgumentException) {
                sdkLogger.e(OFFSET_CORRECTION_ERROR_MESSAGE, e)
                null
            } catch (@SuppressWarnings("TooGenericExceptionCaught") e: NullPointerException) {
                sdkLogger.e(OFFSET_CORRECTION_ERROR_MESSAGE, e)
                null
            }
        }
    }

    private fun resolveTimeFromDate(date: String): Long? {
        return try {
            simpleDateFormat.parse(date)?.time
        } catch (e: ParseException) {
            sdkLogger.e(DATE_PARSING_ERROR_MESSAGE.format(Locale.US, date), e)
            null
        } catch (@SuppressWarnings("TooGenericExceptionCaught") e: NullPointerException) {
            sdkLogger.e(DATE_PARSING_ERROR_MESSAGE.format(Locale.US, date), e)
            null
        }
    }

    companion object {
        const val DATE_PARSING_ERROR_MESSAGE = "Date value: [%s] in the web " +
            "log event has a broken format"
        const val OFFSET_CORRECTION_ERROR_MESSAGE = "Could not apply the offset correction"
    }
}
