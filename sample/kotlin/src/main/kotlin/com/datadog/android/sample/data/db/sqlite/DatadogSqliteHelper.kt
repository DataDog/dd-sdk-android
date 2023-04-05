/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.sqlite

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import com.datadog.android.Datadog
import com.datadog.android.sample.data.db.DatadogDbContract
import com.datadog.android.sqlite.DatadogDatabaseErrorHandler

internal class DatadogSqliteHelper(context: Context) :
    SQLiteOpenHelper(
        context,
        DatadogDbContract.DB_NAME,
        null,
        DatadogDbContract.DB_VERSION,
        DatadogDatabaseErrorHandler({ Datadog.getInstance() })
    ) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(CREATE_DB_QUERY)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL(DELETE_DB_QUERY)
        onCreate(db)
    }

    companion object {

        const val CREATE_DB_QUERY =
            """
            CREATE TABLE IF NOT EXISTS ${DatadogDbContract.Logs.TABLE_NAME}
            (${BaseColumns._ID} TEXT PRIMARY KEY,
            ${DatadogDbContract.Logs.COLUMN_NAME_MESSAGE} TEXT,
            ${DatadogDbContract.Logs.COLUMN_NAME_TIMESTAMP} TEXT,
            ${DatadogDbContract.Logs.COLUMN_NAME_TTL} INTEGER
            )
            """
        const val DELETE_DB_QUERY =
            """
            DROP TABLE IF EXISTS ${DatadogDbContract.Logs.TABLE_NAME}
            """
    }
}
