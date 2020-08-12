/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.datadog.android.sample.traces.TracesViewModel
import okhttp3.OkHttpClient

class ViewModelFactory(val okHttpClient: OkHttpClient) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass == TracesViewModel::class.java) {
            @Suppress("UNCHECKED_CAST")
            return TracesViewModel(okHttpClient) as T
        } else {
            return modelClass.newInstance()
        }
    }
}
