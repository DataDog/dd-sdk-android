/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.remote.config

import androidx.annotation.WorkerThread
import com.datadog.android.internal.utils.DDCoreResult
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request
import java.io.IOException

internal sealed interface RemoteConfigError {

    data class ServerError(val code: Int) : RemoteConfigError

    data class ClientError(val code: Int) : RemoteConfigError

    data class IOError(val exception: IOException) : RemoteConfigError

    data class UnknownError(val exception: Exception) : RemoteConfigError
}

internal interface RemoteConfigNetworkService {

    @WorkerThread
    fun fetch(url: HttpUrl): DDCoreResult<String, RemoteConfigError>
}

internal class RemoteConfigNetworkServiceImpl(
    private val callFactory: Call.Factory
) : RemoteConfigNetworkService {

    @WorkerThread
    @Suppress("TooGenericExceptionCaught", "UnsafeThirdPartyFunctionCall", "MagicNumber")
    override fun fetch(url: HttpUrl): DDCoreResult<String, RemoteConfigError> {
        return try {
            val request = Request.Builder()
                .url(url)
                .get()
                .build()
            callFactory.newCall(request).execute().use { response ->
                when (response.code) {
                    in 500..599 -> DDCoreResult.Error(RemoteConfigError.ServerError(response.code))
                    in 400..499 -> DDCoreResult.Error(RemoteConfigError.ClientError(response.code))
                    else -> DDCoreResult.Result(response.body?.string().orEmpty())
                }
            }
        } catch (e: IOException) {
            DDCoreResult.Error(RemoteConfigError.IOError(e))
        } catch (e: Exception) {
            DDCoreResult.Error(RemoteConfigError.UnknownError(e))
        }
    }
}
