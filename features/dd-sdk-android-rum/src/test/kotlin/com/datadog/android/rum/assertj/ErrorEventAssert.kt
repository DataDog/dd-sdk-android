/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.isConnected
import com.datadog.android.rum.internal.domain.scope.toErrorMethod
import com.datadog.android.rum.internal.domain.scope.toErrorSessionPrecondition
import com.datadog.android.rum.internal.domain.scope.toSchemaSource
import com.datadog.android.rum.model.ErrorEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.assertj.core.data.Offset

internal class ErrorEventAssert(actual: ErrorEvent) :
    AbstractObjectAssert<ErrorEventAssert, ErrorEvent>(
        actual,
        ErrorEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = TIMESTAMP_THRESHOLD_MS
    ): ErrorEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasErrorSource(expected: RumErrorSource): ErrorEventAssert {
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

    fun hasStackTrace(expected: String?): ErrorEventAssert {
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
        expectedMethod: RumResourceMethod,
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
            .isEqualTo(expectedMethod.toErrorMethod())
        assertThat(actual.error.resource?.statusCode)
            .overridingErrorMessage(
                "Expected event data to have error.resource.statusCode $expectedStatusCode " +
                    "but was ${actual.error.resource?.statusCode}"
            )
            .isEqualTo(expectedStatusCode)
        return this
    }

    fun hasNoUserInfo(): ErrorEventAssert {
        assertThat(actual.usr)
            .overridingErrorMessage(
                "Expected RUM event to have no user information but was ${actual.usr}"
            )
            .isNull()
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

    fun hasView(
        expectedId: String?,
        expectedName: String?,
        expectedUrl: String?
    ): ErrorEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId.orEmpty())
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expectedName but was ${actual.view.name}"
            )
            .isEqualTo(expectedName)
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expectedUrl but was ${actual.view.url}"
            )
            .isEqualTo(expectedUrl.orEmpty())
        return this
    }

    fun hasView(expected: RumContext): ErrorEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id ${expected.viewId} " +
                    "but was ${actual.view.id}"
            )
            .isEqualTo(expected.viewId.orEmpty())
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name ${expected.viewName} " +
                    "but was ${actual.view.name}"
            )
            .isEqualTo(expected.viewName)
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url ${expected.viewUrl} " +
                    "but was ${actual.view.url}"
            )
            .isEqualTo(expected.viewUrl.orEmpty())
        return this
    }

    fun hasApplicationId(expected: String): ErrorEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected event to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ErrorEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected event to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUserSession(): ErrorEventAssert {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:user but was ${actual.session.type}"
            ).isEqualTo(ErrorEvent.ErrorEventSessionType.USER)
        return this
    }

    fun hasSyntheticsSession(): ErrorEventAssert {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:synthetics but was ${actual.session.type}"
            ).isEqualTo(ErrorEvent.ErrorEventSessionType.SYNTHETICS)
        return this
    }

    fun hasNoSyntheticsTest(): ErrorEventAssert {
        assertThat(actual.synthetics?.testId)
            .overridingErrorMessage(
                "Expected event to have no synthetics.testId but was ${actual.synthetics?.testId}"
            ).isNull()
        assertThat(actual.synthetics?.resultId)
            .overridingErrorMessage(
                "Expected event to have no synthetics.resultId but was ${actual.synthetics?.resultId}"
            ).isNull()
        return this
    }

    fun hasSyntheticsTest(testId: String, resultId: String): ErrorEventAssert {
        assertThat(actual.synthetics?.testId)
            .overridingErrorMessage(
                "Expected event to have synthetics.testId $testId but was ${actual.synthetics?.testId}"
            ).isEqualTo(testId)
        assertThat(actual.synthetics?.resultId)
            .overridingErrorMessage(
                "Expected event to have synthetics.resultId $resultId but was ${actual.synthetics?.resultId}"
            ).isEqualTo(resultId)
        return this
    }

    fun hasActionId(expected: String?): ErrorEventAssert {
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

    fun hasProviderType(expected: ErrorEvent.ProviderType): ErrorEventAssert {
        assertThat(actual.error.resource?.provider?.type)
            .overridingErrorMessage(
                "Expected event data to have resource provider type $expected " +
                    "but was ${actual.error.resource?.provider?.type}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasProviderDomain(expected: String): ErrorEventAssert {
        assertThat(actual.error.resource?.provider?.domain)
            .overridingErrorMessage(
                "Expected event data to have resource provider domain $expected " +
                    "but was ${actual.error.resource?.provider?.domain}"
            )
            .isEqualTo(expected)
        return this
    }

    fun doesNotHaveAResourceProvider(): ErrorEventAssert {
        assertThat(actual.error.resource?.provider)
            .overridingErrorMessage(
                "Expected event data to not have a resource provider"
            ).isNull()
        return this
    }

    fun hasConnectivityStatus(expected: ErrorEvent.Status?): ErrorEventAssert {
        assertThat(actual.connectivity?.status)
            .overridingErrorMessage(
                "Expected event data to have connectivity status: $expected" +
                    " but was: ${actual.connectivity?.status} "
            )
            .isEqualTo(expected)
        return this
    }

    fun hasConnectivityInterface(expected: List<ErrorEvent.Interface>?): ErrorEventAssert {
        val interfaces = actual.connectivity?.interfaces
        assertThat(interfaces)
            .overridingErrorMessage(
                "Expected event data to have connectivity interfaces: $expected" +
                    " but was: $interfaces "
            )
            .isEqualTo(expected)
        return this
    }

    fun hasConnectivityCellular(expected: ErrorEvent.Cellular?): ErrorEventAssert {
        assertThat(actual.connectivity?.cellular)
            .overridingErrorMessage(
                "Expected event data to have connectivity cellular: $expected" +
                    " but was: ${actual.connectivity?.cellular} "
            )
            .isEqualTo(expected)
        return this
    }

    fun hasErrorType(expected: String?): ErrorEventAssert {
        assertThat(actual.error.type)
            .overridingErrorMessage(
                "Expected event data to have error type $expected" +
                    " but was ${actual.error.type}"
            ).isEqualTo(expected)
        return this
    }

    fun hasErrorSourceType(expected: ErrorEvent.SourceType?): ErrorEventAssert {
        assertThat(actual.error.sourceType)
            .overridingErrorMessage(
                "Expected event data to have error source type $expected" +
                    " but was ${actual.error.sourceType}"
            ).isEqualTo(expected)
        return this
    }

    fun hasLiteSessionPlan(): ErrorEventAssert {
        assertThat(actual.dd.session?.plan)
            .overridingErrorMessage(
                "Expected event to have a session plan of 1 instead it was %s",
                actual.dd.session?.plan ?: "null"
            )
            .isEqualTo(ErrorEvent.Plan.PLAN_1)
        return this
    }

    fun hasStartReason(reason: RumSessionScope.StartReason): ErrorEventAssert {
        assertThat(actual.dd.session?.sessionPrecondition)
            .overridingErrorMessage(
                "Expected event to have a session sessionPrecondition of ${reason.name} " +
                    "but was ${actual.dd.session?.sessionPrecondition}"
            )
            .isEqualTo(reason.toErrorSessionPrecondition())
        return this
    }

    fun hasSource(source: ErrorEvent.ErrorEventSource?): ErrorEventAssert {
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
        type: ErrorEvent.DeviceType,
        architecture: String
    ): ErrorEventAssert {
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
    ): ErrorEventAssert {
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

    fun hasServiceName(serviceName: String?): ErrorEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected RUM event to have serviceName: $serviceName" +
                    " but instead was: ${actual.service}"
            )
            .isEqualTo(serviceName)
        return this
    }

    fun hasVersion(version: String?): ErrorEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected RUM event to have version: $version" +
                    " but instead was: ${actual.version}"
            )
            .isEqualTo(version)
        return this
    }

    fun hasFeatureFlag(flagName: String, flagValue: Any): ErrorEventAssert {
        assertThat(actual.featureFlags)
            .isNotNull
        assertThat(actual.featureFlags?.additionalProperties)
            .contains(entry(flagName, flagValue))
        return this
    }

    fun hasSampleRate(sampleRate: Float?): ErrorEventAssert {
        assertThat(actual.dd.configuration?.sessionSampleRate ?: 0)
            .overridingErrorMessage(
                "Expected RUM event to have sample rate: $sampleRate" +
                    " but instead was: ${actual.dd.configuration?.sessionSampleRate}"
            )
            .isEqualTo(sampleRate)
        return this
    }

    fun hasBuildId(buildId: String?): ErrorEventAssert {
        assertThat(actual.buildId)
            .overridingErrorMessage(
                "Expected RUM event to have build ID: $buildId" +
                    " but instead was ${actual.buildId}"
            )
            .isEqualTo(buildId)
        return this
    }

    companion object {
        internal const val TIMESTAMP_THRESHOLD_MS = 50L
        internal fun assertThat(actual: ErrorEvent): ErrorEventAssert =
            ErrorEventAssert(actual)
    }
}
