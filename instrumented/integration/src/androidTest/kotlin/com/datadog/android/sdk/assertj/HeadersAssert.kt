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
