/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.rum.internal.domain.scope.isConnected
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.v2.api.context.NetworkInfo
import com.datadog.android.v2.api.context.UserInfo
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class LongTaskEventAssert(actual: LongTaskEvent) :
    AbstractObjectAssert<LongTaskEventAssert, LongTaskEvent>(
        actual,
        LongTaskEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = TIMESTAMP_THRESHOLD_MS
    ): LongTaskEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasDuration(expected: Long): LongTaskEventAssert {
        assertThat(actual.longTask.duration)
            .overridingErrorMessage(
                "Expected event data to have longTask.duration $expected " +
                    "but was ${actual.longTask.duration}"
            )
            .isEqualTo(expected)
        return this
    }

    fun isFrozenFrame(expected: Boolean?): LongTaskEventAssert {
        assertThat(actual.longTask.isFrozenFrame)
            .overridingErrorMessage(
                "Expected event data to have longTask.isFrozenFrame $expected " +
                    "but was ${actual.longTask.isFrozenFrame}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUserInfo(expected: UserInfo?): LongTaskEventAssert {
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
        assertThat(actual.usr?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have user additional " +
                    "properties ${expected?.additionalProperties} " +
                    "but was ${actual.usr?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.additionalProperties)
        return this
    }

    fun containsExactlyContextAttributes(expected: Map<String, Any?>) {
        assertThat(actual.context?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have context " +
                    "additional properties $expected " +
                    "but was ${actual.context?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected)
    }

    fun hasConnectivityInfo(expected: NetworkInfo?): LongTaskEventAssert {
        val expectedStatus = if (expected?.isConnected() == true) {
            LongTaskEvent.Status.CONNECTED
        } else {
            LongTaskEvent.Status.NOT_CONNECTED
        }
        val expectedInterfaces = when (expected?.connectivity) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(LongTaskEvent.Interface.ETHERNET)
            NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(LongTaskEvent.Interface.WIFI)
            NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(LongTaskEvent.Interface.WIMAX)
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(
                LongTaskEvent.Interface.BLUETOOTH
            )
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(LongTaskEvent.Interface.CELLULAR)
            NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(LongTaskEvent.Interface.OTHER)
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

    fun hasView(expectedId: String?, expectedUrl: String?): LongTaskEventAssert {
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

    fun hasApplicationId(expected: String): LongTaskEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected context to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): LongTaskEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected context to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionId(expected: String?): LongTaskEventAssert {
        if (expected != null) {
            assertThat(actual.action?.id)
                .overridingErrorMessage(
                    "Expected event data to have action.id $expected but was ${actual.action?.id}"
                )
                .contains(expected)
        } else {
            assertThat(actual.action?.id)
                .overridingErrorMessage(
                    "Expected event data to have no action.id but was ${actual.action?.id}"
                )
                .isNullOrEmpty()
        }
        return this
    }

    fun hasLiteSessionPlan(): LongTaskEventAssert {
        assertThat(actual.dd.session?.plan)
            .overridingErrorMessage(
                "Expected event to have a session plan of 1 instead it was %s",
                actual.dd.session?.plan ?: "null"
            )
            .isEqualTo(LongTaskEvent.Plan.PLAN_1)
        return this
    }

    fun hasSource(source: LongTaskEvent.Source?): LongTaskEventAssert {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event to have a source %s" +
                    " instead it was %s",
                source ?: "null",
                actual.source ?: "null"
            )
            .isEqualTo(source)
        return this
    }

    fun hasDeviceInfo(
        name: String,
        model: String,
        brand: String,
        type: LongTaskEvent.DeviceType,
        architecture: String
    ): LongTaskEventAssert {
        assertThat(actual.device?.name)
            .overridingErrorMessage(
                "Expected event data to have device.name $name but was ${actual.device?.name}"
            )
            .isEqualTo(name)
        assertThat(actual.device?.model)
            .overridingErrorMessage(
                "Expected event data to have device.model $model but was ${actual.device?.model}"
            )
            .isEqualTo(model)
        assertThat(actual.device?.brand)
            .overridingErrorMessage(
                "Expected event data to have device.brand $brand but was ${actual.device?.brand}"
            )
            .isEqualTo(brand)
        assertThat(actual.device?.type)
            .overridingErrorMessage(
                "Expected event data to have device.type $type but was ${actual.device?.type}"
            )
            .isEqualTo(type)
        assertThat(actual.device?.architecture)
            .overridingErrorMessage(
                "Expected event data to have device.architecture $architecture" +
                    " but was ${actual.device?.architecture}"
            )
            .isEqualTo(architecture)
        return this
    }

    fun hasOsInfo(
        name: String,
        version: String,
        versionMajor: String
    ): LongTaskEventAssert {
        assertThat(actual.os?.name)
            .overridingErrorMessage(
                "Expected event data to have os.name $name but was ${actual.os?.name}"
            )
            .isEqualTo(name)
        assertThat(actual.os?.version)
            .overridingErrorMessage(
                "Expected event data to have os.version $version but was ${actual.os?.version}"
            )
            .isEqualTo(version)
        assertThat(actual.os?.versionMajor)
            .overridingErrorMessage(
                "Expected event data to have os.version_major $versionMajor" +
                    " but was ${actual.os?.versionMajor}"
            )
            .isEqualTo(versionMajor)
        return this
    }

    fun hasReplay(hasReplay: Boolean) {
        assertThat(actual.session.hasReplay)
            .overridingErrorMessage(
                "Expected event data to have hasReplay $hasReplay " +
                    "but was ${actual.session.hasReplay}"
            )
            .isEqualTo(hasReplay)
    }

    fun hasServiceName(serviceName: String?): LongTaskEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected RUM event to have serviceName: $serviceName" +
                    " but instead was: ${actual.service}"
            )
            .isEqualTo(serviceName)
        return this
    }

    fun hasVersion(version: String?): LongTaskEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected RUM event to have version: $version" +
                    " but instead was: ${actual.version}"
            )
            .isEqualTo(version)
        return this
    }

    companion object {
        internal const val TIMESTAMP_THRESHOLD_MS = 50L
        internal fun assertThat(actual: LongTaskEvent): LongTaskEventAssert =
            LongTaskEventAssert(actual)
    }
}
