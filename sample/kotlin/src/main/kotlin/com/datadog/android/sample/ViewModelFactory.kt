/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.ndk.tracer.NdkTracer
import com.datadog.android.sample.data.DataRepository
import com.datadog.android.sample.data.db.LocalDataSource
import com.datadog.android.sample.data.remote.RemoteDataSource
import com.datadog.android.sample.datalist.DataListViewModel
import com.datadog.android.sample.traces.OtelTracesViewModel
import com.datadog.android.sample.traces.TracesViewModel
import com.datadog.android.sample.webview.WebViewModel
import com.datadog.android.vendor.sample.LocalServer
import okhttp3.OkHttpClient

internal class ViewModelFactory(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val remoteDataSource: RemoteDataSource,
    private val localDataSource: LocalDataSource,
    private val localServer: LocalServer
) :
    ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            TracesViewModel::class.java -> {
                TracesViewModel(okHttpClient, localServer) as T
            }
            DataListViewModel::class.java -> {
                DataListViewModel(
                    DataRepository(remoteDataSource, localDataSource)
                ) as T
            }
            WebViewModel::class.java -> {
                WebViewModel(localServer) as T
            }
            OtelTracesViewModel::class.java -> {
                OtelTracesViewModel(okHttpClient, localServer, NdkTracer(context.filesDir)) as T
            }
            else -> {
                modelClass.newInstance()
            }
        }
    }
}
