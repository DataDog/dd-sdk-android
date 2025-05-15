/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.utils.io.errors.IOException

internal sealed interface KtorHttpResponse<out T : Any> {
    data class Success<T : Any>(val result: T) : KtorHttpResponse<T>
    data class IOError(val exception: IOException) : KtorHttpResponse<Nothing>
    data class UnknownException(val exception: Exception) : KtorHttpResponse<Nothing>
    data class ServerError(val code: HttpStatusCode): KtorHttpResponse<Nothing>

    val optionalResult: T? get() = (this as? Success)?.result
}

internal suspend inline fun <reified T: Any> HttpClient.safeGet(url: Url): KtorHttpResponse<T> {
    return try {
        val response = get(url)
        KtorHttpResponse.Success(response.body())
    } catch (e: ServerResponseException) {
        KtorHttpResponse.ServerError(e.response.status)
    } catch (e: ClientRequestException) {
        KtorHttpResponse.UnknownException(e)
    } catch (e: IOException) {
        KtorHttpResponse.IOError(e)
    } catch (e: Exception) {
        KtorHttpResponse.UnknownException(e)
    }
}
