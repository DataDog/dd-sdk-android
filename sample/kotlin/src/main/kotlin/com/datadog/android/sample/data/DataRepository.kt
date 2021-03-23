/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data

import com.datadog.android.sample.data.db.LocalDataSource
import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.data.remote.RemoteDataSource
import com.datadog.android.sample.datalist.DataSourceType
import io.opentracing.util.GlobalTracer
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

class DataRepository(
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource
) {

    @Suppress("SimpleRedundantLet")
    fun getLogs(query: String): Flowable<List<Log>> {
        return Single.concat(
            localDataSource.fetchLogs(),
            remoteDataSource.getLogs(query)
                .subscribeOn(Schedulers.io())
                .map { it.data }
                .doOnSuccess {
                    localDataSource.persistLogs(it)
                }
                .doOnSubscribe {
                    val span = GlobalTracer.get()
                        .buildSpan("Fetch recent logs")
                        .start()
                    GlobalTracer.get().scopeManager().activate(span)
                }
                .doOnTerminate {
                    GlobalTracer.get().scopeManager().activeSpan()?.let {
                        it.finish()
                    }
                }
        )
    }

    fun getDataSource(): DataSourceType {
        return localDataSource.getType()
    }

    fun setDataSource(type: DataSourceType) {
        localDataSource.setType(type)
    }
}
