/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample.datalist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DataListViewModel : ViewModel() {

    val data = mutableListOf<String>()
    val liveData = MutableLiveData<List<String>>()

    fun onAddData() {
        data.add("Item ${data.size}")
        liveData.value = data.toList()
    }

    fun observeLiveData(): LiveData<List<String>> {
        return liveData
    }
}
