/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.realm

import android.content.Context
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.sample.data.db.DataSource
import com.datadog.android.sample.data.db.DatadogDbContract
import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.data.model.LogAttributes
import com.datadog.android.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.UpdatePolicy
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit

internal class RealmDataSource(val context: Context) : DataSource {

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
        Realm.open().use {
            writeBlocking {
                logs.map {
                    LogRealm(
                        id = it.id,
                        message = it.attributes.message,
                        timestamp = it.attributes.timestamp,
                        ttl = currentTimeInMillis
                    )
                }.forEach {
                    copyToRealm(it, UpdatePolicy.ALL)
                }
            }
        }
    }

    private val fetchLogsCallable = Callable {
        Realm.open().use {
            val minTtlRequired =
                System.currentTimeMillis() - LOGS_EXPIRING_TTL_IN_MS
            query(LogRealm::class, "${DatadogDbContract.Logs.COLUMN_NAME_TTL} >= $0", minTtlRequired)
                .find()
                .map {
                    copyFromRealm(it)
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
        Realm.open().use {
            writeBlocking {
                query(
                    LogRealm::class,
                    "${DatadogDbContract.Logs.COLUMN_NAME_TTL} < $0",
                    minTtlRequired
                )
                    .find()
                    .forEach {
                        delete(it)
                    }
            }
        }
    }

    // endregion

    private fun Realm.Companion.open(): Realm {
        val realmConfiguration = RealmConfiguration.create(schema = setOf(LogRealm::class))
        return open(realmConfiguration)
    }

    private fun <T> Realm.use(block: Realm.() -> T): T {
        try {
            return block(this)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            handleRealmException(e)
            throw e
        } finally {
            try {
                close()
            } catch (@Suppress("TooGenericExceptionCaught") closeException: Exception) {
                handleRealmException(closeException)
            }
        }
    }

    private fun handleRealmException(exception: Exception) {
        GlobalRumMonitor.get().addError(
            "Error while working with Realm",
            RumErrorSource.SOURCE,
            exception,
            emptyMap()
        )
    }

    companion object {
        val LOGS_EXPIRING_TTL_IN_MS = TimeUnit.HOURS.toMillis(2)
    }
}
