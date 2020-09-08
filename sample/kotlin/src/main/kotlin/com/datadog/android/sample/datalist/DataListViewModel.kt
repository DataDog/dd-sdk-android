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
import com.datadog.android.sample.data.model.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject

class DataListViewModel(val repository: DataRepository) : ViewModel() {

    val uiRequestSubject: PublishSubject<UIRequest> = PublishSubject.create()
    var disposable: Disposable? = null
    val performRequestObservable: Observable<UIResponse> =
        uiRequestSubject
            .switchMap { request ->
                when (request) {
                    is UIRequest.FetchData -> repository.getLogs("source:android")
                        .toObservable()
                        .map<UIResponse> {
                            val data = it.data
                            UIResponse.Success(data)
                        }
                        .onErrorReturn {
                            UIResponse.Error(it.message ?: "Unknown Error")
                        }
                }
            }
    val data = mutableListOf<String>()
    val liveData = MutableLiveData<UIResponse>()

    fun performRequest(request: UIRequest) {
        if (disposable == null) {
            disposable = performRequestObservable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        val data = it
                        liveData.value = data
                    }
        }
        uiRequestSubject.onNext(request)
    }

    fun observeLiveData(): LiveData<UIResponse> {
        return liveData
    }

    sealed class UIResponse {
        class Success(val data: List<Log>) : UIResponse()
        class Error(val message: String) : UIResponse()
    }

    sealed class UIRequest() {
        object FetchData : UIRequest()
    }
}
