/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.internal

import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class LogAssert(actual: Log) :
    AbstractObjectAssert<LogAssert, Log>(actual, LogAssert::class.java) {

    fun hasLevel(expected: Int): LogAssert {
        assertThat(actual.level)
            .overridingErrorMessage(
                "Expected log to have level $expected but was ${actual.level}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasServiceName(expected: String): LogAssert {
        assertThat(actual.serviceName)
            .overridingErrorMessage(
                "Expected log to have name $expected but was ${actual.serviceName}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMessage(expected: String): LogAssert {
        assertThat(actual.message)
            .overridingErrorMessage(
                "Expected log to have message $expected but was ${actual.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTimestamp(expected: Long): LogAssert {
        assertThat(actual.timestamp)
            .overridingErrorMessage(
                "Expected log to have timestamp $expected but was ${actual.timestamp}"
            )
            .isBetween(expected - TIMESTAMP_THRESHOLD_MS, expected)
        return this
    }

    fun hasNoTimestamp(): LogAssert {
        assertThat(actual.timestamp)
            .overridingErrorMessage(
                "Expected log to have no timestamp but was ${actual.timestamp}"
            )
            .isNull()
        return this
    }

    fun hasUserAgent(expected: String?): LogAssert {
        assertThat(actual.userAgent)
            .overridingErrorMessage(
                "Expected log to have userAgent $expected but was ${actual.userAgent}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFields(fields: Map<String, Any?>) {
        assertThat(actual.fields)
            .containsExactly(*fields.entries.toTypedArray())
    }

    companion object {

        private const val TIMESTAMP_THRESHOLD_MS = 50

        internal fun assertThat(actual: Log): LogAssert =
            LogAssert(actual)
    }
}
