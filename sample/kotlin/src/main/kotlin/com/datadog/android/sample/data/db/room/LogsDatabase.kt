/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.datadog.android.sample.data.db.DatadogDbContract

@Database(entities = [LogRoom::class], version = DatadogDbContract.DB_VERSION, exportSchema = false)
abstract class LogsDatabase : RoomDatabase() {

    abstract fun logDao(): LogDao

    companion object {

        @Volatile
        private var INSTANCE: LogsDatabase? = null

        fun getInstance(context: Context): LogsDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context): LogsDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LogsDatabase::class.java,
                DatadogDbContract.DB_NAME
            ).build()
    }
}
