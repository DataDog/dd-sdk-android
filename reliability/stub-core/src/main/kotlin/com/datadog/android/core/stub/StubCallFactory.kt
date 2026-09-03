/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.stub

import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import okio.Timeout
import java.io.IOException

/**
 * Stubbed version of a [Call.Factory], which never performs any real network call.
 *
 * Every [Call] it creates fails with an [IOException], letting the calling code exercise its
 * error handling path without reaching out to any host.
 */
class StubCallFactory : Call.Factory {

    override fun newCall(request: Request): Call = StubCall(request)

    private class StubCall(private val request: Request) : Call {

        private var isExecuted = false
        private var isCanceled = false

        override fun request(): Request = request

        override fun execute(): Response {
            isExecuted = true
            throw IOException(ERROR_MESSAGE)
        }

        override fun enqueue(responseCallback: Callback) {
            isExecuted = true
            responseCallback.onFailure(this, IOException(ERROR_MESSAGE))
        }

        override fun cancel() {
            isCanceled = true
        }

        override fun isExecuted(): Boolean = isExecuted

        override fun isCanceled(): Boolean = isCanceled

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = StubCall(request)
    }

    private companion object {
        const val ERROR_MESSAGE = "StubCallFactory does not perform any real network call"
    }
}
