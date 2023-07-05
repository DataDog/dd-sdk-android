/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.realm

import android.content.Context
import com.datadog.android.ktx.rum.useMonitored
import com.datadog.android.sample.data.db.DataSource
import com.datadog.android.sample.data.db.DatadogDbContract
import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.data.model.LogAttributes
import com.datadog.android.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.realm.Realm
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

internal class RealmDataSource(val context: Context) : DataSource {

    init {
        RealmFeature.initialise(context)
    }

    // region LocalDataSource

    override val type: DataSourceType = DataSourceType.REALM

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
        purgeLogs(minTtlRequired)
        // add new data
        Realm.getDefaultInstance().useMonitored { realm ->
            realm.beginTransaction()
            realm.insertOrUpdate(
                logs.map {
                    LogRealm(
                        id = it.id,
                        message = it.attributes.message,
                        timestamp = it.attributes.timestamp,
                        ttl = currentTimeInMillis
                    )
                }
            )
            realm.commitTransaction()
        }
    }

    private val fetchLogsCallable = Callable {
        Realm.getDefaultInstance().useMonitored { realm ->
            val minTtlRequired =
                System.currentTimeMillis() - LOGS_EXPIRING_TTL_IN_MS
            realm.where(LogRealm::class.java)
                .greaterThanOrEqualTo(DatadogDbContract.Logs.COLUMN_NAME_TTL, minTtlRequired)
                .findAll()
                .map {
                    realm.copyFromRealm(it)
                }
                .map {
                    Log(
                        id = it.id,
                        attributes = LogAttributes(message = it.message, timestamp = it.timestamp)
                    )
                }
        }
    }

    private fun purgeLogs(minTtlRequired: Long) {
        Realm.getDefaultInstance().useMonitored { realm ->
            realm.beginTransaction()
            realm.where(LogRealm::class.java)
                .lessThan(DatadogDbContract.Logs.COLUMN_NAME_TTL, minTtlRequired)
                .findAll()
                .deleteAllFromRealm()
            realm.commitTransaction()
        }
    }

    // endregion

    companion object {
        val LOGS_EXPIRING_TTL_IN_MS = TimeUnit.HOURS.toMillis(2)
    }
}
