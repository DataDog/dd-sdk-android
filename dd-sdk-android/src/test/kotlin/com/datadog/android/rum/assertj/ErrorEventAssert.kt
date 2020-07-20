/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.internal.domain.model.ErrorEvent
import com.datadog.android.rum.internal.domain.scope.toSchemaSource
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ErrorEventAssert(actual: ErrorEvent) :
    AbstractObjectAssert<ErrorEventAssert, ErrorEvent>(
        actual,
        ErrorEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = RumEventAssert.TIMESTAMP_THRESHOLD_MS
    ): ErrorEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasSource(expected: RumErrorSource): ErrorEventAssert {
        assertThat(actual.error.source)
            .overridingErrorMessage(
                "Expected event data to have error.source $expected but was ${actual.error.source}"
            )
            .isEqualTo(expected.toSchemaSource())
        return this
    }

    fun hasMessage(expected: String): ErrorEventAssert {
        assertThat(actual.error.message)
            .overridingErrorMessage(
                "Expected event data to have error.message $expected " +
                    "but was ${actual.error.message}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasStackTrace(expected: String): ErrorEventAssert {
        assertThat(actual.error.stack)
            .overridingErrorMessage(
                "Expected event data to have error.stack $expected but was ${actual.error.stack}"
            )
            .isEqualTo(expected)
        return this
    }

    fun isCrash(expected: Boolean): ErrorEventAssert {
        assertThat(actual.error.isCrash)
            .overridingErrorMessage(
                "Expected event data to have error.isCrash $expected " +
                    "but was ${actual.error.isCrash}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResource(
        expectedUrl: String,
        expectedMethod: String,
        expectedStatusCode: Long
    ): ErrorEventAssert {
        assertThat(actual.error.resource?.url)
            .overridingErrorMessage(
                "Expected event data to have error.resource.url $expectedUrl " +
                    "but was ${actual.error.resource?.url}"
            )
            .isEqualTo(expectedUrl)
        assertThat(actual.error.resource?.method)
            .overridingErrorMessage(
                "Expected event data to have error.resource.method $expectedMethod " +
                    "but was ${actual.error.resource?.method}"
            )
            .isEqualTo(ErrorEvent.Method.valueOf(expectedMethod))
        assertThat(actual.error.resource?.statusCode)
            .overridingErrorMessage(
                "Expected event data to have error.resource.statusCode $expectedStatusCode " +
                    "but was ${actual.error.resource?.statusCode}"
            )
            .isEqualTo(expectedStatusCode)
        return this
    }

    fun hasUserInfo(expected: UserInfo?): ErrorEventAssert {
        assertThat(actual.usr?.id)
            .overridingErrorMessage(
                "Expected RUM event to have usr.id ${expected?.id} " +
                    "but was ${actual.usr?.id}"
            )
            .isEqualTo(expected?.id)
        assertThat(actual.usr?.name)
            .overridingErrorMessage(
                "Expected RUM event to have usr.name ${expected?.name} " +
                    "but was ${actual.usr?.name}"
            )
            .isEqualTo(expected?.name)
        assertThat(actual.usr?.email)
            .overridingErrorMessage(
                "Expected RUM event to have usr.email ${expected?.email} " +
                    "but was ${actual.usr?.email}"
            )
            .isEqualTo(expected?.email)
        return this
    }

    fun hasConnectivityInfo(expected: NetworkInfo?): ErrorEventAssert {
        val expectedStatus = if (expected?.isConnected() == true) {
            ErrorEvent.Status.CONNECTED
        } else {
            ErrorEvent.Status.NOT_CONNECTED
        }
        val expectedInterfaces = when (expected?.connectivity) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ErrorEvent.Interface.ETHERNET)
            NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ErrorEvent.Interface.WIFI)
            NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ErrorEvent.Interface.WIMAX)
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ErrorEvent.Interface.BLUETOOTH)
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ErrorEvent.Interface.CELLULAR)
            NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ErrorEvent.Interface.OTHER)
            NetworkInfo.Connectivity.NETWORK_NOT_CONNECTED -> emptyList()
            null -> null
        }

        assertThat(actual.connectivity?.status)
            .overridingErrorMessage(
                "Expected RUM event to have connectivity.status $expectedStatus " +
                    "but was ${actual.connectivity?.status}"
            )
            .isEqualTo(expectedStatus)

        assertThat(actual.connectivity?.cellular?.technology)
            .overridingErrorMessage(
                "Expected RUM event to connectivity usr.cellular.technology " +
                    "${expected?.cellularTechnology} " +
                    "but was ${actual.connectivity?.cellular?.technology}"
            )
            .isEqualTo(expected?.cellularTechnology)

        assertThat(actual.connectivity?.cellular?.carrierName)
            .overridingErrorMessage(
                "Expected RUM event to connectivity usr.cellular.carrierName " +
                    "${expected?.carrierName} " +
                    "but was ${actual.connectivity?.cellular?.carrierName}"
            )
            .isEqualTo(expected?.carrierName)

        assertThat(actual.connectivity?.interfaces)
            .overridingErrorMessage(
                "Expected RUM event to have connectivity.interfaces $expectedInterfaces " +
                    "but was ${actual.connectivity?.interfaces}"
            )
            .isEqualTo(expectedInterfaces)
        return this
    }

    fun hasView(expectedId: String?, expectedUrl: String?): ErrorEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId.orEmpty())
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedUrl but was ${actual.view.url}"
            )
            .isEqualTo(expectedUrl.orEmpty())
        return this
    }

    fun hasApplicationId(expected: String): ErrorEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected context to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ErrorEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected context to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): ErrorEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal fun assertThat(actual: ErrorEvent): ErrorEventAssert =
            ErrorEventAssert(actual)
    }
}
