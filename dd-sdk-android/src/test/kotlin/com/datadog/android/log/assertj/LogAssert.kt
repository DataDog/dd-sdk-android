/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.datadog.android.log.internal.domain.Log
import com.datadog.android.log.internal.net.NetworkInfo
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

    fun hasThrowable(expected: Throwable?): LogAssert {
        assertThat(actual.throwable)
            .overridingErrorMessage(
                "Expected log to have throwable $expected but was ${actual.throwable}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasTimestamp(expected: Long): LogAssert {
        assertThat(actual.timestamp)
            .overridingErrorMessage(
                "Expected log to have timestamp $expected but was ${actual.timestamp}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasAttributes(attributes: Map<String, Any?>): LogAssert {
        assertThat(actual.attributes)
            .containsAllEntriesOf(attributes)
        return this
    }

    fun hasTags(tags: Collection<String>): LogAssert {
        assertThat(actual.tags)
            .containsExactlyInAnyOrder(*tags.toTypedArray())
        return this
    }

    fun hasNetworkInfo(expected: NetworkInfo?): LogAssert {
        assertThat(actual.networkInfo)
            .overridingErrorMessage(
                "Expected log to have networkInfo $expected " +
                    "but was ${actual.networkInfo}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLoggerName(expected: String): LogAssert {
        assertThat(actual.loggerName)
            .overridingErrorMessage(
                "Expected log to have loggerName $expected but was ${actual.loggerName}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasThreadName(expected: String): LogAssert {
        assertThat(actual.threadName)
            .overridingErrorMessage(
                "Expected log to have threadName $expected but was ${actual.threadName}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        private const val TIMESTAMP_THRESHOLD_MS = 50

        internal fun assertThat(actual: Log): LogAssert =
            LogAssert(actual)
    }
}
