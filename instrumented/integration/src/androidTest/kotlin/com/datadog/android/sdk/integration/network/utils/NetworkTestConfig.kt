/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.integration.network.utils

import com.datadog.android.core.internal.net.HttpSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking

/**
 * Configuration for network instrumentation tests.
 */
internal object NetworkTestConfig {

    /**
     * TestServer endpoints.
     */
    object Endpoint {
        const val REDIRECT_PREFIX = "/redirect"
        const val ERROR_PREFIX = "/error"
        const val RETRY_PREFIX = "/retry"

        fun error(code: Int, method: String): String = "$ERROR_PREFIX/$code/${method.lowercase()}"
        fun redirect(hopCount: Int, method: String): String = "$REDIRECT_PREFIX/$hopCount/${method.lowercase()}"
        fun retry(method: String, failCount: Int = 1): String = "$RETRY_PREFIX/$failCount/${method.lowercase()}"
        fun forMethod(method: String): String = "/${method.lowercase()}"
    }

    object Body {
        fun forMethod(method: String): String? = "{\"body\": \"$method\"}".takeIf {
            HttpSpec.Method.isMethodWithBody(method)
        }
    }

    val ALLOWED_METHODS = setOf(
        HttpSpec.Method.HEAD,
        HttpSpec.Method.GET,
        HttpSpec.Method.PATCH,
        HttpSpec.Method.PUT,
        HttpSpec.Method.POST,
        HttpSpec.Method.DELETE
    )

    fun asyncTest(block: suspend CoroutineScope.() -> Unit): Unit = runBlocking(block = block)
}
