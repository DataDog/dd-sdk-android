/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sdk.assertj

import okhttp3.Headers
import org.assertj.core.api.AbstractAssert

internal class HeadersAssert(actual: Headers?) : AbstractAssert<HeadersAssert, Headers>(
    actual,
    HeadersAssert::class.java
) {

    fun hasHeader(key: String, name: String): HeadersAssert {
        checkNotNull(actual)
        val headerValue = actual.get(key)
        if (headerValue != name) {
            failWithMessage(
                "We were expecting [ $name ] for the key [ $key ]" +
                    " instead we found [ $headerValue ] "
            )
        }
        return this
    }

    companion object {
        fun assertThat(actual: Headers?): HeadersAssert {
            return HeadersAssert(actual)
        }

        const val HEADER_UA = "User-Agent"
        const val HEADER_CT = "Content-Type"
    }
}
