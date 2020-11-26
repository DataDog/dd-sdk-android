/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.BaseColumns
import com.datadog.android.sample.data.db.DataSource
import com.datadog.android.sample.data.db.DatadogDbContract
import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.data.model.LogAttributes
import com.datadog.android.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

class SQLiteDataSource(val context: Context) : DataSource {

    // region LocalDataSource

    override val type: DataSourceType = DataSourceType.SQLITE

    override fun persistLogs(logs: List<Log>) {
        insertLogs(logs)
    }

    override fun fetchLogs(): Single<List<Log>> {
        return Single.fromCallable(fetchLogsCallable).subscribeOn(Schedulers.io())
    }

    // endregion

    // region Internal

    private fun insertLogs(logs: List<Log>) {
        val currentTimeInMillis = System.currentTimeMillis()
        val minTtlRequired = currentTimeInMillis - LOGS_EXPIRING_TTL_IN_MS
        // purge data first
        purgeData(minTtlRequired)
        // add new data
        val contentValues = logs.map {
            ContentValues().apply {
                put(BaseColumns._ID, it.id)
                put(DatadogDbContract.Logs.COLUMN_NAME_MESSAGE, it.attributes.message)
                put(DatadogDbContract.Logs.COLUMN_NAME_TIMESTAMP, it.attributes.timestamp)
                put(DatadogDbContract.Logs.COLUMN_NAME_TTL, currentTimeInMillis)
            }
        }.toTypedArray()
        context.contentResolver.bulkInsert(DatadogContentProvider.LOGS_URI, contentValues)
    }

    private fun purgeData(minTtlRequired: Long) {
        context.contentResolver.delete(
            DatadogContentProvider.LOGS_URI,
            "${DatadogDbContract.Logs.COLUMN_NAME_TTL} <= ?",
            arrayOf(minTtlRequired.toString())
        )
    }

    private val fetchLogsCallable = object : Callable<List<Log>> {

        override fun call(): List<Log> {
            val columns = arrayOf(
                BaseColumns._ID,
                DatadogDbContract.Logs.COLUMN_NAME_MESSAGE,
                DatadogDbContract.Logs.COLUMN_NAME_TIMESTAMP,
                DatadogDbContract.Logs.COLUMN_NAME_TTL
            )
            val whereClause = "${DatadogDbContract.Logs.COLUMN_NAME_TTL} >= ?"
            val minTtlRequired =
                System.currentTimeMillis() - LOGS_EXPIRING_TTL_IN_MS
            val whereClauseArg = arrayOf(minTtlRequired.toString())
            val cursor =
                context.contentResolver.query(
                    DatadogContentProvider.LOGS_URI,
                    columns,
                    whereClause,
                    whereClauseArg,
                    null
                )
            return if (cursor != null) {
                fetchDataFromCursor(cursor)
            } else {
                emptyList()
            }
        }

        private fun fetchDataFromCursor(cursor: Cursor): List<Log> {
            val toReturn = mutableListOf<Log>()
            cursor.use {
                val idColumnIndex =
                    it.getColumnIndexOrThrow(BaseColumns._ID)
                val messageColumnIndex =
                    it.getColumnIndexOrThrow(DatadogDbContract.Logs.COLUMN_NAME_MESSAGE)
                val timestampColumnIndex =
                    it.getColumnIndexOrThrow(DatadogDbContract.Logs.COLUMN_NAME_TIMESTAMP)
                while (it.moveToNext()) {
                    val log = Log(
                        id = it.getString(idColumnIndex),
                        attributes = LogAttributes(
                            it.getString(messageColumnIndex),
                            it.getString(timestampColumnIndex)
                        )
                    )
                    toReturn.add(log)
                }
            }
            return toReturn.toList()
        }
    }

    // endregion

    companion object {
        val LOGS_EXPIRING_TTL_IN_MS = TimeUnit.HOURS.toMillis(2)
    }
}
