/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.assertj

import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.log.internal.domain.DatadogLogGenerator
import com.datadog.android.log.model.LogEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

internal class LogEventAssert(actual: LogEvent) :
    AbstractObjectAssert<LogEventAssert, LogEvent>(actual, LogEventAssert::class.java) {

    fun hasStatus(expected: LogEvent.Status): LogEventAssert {
        assertThat(actual.status)
            .overridingErrorMessage(
                "Expected log to have level $expected but was ${actual.status}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasServiceName(expected: String): LogEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected log to have name $expected but was ${actual.service}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMessage(expected: String): LogEventAssert {
        assertThat(actual.message)
            .overridingErrorMessage(
                "Expected log to have message $expected but was ${actual.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasError(expected: LogEvent.Error): LogEventAssert {
        assertThat(actual.error)
            .overridingErrorMessage(
                "Expected log to have error info $expected but was ${actual.error}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDate(expected: String): LogEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected log to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDateAround(expected: Long): LogEventAssert {
        val parsedDate = dateFormatter.parse(actual.date)?.time
        assertThat(parsedDate)
            .overridingErrorMessage(
                "Expected log to have date around $expected but was $parsedDate"
            )
            .isCloseTo(expected, Offset.offset(200L))
        return this
    }

    fun hasExactlyAttributes(attributes: Map<String, Any?>): LogEventAssert {
        assertThat(actual.additionalProperties)
            .hasSameSizeAs(attributes)
            .containsAllEntriesOf(attributes)
        return this
    }

    fun hasExactlyTags(expected: Collection<String>): LogEventAssert {
        val serializedTags = expected.joinToString(separator = ",")
        assertThat(actual.ddtags)
            .overridingErrorMessage(
                "Expected log to have tags $serializedTags but was ${actual.ddtags}"
            )
            .isEqualTo(serializedTags)
        return this
    }

    fun hasLoggerName(expected: String): LogEventAssert {
        assertThat(actual.logger.name)
            .overridingErrorMessage(
                "Expected log to have loggerName $expected but was ${actual.logger.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDeviceArchitecture(expected: String): LogEventAssert {
        assertThat(actual.dd.device.architecture)
            .overridingErrorMessage(
                "Expected device to have architecture $expected but was ${actual.dd.device.architecture}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasThreadName(expected: String): LogEventAssert {
        assertThat(actual.logger.threadName)
            .overridingErrorMessage(
                "Expected log to have threadName $expected but was ${actual.logger.threadName}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLoggerVersion(expected: String): LogEventAssert {
        assertThat(actual.logger.version)
            .overridingErrorMessage(
                "Expected log to have version $expected but was ${actual.logger.version}"
            )
            .isEqualTo(expected)

        return this
    }

    fun hasNetworkInfo(networkInfo: NetworkInfo): LogEventAssert {
        assertThat(actual.network?.client?.connectivity)
            .overridingErrorMessage(
                "Expected LogEvent to have connectivity: " +
                    "${networkInfo.connectivity} but " +
                    "instead was: ${actual.network?.client?.connectivity}"
            )
            .isEqualTo(networkInfo.connectivity.toString())
        assertThat(actual.network?.client?.downlinkKbps)
            .overridingErrorMessage(
                "Expected LogEvent to have downlinkKbps: " +
                    "${networkInfo.downKbps?.toString()} but " +
                    "instead was: ${actual.network?.client?.downlinkKbps}"
            )
            .isEqualTo(networkInfo.downKbps?.toString())
        assertThat(actual.network?.client?.uplinkKbps)
            .overridingErrorMessage(
                "Expected LogEvent to have uplinkKbps: " +
                    "${networkInfo.upKbps?.toString()} but " +
                    "instead was: ${actual.network?.client?.uplinkKbps}"
            )
            .isEqualTo(networkInfo.upKbps?.toString())
        assertThat(actual.network?.client?.signalStrength)
            .overridingErrorMessage(
                "Expected LogEvent to have signal strength: " +
                    "${networkInfo.strength?.toString()} but " +
                    "instead was: ${actual.network?.client?.signalStrength}"
            )
            .isEqualTo(networkInfo.strength?.toString())
        assertThat(actual.network?.client?.simCarrier?.id)
            .overridingErrorMessage(
                "Expected LogEvent to have carrier id: " +
                    "${networkInfo.carrierId?.toString()} but " +
                    "instead was: ${actual.network?.client?.simCarrier?.id}"
            )
            .isEqualTo(networkInfo.carrierId?.toString())
        assertThat(actual.network?.client?.simCarrier?.name)
            .overridingErrorMessage(
                "Expected LogEvent to have carrier name: " +
                    "${networkInfo.carrierName} but " +
                    "instead was: ${actual.network?.client?.simCarrier?.name}"
            )
            .isEqualTo(networkInfo.carrierName)
        return this
    }

    fun doesNotHaveError(): LogEventAssert {
        assertThat(actual.error)
            .overridingErrorMessage(
                "Expected log to not have a error info " +
                    "but instead it had ${actual.error}"
            )
            .isNull()
        return this
    }

    fun doesNotHaveNetworkInfo(): LogEventAssert {
        assertThat(actual.network)
            .overridingErrorMessage(
                "Expected log to not have a network info " +
                    "but instead it had ${actual.network}"
            )
            .isNull()
        return this
    }

    fun hasUserInfo(userInfo: UserInfo): LogEventAssert {
        assertThat(actual.usr?.name)
            .overridingErrorMessage(
                "Expected LogEvent to have user name: " +
                    "${userInfo.name} but " +
                    "instead was: ${actual.usr?.name}"
            )
            .isEqualTo(userInfo.name)
        assertThat(actual.usr?.email)
            .overridingErrorMessage(
                "Expected LogEvent to have user email: " +
                    "${userInfo.email} but " +
                    "instead was: ${actual.usr?.email}"
            )
            .isEqualTo(userInfo.email)
        assertThat(actual.usr?.id)
            .overridingErrorMessage(
                "Expected LogEvent to have user id: " +
                    "${userInfo.id} but " +
                    "instead was: ${actual.usr?.id}"
            )
            .isEqualTo(userInfo.id)
        assertThat(actual.usr?.additionalProperties)
            .hasSameSizeAs(userInfo.additionalProperties)
            .containsAllEntriesOf(userInfo.additionalProperties)
        return this
    }

    fun doesNotHaveUserInfo(): LogEventAssert {
        assertThat(actual.usr)
            .overridingErrorMessage(
                "Expected log to not have an user info " +
                    "but instead it had ${actual.usr}"
            )
            .isNull()
        return this
    }

    fun hasBuildId(buildId: String?): LogEventAssert {
        assertThat(actual.buildId)
            .overridingErrorMessage(
                "Expected LogEvent to have build ID: $buildId" +
                    " but instead was ${actual.buildId}"
            )
            .isEqualTo(buildId)
        return this
    }

    companion object {

        private val dateFormatter =
            SimpleDateFormat(DatadogLogGenerator.ISO_8601, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

        internal fun assertThat(actual: LogEvent): LogEventAssert =
            LogEventAssert(actual)
    }
}
