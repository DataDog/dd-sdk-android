/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import com.datadog.android.core.internal.data.Reader
import com.datadog.android.core.internal.data.Writer

internal interface LogStrategy {

    fun getLogWriter(): Writer

    fun getLogReader(): Reader

    companion object {

        // MAIN TAGS
        internal const val TAG_HOST = "host"
        internal const val TAG_MESSAGE = "message"
        internal const val TAG_STATUS = "status"
        internal const val TAG_SERVICE_NAME = "service"
        internal const val TAG_SOURCE = "source"
        internal const val TAG_DATE = "date"

        // COMMON TAGS
        internal const val TAG_DATADOG_TAGS = "ddtags"

        // ERROR TAGS
        internal const val TAG_ERROR_KIND = "error.kind"
        internal const val TAG_ERROR_MESSAGE = "error.message"
        internal const val TAG_ERROR_STACK = "error.stack"

        // THREAD RELATED TAGS
        internal const val TAG_LOGGER_NAME = "logger.name"
        internal const val TAG_THREAD_NAME = "logger.thread_name"

        // ANDROID SPECIFIC TAGS
        internal const val TAG_NETWORK_CONNECTIVITY = "network.client.connectivity"
        internal const val TAG_NETWORK_CARRIER_NAME = "network.client.sim_carrier.name"
        internal const val TAG_NETWORK_CARRIER_ID = "network.client.sim_carrier.id"

        internal val reservedAttributes = arrayOf(
            TAG_HOST,
            TAG_MESSAGE,
            TAG_STATUS,
            TAG_SERVICE_NAME,
            TAG_SOURCE,
            TAG_ERROR_KIND,
            TAG_ERROR_MESSAGE,
            TAG_ERROR_STACK,
            TAG_DATADOG_TAGS
        )
    }
}
