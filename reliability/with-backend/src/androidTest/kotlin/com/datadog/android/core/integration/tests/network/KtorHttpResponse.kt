/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.integration.tests.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import java.io.IOException

internal sealed interface KtorHttpResponse<out T : Any> {
    data class Success<T : Any>(val result: T) : KtorHttpResponse<T>
    data class IOError(val exception: IOException) : KtorHttpResponse<Nothing>
    data class UnknownException(val exception: Exception) : KtorHttpResponse<Nothing>
    data class ServerError(val code: HttpStatusCode) : KtorHttpResponse<Nothing>
    data class ClientError(val code: HttpStatusCode) : KtorHttpResponse<Nothing>

    val optionalResult: T? get() = (this as? Success)?.result
}

@Suppress("TooGenericExceptionCaught", "MagicNumber")
internal suspend inline fun <reified T : Any> HttpClient.safePost(url: Url, body: String): KtorHttpResponse<T> {
    return try {
        val response = post(url) {
            setBody(TextContent(body, ContentType.Application.Json))
        }
        val statusCode = response.status
        when (statusCode.value) {
            in 500..599 -> KtorHttpResponse.ServerError(statusCode)
            in 400..499 -> KtorHttpResponse.ClientError(statusCode)
            else -> KtorHttpResponse.Success(response.body())
        }
    } catch (e: IOException) {
        KtorHttpResponse.IOError(e)
    } catch (e: Exception) {
        KtorHttpResponse.UnknownException(e)
    }
}
