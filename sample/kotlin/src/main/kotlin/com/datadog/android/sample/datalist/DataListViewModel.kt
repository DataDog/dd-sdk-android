/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.sample.datalist

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.datadog.android.Datadog
import com.datadog.android.rx.sendErrorToDatadog
import com.datadog.android.sample.data.DataRepository
import com.datadog.android.sample.data.model.Log
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.subjects.PublishSubject

internal class DataListViewModel(val repository: DataRepository) : ViewModel() {

    private val uiRequestSubject: PublishSubject<UIRequest> = PublishSubject.create()
    private var disposable: Disposable? = null
    private val performRequestObservable: Observable<UIResponse> =
        uiRequestSubject
            .switchMap { request ->
                when (request) {
                    is UIRequest.FetchData -> {
                        val flowable = repository.getLogs("source:android")
                        flowable.sendErrorToDatadog(Datadog.getInstance())
                        flowable.toObservable()
                            .map<UIResponse> {
                                UIResponse.Success(it)
                            }
                            .onErrorReturn {
                                UIResponse.Error(it.message ?: "Unknown Error")
                            }
                    }
                }
            }
    private val liveData = MutableLiveData<UIResponse>()

    fun performRequest(request: UIRequest) {
        if (disposable == null) {
            disposable = performRequestObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    liveData.value = it
                }
        }
        uiRequestSubject.onNext(request)
    }

    fun observeLiveData(): LiveData<UIResponse> {
        return liveData
    }

    fun getDataSource(): DataSourceType {
        return repository.getDataSource()
    }

    fun selectDataSource(type: DataSourceType) {
        repository.setDataSource(type)
    }

    sealed class UIResponse {
        class Success(val data: List<Log>) : UIResponse()
        class Error(val message: String) : UIResponse()
    }

    sealed class UIRequest {
        object FetchData : UIRequest()
    }
}
