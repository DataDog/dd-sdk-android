/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.benchmark.sample.network.rickandmorty.fallback

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Retries failed (I/O error or non-2xx) Rick & Morty requests against the local snapshot server.
 * Once an API request has been served locally, later API requests go to the local server directly;
 * avatar requests always try the real host first.
 * If the local server is unavailable, the original outcome is returned or rethrown.
 */
internal class RickAndMortyFallbackInterceptor(
    private val localPortProvider: () -> Int,
    private val listener: RickAndMortyFallbackListener
) : Interceptor {

    private val isApiServedLocally = AtomicBoolean(false)
    private val isFallbackActivated = AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestType = requestTypeOf(request.url) ?: return chain.proceed(request)

        if (requestType == RequestType.API && isApiServedLocally.get()) {
            val port = startLocalServer()
            val localResponse = port?.let { proceedLocally(chain, request, requestType, it) }
            if (localResponse != null) {
                return localResponse
            }
        }

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            val port = startLocalServer() ?: throw e
            return proceedLocally(chain, request, requestType, port) ?: throw e
        }

        return if (response.isSuccessful) {
            response
        } else {
            fallBackFromResponse(chain, request, requestType, response)
        }
    }

    private fun fallBackFromResponse(
        chain: Interceptor.Chain,
        request: Request,
        requestType: RequestType,
        response: Response
    ): Response {
        val port = startLocalServer() ?: return response
        // OkHttp requires the previous response to be closed before the next proceed call,
        // so keep a copy of the body in case the local call fails.
        val retainedBody = try {
            response.peekBody(MAX_RETAINED_BODY_BYTES)
        } catch (e: IOException) {
            listener.onFallbackFailed(e)
            return response
        }
        response.close()
        return proceedLocally(chain, request, requestType, port)
            ?: response.newBuilder().body(retainedBody).build()
    }

    private fun startLocalServer(): Int? = try {
        localPortProvider()
    } catch (e: IOException) {
        listener.onFallbackFailed(e)
        null
    }

    private fun proceedLocally(
        chain: Interceptor.Chain,
        request: Request,
        requestType: RequestType,
        port: Int
    ): Response? {
        val localUrl = request.url.newBuilder()
            .scheme("http")
            .host(LOCAL_HOST)
            .port(port)
            .build()
        val response = try {
            chain.proceed(request.newBuilder().url(localUrl).build())
        } catch (e: IOException) {
            listener.onFallbackFailed(e)
            return null
        }
        if (requestType == RequestType.API) {
            isApiServedLocally.set(true)
        }
        if (isFallbackActivated.compareAndSet(false, true)) {
            listener.onFallbackActivated()
        }
        return response
    }

    private fun requestTypeOf(url: HttpUrl): RequestType? {
        val path = url.encodedPath
        return when {
            url.host != RICK_AND_MORTY_HOST -> null
            path.startsWith(AVATAR_PATH_PREFIX) -> RequestType.AVATAR
            path.startsWith(API_PATH_PREFIX) -> RequestType.API
            else -> null
        }
    }

    private enum class RequestType {
        API,
        AVATAR
    }

    private companion object {
        const val RICK_AND_MORTY_HOST = "rickandmortyapi.com"
        const val LOCAL_HOST = "127.0.0.1"
        const val API_PATH_PREFIX = "/api/"
        const val AVATAR_PATH_PREFIX = "/api/character/avatar/"
        const val MAX_RETAINED_BODY_BYTES = 64L * 1024
    }
}
