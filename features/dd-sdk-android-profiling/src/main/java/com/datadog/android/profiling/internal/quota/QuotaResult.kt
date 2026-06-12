/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.internal.quota

internal data class QuotaResult(
    val decision: Decision,
    val reason: QuotaReason
) {
    internal enum class Decision { ALLOWED, DENIED }

    companion object {
        /***
         * Quota check timed out, allow profiling rather than stall app launch waiting for a
         * response.
         */
        val FAIL_OPEN = QuotaResult(Decision.ALLOWED, QuotaReason.TIMEOUT)

        /***
         * Quota endpoint unreachable or returned an unexpected error, allow profiling to avoid
         * silent data loss.
         */
        val API_ERROR = QuotaResult(Decision.ALLOWED, QuotaReason.API_ERROR)

        /***
         * Quota endpoint denied the session due to quota being exceeded.
         */
        val QUOTA_EXCEEDED = QuotaResult(Decision.DENIED, QuotaReason.QUOTA_EXCEEDED)
    }
}
