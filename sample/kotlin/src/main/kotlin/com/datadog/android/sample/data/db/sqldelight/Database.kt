/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.sqldelight

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.datadog.android.sample.LogsDatabase
import com.datadog.android.sqldelight.DatadogSqliteCallback

internal object Database {

    @Volatile
    private var INSTANCE: LogsDatabase? = null

    fun getInstance(context: Context): LogsDatabase =
        INSTANCE ?: synchronized(this) {
            INSTANCE ?: setupDb(context).also { INSTANCE = it }
        }

    private fun setupDb(context: Context): LogsDatabase {
        return LogsDatabase(
            AndroidSqliteDriver(
                LogsDatabase.Schema,
                context,
                callback = DatadogSqliteCallback(LogsDatabase.Schema)
            )
        )
    }
}
