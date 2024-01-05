/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.sqldelight

import android.content.Context
import com.datadog.android.sample.data.db.DataSource
import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.data.model.LogAttributes
import com.datadog.android.sample.datalist.DataSourceType
import com.datadog.android.sqldelight.transactionTraced
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import timber.log.Timber

internal class SqlDelightDataSource(val context: Context) : DataSource {

    private val logsDatabase = Database.getInstance(context)

    // region LocalDataSource

    override val type: DataSourceType = DataSourceType.SQLDELIGHT

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
        logsDatabase.logsQueries.purgeLogs(minTtlRequired)
        // add new data
        logsDatabase.logsQueries.transactionTraced("Adding data to Logs DB") {
            setTag("logs_count", logs.size)
            logs.forEach {
                logsDatabase.logsQueries.insertLog(
                    it.id,
                    it.attributes.message,
                    it.attributes.timestamp,
                    currentTimeInMillis
                )
            }
            afterCommit {
                Timber.d("All the logs were successfully persisted into local DB")
            }
        }
    }

    private val fetchLogsCallable = Callable<List<Log>> {
        val minTtlRequired =
            System.currentTimeMillis() - LOGS_EXPIRING_TTL_IN_MS
        logsDatabase.logsQueries.getLogs(minTtlRequired).executeAsList().map {
            Log(
                it._id,
                attributes = LogAttributes(it.message ?: "", it.timestamp ?: "")
            )
        }
    }

    // endregion

    companion object {
        val LOGS_EXPIRING_TTL_IN_MS = TimeUnit.HOURS.toMillis(2)
    }
}
