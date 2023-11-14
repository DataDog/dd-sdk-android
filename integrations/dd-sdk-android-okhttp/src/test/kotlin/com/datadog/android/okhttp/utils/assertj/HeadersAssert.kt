/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.utils.assertj

import okhttp3.Headers
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class HeadersAssert(actual: Headers) :
    AbstractAssert<HeadersAssert, Headers>(actual, HeadersAssert::class.java) {

    fun hasTraceParentHeader(traceId: String, spanId: String, isSampled: Boolean): HeadersAssert {
        val expected = createTraceParentHeader(traceId, spanId, isSampled)
        return hasHeader(TRACEPARENT_HEADER_NAME, expected)
    }

    fun hasHeader(name: String, expectedValue: String): HeadersAssert {
        val actualValue = actual[name]
        if (actualValue == null) {
            failWithMessage(
                "We were expecting to have header [$name] but it was missing."
            )
        } else {
            assertThat(actualValue)
                .overridingErrorMessage(
                    "We were expecting to have value [$expectedValue] for" +
                        " header [$name], but it was [$actualValue]."
                )
                .isEqualTo(expectedValue)
        }
        return this
    }

    fun hasTraceStateHeaderWithOnlyDatadogVendorValues(
        spanId: String,
        isSampled: Boolean,
        origin: String? = null
    ): HeadersAssert {
        val headerValue = actual[TRACESTATE_HEADER_NAME]
        if (headerValue == null) {
            failWithMessage(
                "We were expecting to have header [$TRACESTATE_HEADER_NAME] but it was missing."
            )
        } else {
            val vendorTags = headerValue.split(",")
            assertThat(vendorTags)
                .overridingErrorMessage(
                    "We were expecting to have only one vendor for" +
                        " [$TRACESTATE_HEADER_NAME] header, but the actual header value is [$headerValue]"
                )
                .hasSize(1)
            val vendor = vendorTags[0]
            assertThat(vendor)
                .overridingErrorMessage(
                    "We were expecting to have Datadog vendor for" +
                        " [$TRACESTATE_HEADER_NAME] header, but the actual header value is [$headerValue]"
                )
                .startsWith("dd=")

            val rawActualTags = vendor.substringAfter("dd=")
                .split(";")
                .map { it.split(":").let { it[0] to it[1] } }
                .groupBy { it.first }
                .mapValues { it.value.map { it.second } }

            rawActualTags.forEach {
                assertThat(it.value)
                    .overridingErrorMessage(
                        "We were expecting to not have duplicated or empty tags for" +
                            " Datadog vendor of [$TRACESTATE_HEADER_NAME] header, but" +
                            " the actual tags were $vendor"
                    )
                    .hasSize(1)
            }

            val expectedTags = mutableMapOf(
                "s" to if (isSampled) "1" else "0",
                "p" to spanId.padStart(length = 16, padChar = '0')
            ).apply {
                if (origin != null) put("o", origin)
            }

            val actualTags = rawActualTags.mapValues { it.value.first() }

            assertThat(actualTags)
                .overridingErrorMessage(
                    "We were expecting to have the following tags for" +
                        " Datadog vendor of [$TRACESTATE_HEADER_NAME] header [$expectedTags], but" +
                        " the actual tags were [$actualTags]"
                )
                .isEqualTo(expectedTags)
        }
        return this
    }

    private fun createTraceParentHeader(
        traceId: String,
        spanId: String,
        isSampled: Boolean
    ): String {
        // https://www.w3.org/TR/trace-context/#traceparent-header
        val paddedTraceId = traceId.padStart(length = 32, padChar = '0')
        val paddedSpanId = spanId.padStart(length = 16, padChar = '0')
        val flags = if (isSampled) "01" else "00"
        return "00-$paddedTraceId-$paddedSpanId-$flags"
    }

    companion object {

        private const val TRACEPARENT_HEADER_NAME = "traceparent"
        private const val TRACESTATE_HEADER_NAME = "tracestate"

        /**
         * Create assertion for [Headers].
         * @param actual the actual element to assert on
         * @return the created assertion object.
         */
        fun assertThat(actual: Headers): HeadersAssert = HeadersAssert(actual)
    }
}
