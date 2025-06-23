/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.core.feature.event.ThreadDump
import com.datadog.android.log.model.LogEvent
import com.datadog.tools.annotation.NoOpImplementation

@NoOpImplementation
internal interface LogGenerator {

    @Suppress("LongParameterList")
    fun generateLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        datadogContext: DatadogContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean = true,
        bundleWithRum: Boolean = true,
        userInfo: UserInfo? = null,
        accountInfo: AccountInfo? = null,
        networkInfo: NetworkInfo? = null,
        threads: List<ThreadDump> = emptyList()
    ): LogEvent?

    @Suppress("LongParameterList")
    fun generateLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStack: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        datadogContext: DatadogContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean = true,
        bundleWithRum: Boolean = true,
        userInfo: UserInfo? = null,
        accountInfo: AccountInfo? = null,
        networkInfo: NetworkInfo? = null
    ): LogEvent?
}
