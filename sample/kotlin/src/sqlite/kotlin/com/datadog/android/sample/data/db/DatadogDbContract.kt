/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db

internal object DatadogDbContract {

    const val DB_NAME = "Datadog.db"
    const val DB_VERSION = 1

    object Logs {
        const val TABLE_NAME = "logs"
        const val COLUMN_NAME_MESSAGE = "message"
        const val COLUMN_NAME_TIMESTAMP = "timestamp"
        const val COLUMN_NAME_TTL = "ttl"
    }
}
