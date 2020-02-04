/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.log.internal.user.UserInfo

/**
 * Represents a Log before it is persisted and sent to Datadog servers.
 */
internal data class Log(
    val serviceName: String,
    val level: Int,
    val message: String,
    val timestamp: Long,
    val attributes: Map<String, Any?>,
    val tags: List<String>,
    val throwable: Throwable?,
    val networkInfo: NetworkInfo?,
    val userInfo: UserInfo,
    val loggerName: String,
    val threadName: String,
    val traceId: String? = null,
    val spanId: String? = null
) {

    companion object {
        internal const val CRASH: Int = 9
    }
}
