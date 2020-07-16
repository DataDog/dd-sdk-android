/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.model.ResourceEvent
import com.datadog.android.rum.internal.domain.model.ViewEvent
import com.datadog.android.rum.internal.domain.scope.toMethod
import com.datadog.android.rum.internal.domain.scope.toSchemaType
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ResourceEventAssert(actual: ResourceEvent) :
    AbstractObjectAssert<ResourceEventAssert, ResourceEvent>(
        actual,
        ResourceEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = RumEventAssert.TIMESTAMP_THRESHOLD_MS
    ): ResourceEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasKind(expected: RumResourceKind): ResourceEventAssert {
        assertThat(actual.resource.type)
            .overridingErrorMessage(
                "Expected event data to have resource.type $expected " +
                    "but was ${actual.resource.type}"
            )
            .isEqualTo(expected.toSchemaType())
        return this
    }

    fun hasUrl(expected: String): ResourceEventAssert {
        assertThat(actual.resource.url)
            .overridingErrorMessage(
                "Expected event data to have resource.url $expected but was ${actual.resource.url}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasMethod(expected: String): ResourceEventAssert {
        assertThat(actual.resource.method)
            .overridingErrorMessage(
                "Expected event data to have resource.method $expected " +
                    "but was ${actual.resource.method}"
            )
            .isEqualTo(expected.toMethod())
        return this
    }

    fun hasDurationGreaterThan(upperBound: Long): ResourceEventAssert {
        assertThat(actual.resource.duration)
            .overridingErrorMessage(
                "Expected event data to have resource.duration greater than $upperBound " +
                    "but was ${actual.resource.duration}"
            )
            .isGreaterThanOrEqualTo(upperBound)
        return this
    }

    fun hasTiming(expected: ResourceTiming): ResourceEventAssert {
        if (expected.dnsDuration > 0) {
            assertThat(actual.resource.dns?.start)
                .overridingErrorMessage(
                    "Expected event data to have resource.dns.start ${expected.dnsStart} " +
                        "but was ${actual.resource.dns?.start}"
                )
                .isEqualTo(expected.dnsStart)
            assertThat(actual.resource.dns?.duration)
                .overridingErrorMessage(
                    "Expected event data to have resource.dns.duration ${expected.dnsDuration} " +
                        "but was ${actual.resource.dns?.duration}"
                )
                .isEqualTo(expected.dnsDuration)
        } else {
            assertThat(actual.resource.dns)
                .overridingErrorMessage(
                    "Expected event data to have no resource.dns but was ${actual.resource.dns}"
                )
                .isNull()
        }

        if (expected.connectDuration > 0) {
            assertThat(actual.resource.connect?.start)
                .overridingErrorMessage(
                    "Expected event data to have resource.connect.start ${expected.connectStart} " +
                        "but was ${actual.resource.connect?.start}"
                )
                .isEqualTo(expected.connectStart)
            assertThat(actual.resource.connect?.duration)
                .overridingErrorMessage(
                    "Expected event data to have resource.connect.duration " +
                        "${expected.connectDuration} but was ${actual.resource.connect?.duration}"
                )
                .isEqualTo(expected.connectDuration)
        } else {
            assertThat(actual.resource.connect)
                .overridingErrorMessage(
                    "Expected event data to have no resource.connect " +
                        "but was ${actual.resource.connect}"
                )
                .isNull()
        }

        if (expected.sslDuration > 0) {
            assertThat(actual.resource.ssl?.start)
                .overridingErrorMessage(
                    "Expected event data to have resource.ssl.start ${expected.sslStart} " +
                        "but was ${actual.resource.ssl?.start}"
                )
                .isEqualTo(expected.sslStart)
            assertThat(actual.resource.ssl?.duration)
                .overridingErrorMessage(
                    "Expected event data to have resource.ssl.duration ${expected.sslDuration} " +
                        "but was ${actual.resource.ssl?.duration}"
                )
                .isEqualTo(expected.sslDuration)
        } else {
            assertThat(actual.resource.ssl)
                .overridingErrorMessage(
                    "Expected event data to have no resource.ssl but was ${actual.resource.ssl}"
                )
                .isNull()
        }

        if (expected.firstByteDuration > 0) {
            assertThat(actual.resource.firstByte?.start)
                .overridingErrorMessage(
                    "Expected event data to have resource.firstByte.start " +
                        "${expected.firstByteStart} but was ${actual.resource.firstByte?.start}"
                )
                .isEqualTo(expected.firstByteStart)
            assertThat(actual.resource.firstByte?.duration)
                .overridingErrorMessage(
                    "Expected event data to have resource.firstByte.duration " +
                        "${expected.firstByteDuration} but was " +
                        "${actual.resource.firstByte?.duration}"
                )
                .isEqualTo(expected.firstByteDuration)
        } else {
            assertThat(actual.resource.firstByte)
                .overridingErrorMessage(
                    "Expected event data to have no resource.firstByte " +
                        "but was ${actual.resource.firstByte}"
                )
                .isNull()
        }

        if (expected.downloadDuration > 0) {
            assertThat(actual.resource.download?.start)
                .overridingErrorMessage(
                    "Expected event data to have resource.download.start " +
                        "${expected.downloadStart} but was ${actual.resource.download?.start}"
                )
                .isEqualTo(expected.downloadStart)
            assertThat(actual.resource.download?.duration)
                .overridingErrorMessage(
                    "Expected event data to have resource.download.duration " +
                        "${expected.downloadDuration} but was ${actual.resource.download?.duration}"
                )
                .isEqualTo(expected.downloadDuration)
        } else {
            assertThat(actual.resource.download)
                .overridingErrorMessage(
                    "Expected event data to have no resource.download " +
                        "but was ${actual.resource.download}"
                )
                .isNull()
        }
        return this
    }

    fun hasNoTiming(): ResourceEventAssert {
        assertThat(actual.resource.dns)
            .overridingErrorMessage(
                "Expected event data to have no resource.dns but was ${actual.resource.dns}"
            )
            .isNull()
        assertThat(actual.resource.connect)
            .overridingErrorMessage(
                "Expected event data to have no resource.connect but was ${actual.resource.connect}"
            )
            .isNull()
        assertThat(actual.resource.ssl)
            .overridingErrorMessage(
                "Expected event data to have no resource.ssl but was ${actual.resource.ssl}"
            )
            .isNull()
        assertThat(actual.resource.firstByte)
            .overridingErrorMessage(
                "Expected event data to have no resource.firstByte " +
                    "but was ${actual.resource.firstByte}"
            )
            .isNull()
        assertThat(actual.resource.download)
            .overridingErrorMessage(
                "Expected event data to have no resource.download " +
                    "but was ${actual.resource.download}"
            )
            .isNull()
        return this
    }

    fun hasUserInfo(expected: UserInfo?): ResourceEventAssert {
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

    fun hasConnectivityInfo(expected: NetworkInfo?): ResourceEventAssert {
        val expectedStatus = if (expected?.isConnected() == true) {
            ResourceEvent.Status.CONNECTED
        } else {
            ResourceEvent.Status.NOT_CONNECTED
        }
        val expectedInterfaces = when (expected?.connectivity) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ResourceEvent.Interface.ETHERNET)
            NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ResourceEvent.Interface.WIFI)
            NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ResourceEvent.Interface.WIMAX)
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ResourceEvent.Interface.BLUETOOTH)
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ResourceEvent.Interface.CELLULAR)
            NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ResourceEvent.Interface.OTHER)
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

    fun hasView(expectedId: String?, expectedUrl: String?): ResourceEventAssert {
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

    fun hasApplicationId(expected: String): ResourceEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected event data to have application.id $expected " +
                    "but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ResourceEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected event data to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): ResourceEventAssert {
        assertThat(actual.action?.id)
            .overridingErrorMessage(
                "Expected event data to have action.id $expected but was ${actual.action?.id}"
            )
            .isEqualTo(expected)
        return this
    }

    companion object {

        internal const val DURATION_THRESHOLD_NANOS = 1000L

        internal fun assertThat(actual: ViewEvent): ViewEventAssert =
            ViewEventAssert(actual)
    }
}
