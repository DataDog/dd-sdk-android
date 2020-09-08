/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data

import com.datadog.android.sample.data.model.LogsCollection
import io.reactivex.rxjava3.core.Single

class DataRepository(private val remoteDataSource: RemoteDataSource) {

    fun getLogs(query: String): Single<LogsCollection> {
        return remoteDataSource.getLogs(query)
    }
}
