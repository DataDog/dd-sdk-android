/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.sample.data.DataRepository
import com.datadog.android.sample.data.db.LocalDataSource
import com.datadog.android.sample.data.remote.RemoteDataSource
import com.datadog.android.sample.datalist.DataListViewModel
import com.datadog.android.sample.traces.TracesViewModel
import okhttp3.OkHttpClient

internal class ViewModelFactory(
    private val okHttpClient: OkHttpClient,
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource
) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            TracesViewModel::class.java -> {
                TracesViewModel(okHttpClient) as T
            }
            DataListViewModel::class.java -> {
                DataListViewModel(
                    DataRepository(remoteDataSource, localDataSource)
                ) as T
            }
            else -> {
                modelClass.newInstance()
            }
        }
    }
}
