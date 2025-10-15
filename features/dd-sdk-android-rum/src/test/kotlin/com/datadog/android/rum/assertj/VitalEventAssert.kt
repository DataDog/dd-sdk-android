/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.rum.assertj

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.isConnected
import com.datadog.android.rum.internal.domain.scope.toVitalSessionPrecondition
import com.datadog.android.rum.model.VitalEvent
import com.datadog.android.rum.model.VitalEvent.VitalEventSessionType
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat

internal class VitalEventAssert(actual: VitalEvent) : AbstractObjectAssert<VitalEventAssert, VitalEvent>(
    actual,
    VitalEventAssert::class.java
) {

    fun hasDate(expected: Long) = apply {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event data to have date $expected but was ${actual.date}"
            )
            .isEqualTo(expected)
    }

    fun hasApplicationId(expected: String) = apply {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected event to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
    }

    fun containsExactlyContextAttributes(expected: Map<String, Any?>) = apply {
        assertThat(actual.context?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have context " +
                    "additional properties $expected " +
                    "but was ${actual.context?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected)
    }

    fun hasStartReason(reason: RumSessionScope.StartReason) = apply {
        assertThat(actual.dd.session?.sessionPrecondition)
            .overridingErrorMessage(
                "Expected event to have a session sessionPrecondition of ${reason.name} " +
                    "but was ${actual.dd.session?.sessionPrecondition}"
            )
            .isEqualTo(reason.toVitalSessionPrecondition())
    }

    fun hasSampleRate(sampleRate: Float?) = apply {
        assertThat(actual.dd.configuration?.sessionSampleRate ?: 0)
            .overridingErrorMessage(
                "Expected event to have sample rate: $sampleRate" +
                    " but instead was: ${actual.dd.configuration?.sessionSampleRate}"
            )
            .isEqualTo(sampleRate)
    }

    fun hasSessionId(expected: String) = apply {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected event to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
    }

    fun hasSessionType(expected: VitalEventSessionType) = apply {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:$expected but was ${actual.session.type}"
            ).isEqualTo(expected)
    }

    fun hasSessionReplay(hasReplay: Boolean) = apply {
        assertThat(actual.session.hasReplay)
            .overridingErrorMessage(
                "Expected event data to have hasReplay $hasReplay but was ${actual.session.hasReplay}"
            )
            .isEqualTo(hasReplay)
    }

    fun hasNullView() = apply {
        assertThat(actual.view)
            .overridingErrorMessage(
                "Expected event data to have view equal to null"
            )
            .isNull()
    }

    fun hasViewId(expectedId: String) = apply {
        assertThat(actual.view?.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view?.id}"
            )
            .isEqualTo(expectedId)
    }

    fun hasName(expected: String) = apply {
        assertThat(actual.view?.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expected but was ${actual.view?.name}"
            )
            .isEqualTo(expected)
    }

    fun hasUrl(expected: String) = apply {
        assertThat(actual.view?.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expected but was ${actual.view?.url}"
            )
            .isEqualTo(expected)
    }

    fun hasNoSyntheticsTest() = apply {
        assertThat(actual.synthetics?.testId)
            .overridingErrorMessage(
                "Expected event to have no synthetics.testId but was ${actual.synthetics?.testId}"
            ).isNull()
        assertThat(actual.synthetics?.resultId)
            .overridingErrorMessage(
                "Expected event to have no synthetics.resultId but was ${actual.synthetics?.resultId}"
            ).isNull()
    }

    fun hasSyntheticsTest(testId: String, resultId: String) = apply {
        assertThat(actual.synthetics?.testId)
            .overridingErrorMessage(
                "Expected event to have synthetics.testId $testId but was ${actual.synthetics?.testId}"
            ).isEqualTo(testId)
        assertThat(actual.synthetics?.resultId)
            .overridingErrorMessage(
                "Expected event to have synthetics.resultId $resultId but was ${actual.synthetics?.resultId}"
            ).isEqualTo(resultId)
    }

    fun hasSource(source: VitalEvent.VitalEventSource?) = apply {
        assertThat(actual.source)
            .overridingErrorMessage(
                "Expected event to have a source %s" +
                    " instead it was %s",
                actual.source ?: "null",
                source ?: "null"
            )
            .isEqualTo(source)
        return this
    }

    fun hasAccountInfo(expected: AccountInfo?) = apply {
        assertThat(actual.account?.id)
            .overridingErrorMessage(
                "Expected RUM event to have account.id ${expected?.id} " +
                    "but was ${actual.account?.id}"
            )
            .isEqualTo(expected?.id)
        assertThat(actual.account?.name)
            .overridingErrorMessage(
                "Expected RUM event to have account.name ${expected?.name} " +
                    "but was ${actual.account?.name}"
            )
            .isEqualTo(expected?.name)
        assertThat(actual.account?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have account additional " +
                    "properties ${expected?.extraInfo} " +
                    "but was ${actual.account?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.extraInfo)
    }

    fun hasUserInfo(expected: UserInfo?) = apply {
        assertThat(actual.usr?.id)
            .overridingErrorMessage(
                "Expected event to have usr.id ${expected?.id} " +
                    "but was ${actual.usr?.id}"
            )
            .isEqualTo(expected?.id)
        assertThat(actual.usr?.name)
            .overridingErrorMessage(
                "Expected event to have usr.name ${expected?.name} " +
                    "but was ${actual.usr?.name}"
            )
            .isEqualTo(expected?.name)
        assertThat(actual.usr?.email)
            .overridingErrorMessage(
                "Expected event to have usr.email ${expected?.email} " +
                    "but was ${actual.usr?.email}"
            )
            .isEqualTo(expected?.email)
        assertThat(actual.usr?.additionalProperties)
            .overridingErrorMessage(
                "Expected event to have user additional" +
                    " properties ${expected?.additionalProperties} " +
                    "but was ${actual.usr?.additionalProperties}"
            )
            .containsExactlyInAnyOrderEntriesOf(expected?.additionalProperties)
    }

    fun hasDeviceInfo(
        name: String,
        model: String,
        brand: String,
        type: VitalEvent.DeviceType,
        architecture: String
    ) = apply {
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
    }

    fun hasOsInfo(
        name: String,
        version: String,
        versionMajor: String
    ) = apply {
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
    }

    fun hasConnectivityInfo(expected: NetworkInfo?) = apply {
        val expectedStatus = if (expected?.isConnected() == true) {
            VitalEvent.ConnectivityStatus.CONNECTED
        } else {
            VitalEvent.ConnectivityStatus.NOT_CONNECTED
        }
        val expectedInterfaces = when (expected?.connectivity) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(VitalEvent.Interface.ETHERNET)
            NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(VitalEvent.Interface.WIFI)
            NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(VitalEvent.Interface.WIMAX)
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(VitalEvent.Interface.BLUETOOTH)
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(VitalEvent.Interface.CELLULAR)

            NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(VitalEvent.Interface.OTHER)
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
    }

    fun hasVersion(version: String?) = apply {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected RUM event to have version: $version" +
                    " but instead was: ${actual.version}"
            )
            .isEqualTo(version)
    }

    fun hasServiceName(serviceName: String?) = apply {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected RUM event to have serviceName: $serviceName" +
                    " but instead was: ${actual.service}"
            )
            .isEqualTo(serviceName)
    }

    fun hasDDTags(ddTags: String) = apply {
        assertThat(actual.ddtags)
            .overridingErrorMessage(
                "Expected RUM event to have ddTags: $ddTags" +
                    " but instead was: ${actual.ddtags}"
            )
            .isEqualTo(ddTags)
    }

    companion object {
        internal fun assertThat(actual: VitalEvent): VitalEventAssert = VitalEventAssert(actual)
    }
}
