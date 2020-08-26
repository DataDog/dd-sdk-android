/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sample.data

import com.datadog.android.sample.data.model.Log
import com.datadog.android.sample.data.model.LogsCollection
import retrofit2.Call
import retrofit2.Response

class DataRepository(private val remoteDataSource: RemoteDataSource) {

    fun getLogs(query: String, callback: Callback<List<Log>>) {
        val call = remoteDataSource.getLogs(query)
        call.enqueue(object : retrofit2.Callback<LogsCollection> {

            override fun onFailure(call: Call<LogsCollection>, t: Throwable) {
                callback.onFailure(Result.Failure(throwable = t))
            }

            override fun onResponse(
                call: Call<LogsCollection>,
                response: Response<LogsCollection>
            ) {
                handleResponse(response)
            }

            private fun handleResponse(response: Response<LogsCollection>) {
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    callback.onSuccess(Result.Success(body.data))
                } else {
                    callback.onFailure(
                        Result.Failure(resolveErrorMessage(response))
                    )
                }
            }

            private fun resolveErrorMessage(response: Response<LogsCollection>): String? {
                return response.errorBody()?.string() ?: "Request (${call.request().url()
                    .url().path}) failed (${response.code()})"
            }
        })
    }

    interface Callback<T> {

        fun onSuccess(result: Result.Success<T>)

        fun onFailure(failure: Result.Failure)
    }
}
