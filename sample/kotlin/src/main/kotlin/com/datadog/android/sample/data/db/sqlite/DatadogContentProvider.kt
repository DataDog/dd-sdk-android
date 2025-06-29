/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.sqlite

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.datadog.android.sample.data.db.DatadogDbContract
import com.datadog.android.trace.sqlite.transactionTraced

internal class DatadogContentProvider : ContentProvider() {

    lateinit var sqliteHelper: DatadogSqliteHelper
    private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(CONTENT_PROVIDER_AUTHORITY, LOGS_PATH, LOGS_MATCHER)
    }

    // region ContentProvider

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int {
        return when (uriMatcher.match(uri)) {
            LOGS_MATCHER -> {
                return insertLogs(sqliteHelper.writableDatabase, values)
            }
            else -> 0
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return when (uriMatcher.match(uri)) {
            LOGS_MATCHER -> {
                val db = sqliteHelper.readableDatabase
                return db.query(
                    DatadogDbContract.Logs.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
                )
            }
            else -> null
        }
    }

    override fun onCreate(): Boolean {
        context?.let {
            sqliteHelper = DatadogSqliteHelper(it)
        }
        return true
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return when (uriMatcher.match(uri)) {
            LOGS_MATCHER -> {
                val writableDatabase = sqliteHelper.writableDatabase
                writableDatabase.delete(
                    DatadogDbContract.Logs.TABLE_NAME,
                    selection,
                    selectionArgs
                )
            }
            else -> 0
        }
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    // endregion

    // region internal

    private fun insertLogs(db: SQLiteDatabase, contentValues: Array<out ContentValues>): Int {
        db. transactionTraced("Adding data to Logs DB") { database ->
            contentValues.forEach { value ->
                database.insertWithOnConflict(
                    DatadogDbContract.Logs.TABLE_NAME,
                    null,
                    value,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            setTag("logs_count", contentValues.size)
            return contentValues.size
        }
    }

    // endregion

    companion object {
        private const val CONTENT_PROVIDER_AUTHORITY = "com.datadog.android.provider"
        private const val LOGS_PATH = "logs"
        private const val LOGS_MATCHER = 1
        private const val SCHEME = "content://"
        val LOGS_URI = Uri.parse("$SCHEME$CONTENT_PROVIDER_AUTHORITY/$LOGS_PATH")
    }
}
