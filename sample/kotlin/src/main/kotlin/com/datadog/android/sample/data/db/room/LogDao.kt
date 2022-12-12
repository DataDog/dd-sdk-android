/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data.db.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface LogDao {

    @Query(value = "SELECT * FROM logs where ttl >= :minTtl")
    fun getAll(minTtl: Long): List<LogRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(logs: List<LogRoom>)

    @Query("DELETE FROM logs where ttl < :minTtl")
    fun purge(minTtl: Long)
}
