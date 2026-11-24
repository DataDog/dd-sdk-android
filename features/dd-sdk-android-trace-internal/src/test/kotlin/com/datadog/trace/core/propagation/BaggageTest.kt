/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.trace.core.propagation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

internal class BaggageTest {

    @ParameterizedTest
    @MethodSource("invalidBaggageHeaderSamples")
    fun `M return empty W from { invalid header value }`(
        headerValue: String?
    ) {
        assertThat(Baggage.from(headerValue).asMap()).isEmpty()
    }

    @ParameterizedTest
    @MethodSource("validBaggageHeaderSamples")
    fun `M return expected W from`(
        headerValue: String?,
        expectedMap: Map<String, String>
    ) {
        assertThat(Baggage.from(headerValue).asMap()).isEqualTo(expectedMap)
    }

    @ParameterizedTest
    @MethodSource("validBaggageHeaderSamples")
    fun `M return expected W toString`(
        expectedString: String?,
        entries: Map<String, String>
    ) {
        val baggage = Baggage()
        entries.forEach { (key, value) -> baggage.put(key, value) }
        assertThat(baggage.toString()).isEqualTo(expectedString)
    }

    @ParameterizedTest
    @MethodSource("removeKeySamples")
    fun `M remove only matching members W removeKey`(
        headerValue: String,
        expected: String?
    ) {
        assertThat(Baggage.removeKey(headerValue, REMOVED_KEY)).isEqualTo(expected)
    }

    @ParameterizedTest
    @MethodSource("headersWithoutKey")
    fun `M return same header W removeKey { no matching member }`(
        headerValue: String
    ) {
        assertThat(Baggage.removeKey(headerValue, REMOVED_KEY)).isSameAs(headerValue)
    }

    private companion object {
        const val REMOVED_KEY = "user.anonymous_id"

        @JvmStatic
        fun removeKeySamples() = listOf(
            Arguments.of("user.anonymous_id=old", null),
            Arguments.of("user.anonymous_id=old,user.anonymous_id=other", null),
            Arguments.of("custom=,user.anonymous_id=old", "custom="),
            Arguments.of("user.id=user+id;prop=value,user.anonymous_id=old", "user.id=user+id;prop=value"),
            Arguments.of(" user.anonymous_id ;p=1 , custom=a", " custom=a"),
            Arguments.of("user%2Eanonymous%5Fid=old,user.id=user%2Bid", "user.id=user%2Bid"),
            Arguments.of("not a member,,=x,user.anonymous_id=old,k=%zz", "not a member,,=x,k=%zz"),
            Arguments.of("k=%zz,user%zz=x,user.anonymous_id=old", "k=%zz,user%zz=x")
        )

        @JvmStatic
        fun headersWithoutKey() = listOf(
            Arguments.of("user.id=userId,user.anonymous_idx=other"),
            Arguments.of("custom=,k=%zz"),
            Arguments.of("")
        )

        @JvmStatic
        fun invalidBaggageHeaderSamples() = listOf(
            Arguments.of(null),
            Arguments.of("no-equal-sign"),
            Arguments.of("foo=gets-dropped-because-subsequent-pair-is-malformed,="),
            Arguments.of("=no-key"),
            Arguments.of("no-value="),
            Arguments.of(",,"),
            Arguments.of("=")
        )

        @JvmStatic
        fun validBaggageHeaderSamples() = listOf(
            Arguments.of("", emptyMap<String, String>()),

            Arguments.of("key=value", mapOf("key" to "value")),
            Arguments.of("key1=val1,key2=val2", mapOf("key1" to "val1", "key2" to "val2")),
            Arguments.of("serverNode=DF%2028", mapOf("serverNode" to "DF 28")),
            Arguments.of("abcdefg=hijklmnopq%E2%99%A5", mapOf("abcdefg" to "hijklmnopq♥")),
            Arguments.of("userId=Am%C3%A9lie", mapOf("userId" to "Amélie")),
            Arguments.of("userId=Am%C3%A9lie", mapOf("userId" to "Amélie")),
            Arguments.of(
                "%22%2C%3B%5C%28%29%2F%3A%3C%3D%3E%3F%40%5B%5D%7B%7D=%22%2C%3B%5C",
                mapOf(
                    "\",;\\()/:<=>?@[]{}" to "\",;\\"
                )
            ),
            Arguments.of(
                "key1=value1%3Bproperty1%3Bproperty2,key2=value2,key3=value3%3B%20propertyKey=propertyValue",
                mapOf(
                    "key1" to "value1;property1;property2",
                    "key2" to "value2",
                    "key3" to "value3; propertyKey=propertyValue"
                )
            )
        )
    }
}
