/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

internal interface LogStrategy {

    fun getLogWriter(): LogWriter

    fun getLogReader(): LogReader

    companion object {

        // MAIN TAGS
        internal const val TAG_HOST = "host"
        internal const val TAG_MESSAGE = "message"
        internal const val TAG_STATUS = "status"
        internal const val TAG_SERVICE_NAME = "service"
        internal const val TAG_SOURCE = "source" // TODO automatically set the source

        // COMMON TAGS
        internal const val TAG_DATADOG_TAGS = "ddtags"

        // ANDROID SPECIFIC TAGS
        internal const val TAG_USER_AGENT_SDK = "http.useragent_sdk"
        internal const val TAG_NETWORK_INFO = "networkinfo"
        internal const val TAG_DATE = "date"

        internal val reservedAttributes = arrayOf(
            TAG_HOST,
            TAG_MESSAGE,
            TAG_STATUS,
            TAG_SERVICE_NAME,
            TAG_SOURCE,
            TAG_USER_AGENT_SDK,
            TAG_DATADOG_TAGS
        )
    }
}
