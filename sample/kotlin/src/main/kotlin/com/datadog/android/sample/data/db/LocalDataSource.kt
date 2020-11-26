/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db

import android.content.Context
import android.preference.PreferenceManager
import com.datadog.android.sample.data.db.realm.RealmDataSource
import com.datadog.android.sample.data.db.room.RoomDataSource
import com.datadog.android.sample.data.db.sqldelight.SqlDelightDataSource
import com.datadog.android.sample.data.db.sqlite.SQLiteDataSource
import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.datalist.DataSourceType
import io.reactivex.rxjava3.core.SingleSource
import java.lang.IllegalArgumentException

class LocalDataSource(
    val context: Context
) {

    private val dataSource: DataSource by lazy {
        val choice = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_DATA_SOURCE, null)

        val type = try {
            DataSourceType.valueOf(choice.orEmpty())
        } catch (e: IllegalArgumentException) {
            DataSourceType.ROOM
        }

        when (type) {
            DataSourceType.REALM -> RealmDataSource(context)
            DataSourceType.ROOM -> RoomDataSource(context)
            DataSourceType.SQLDELIGHT -> SqlDelightDataSource(context)
            DataSourceType.SQLITE -> SQLiteDataSource(context)
        }
    }

    fun fetchLogs(): SingleSource<List<Log>> {
        return dataSource.fetchLogs()
    }

    fun persistLogs(logs: List<Log>) {
        dataSource.persistLogs(logs)
    }

    fun getType(): DataSourceType {
        return dataSource.type
    }

    fun setType(type: DataSourceType) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(PREF_DATA_SOURCE, type.name)
            .apply()
    }

    companion object {
        private const val PREF_DATA_SOURCE = "_data_source"
    }
}
