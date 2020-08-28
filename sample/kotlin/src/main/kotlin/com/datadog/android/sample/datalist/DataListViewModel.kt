/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */
package com.datadog.android.sample.datalist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.datadog.android.sample.data.DataRepository
import com.datadog.android.sample.data.Result
import com.datadog.android.sample.data.model.Log

class DataListViewModel(val repository: DataRepository) : ViewModel(),
    DataRepository.Callback<List<Log>> {

    val data = mutableListOf<String>()
    val liveData = MutableLiveData<UIResponse>()

    fun onAddData() {
        data.add("Item ${data.size}")
        repository.getLogs("source:android", this)
    }

    fun observeLiveData(): LiveData<UIResponse> {
        return liveData
    }

    override fun onSuccess(result: Result.Success<List<Log>>) {
        liveData.value = UIResponse.Success(result.data)
    }

    override fun onFailure(failure: Result.Failure) {
        val errorMessage = failure.message ?: failure.throwable?.message ?: "Unknown error"
        liveData.value = UIResponse.Error(errorMessage)
    }

    sealed class UIResponse {
        class Success(val data: List<Log>) : UIResponse()
        class Error(val message: String) : UIResponse()
    }
}
