/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.sample.data.DataRepository
import com.datadog.android.sample.data.RemoteDataSource
import com.datadog.android.sample.datalist.DataListViewModel
import com.datadog.android.sample.traces.TracesViewModel
import okhttp3.OkHttpClient

class ViewModelFactory(val okHttpClient: OkHttpClient, val remoteDataSource: RemoteDataSource) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass == TracesViewModel::class.java) {
            return TracesViewModel(okHttpClient) as T
        } else if (modelClass == DataListViewModel::class.java) {
            return DataListViewModel(DataRepository(remoteDataSource)) as T
        } else {
            return modelClass.newInstance()
        }
    }
}
