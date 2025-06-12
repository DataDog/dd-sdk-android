/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.assertj

import com.datadog.android.api.context.AccountInfo
import com.datadog.android.api.context.NetworkInfo
import com.datadog.android.api.context.UserInfo
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.domain.scope.RumSessionScope
import com.datadog.android.rum.internal.domain.scope.isConnected
import com.datadog.android.rum.internal.domain.scope.toResourceMethod
import com.datadog.android.rum.internal.domain.scope.toResourceSessionPrecondition
import com.datadog.android.rum.internal.domain.scope.toSchemaType
import com.datadog.android.rum.model.ResourceEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class ResourceEventAssert(actual: ResourceEvent) :
    AbstractObjectAssert<ResourceEventAssert, ResourceEvent>(
        actual,
        ResourceEventAssert::class.java
    ) {

    fun hasId(expected: String?): ResourceEventAssert {
        assertThat(actual.resource.id)
            .overridingErrorMessage(
                "Expected event data to have resource.id $expected " +
                    "but was ${actual.resource.id}"
            )
            .isNotEqualTo(RumContext.NULL_UUID)
            .isEqualTo(expected)
        return this
    }

    fun hasTimestamp(
        expected: Long,
        offset: Long = TIMESTAMP_THRESHOLD_MS
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

    fun hasMethod(expected: RumResourceMethod): ResourceEventAssert {
        assertThat(actual.resource.method)
            .overridingErrorMessage(
                "Expected event data to have resource.method $expected " +
                    "but was ${actual.resource.method}"
            )
            .isEqualTo(expected.toResourceMethod())
        return this
    }

    fun hasStatusCode(expected: Long?): ResourceEventAssert {
        assertThat(actual.resource.statusCode)
            .overridingErrorMessage(
                "Expected event data to have resource.status_code $expected " +
                    "but was ${actual.resource.statusCode}"
            )
            .isEqualTo(expected)
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

    fun hasDuration(duration: Long): ResourceEventAssert {
        assertThat(actual.resource.duration)
            .overridingErrorMessage(
                "Expected event data to have resource.duration $duration " +
                    "but was ${actual.resource.duration}"
            )
            .isEqualTo(duration)
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

    fun hasAccountInfo(expected: AccountInfo?): ResourceEventAssert {
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

    fun hasView(expected: RumContext): ResourceEventAssert {
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

    fun hasUserSession(): ResourceEventAssert {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:user but was ${actual.session.type}"
            ).isEqualTo(ResourceEvent.ResourceEventSessionType.USER)
        return this
    }

    fun hasSyntheticsSession(): ResourceEventAssert {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:synthetics but was ${actual.session.type}"
            ).isEqualTo(ResourceEvent.ResourceEventSessionType.SYNTHETICS)
        return this
    }

    fun hasNoSyntheticsTest(): ResourceEventAssert {
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

    fun hasSyntheticsTest(testId: String, resultId: String): ResourceEventAssert {
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

    fun hasActionId(expected: String?): ResourceEventAssert {
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

    fun hasTraceId(expected: String?): ResourceEventAssert {
        assertThat(actual.dd.traceId)
            .overridingErrorMessage(
                "Expected event data to have _dd.trace_id $expected but was ${actual.dd.traceId}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSpanId(expected: String?): ResourceEventAssert {
        assertThat(actual.dd.spanId)
            .overridingErrorMessage(
                "Expected event data to have _dd.span_id $expected but was ${actual.dd.spanId}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasRulePsr(expected: Number?): ResourceEventAssert {
        assertThat(actual.dd.rulePsr)
            .overridingErrorMessage(
                "Expected event data to have _dd.rule_psr $expected but was ${actual.dd.rulePsr}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasProviderType(expected: ResourceEvent.ProviderType): ResourceEventAssert {
        assertThat(actual.resource.provider?.type)
            .overridingErrorMessage(
                "Expected event data to have resource provider type $expected " +
                    "but was ${actual.resource.provider?.type}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasProviderDomain(expected: String): ResourceEventAssert {
        assertThat(actual.resource.provider?.domain)
            .overridingErrorMessage(
                "Expected event data to have resource provider domain $expected " +
                    "but was ${actual.resource.provider?.domain}"
            )
            .isEqualTo(expected)
        return this
    }

    fun doesNotHaveAResourceProvider(): ResourceEventAssert {
        assertThat(actual.resource.provider)
            .overridingErrorMessage(
                "Expected event data to not have a resource provider"
            ).isNull()
        return this
    }

    fun hasStartReason(reason: RumSessionScope.StartReason): ResourceEventAssert {
        assertThat(actual.dd.session?.sessionPrecondition)
            .overridingErrorMessage(
                "Expected event to have a session sessionPrecondition of ${reason.name} " +
                    "but was ${actual.dd.session?.sessionPrecondition}"
            )
            .isEqualTo(reason.toResourceSessionPrecondition())
        return this
    }

    fun hasSource(source: ResourceEvent.ResourceEventSource?): ResourceEventAssert {
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
        type: ResourceEvent.DeviceType,
        architecture: String
    ): ResourceEventAssert {
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
    ): ResourceEventAssert {
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

    fun hasServiceName(serviceName: String?): ResourceEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected RUM event to have serviceName: $serviceName" +
                    " but instead was: ${actual.service}"
            )
            .isEqualTo(serviceName)
        return this
    }

    fun hasVersion(version: String?): ResourceEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected RUM event to have version: $version" +
                    " but instead was: ${actual.version}"
            )
            .isEqualTo(version)
        return this
    }

    fun hasSampleRate(sampleRate: Float?): ResourceEventAssert {
        assertThat(actual.dd.configuration?.sessionSampleRate ?: 0)
            .overridingErrorMessage(
                "Expected RUM event to have sample rate: $sampleRate" +
                    " but instead was: ${actual.dd.configuration?.sessionSampleRate}"
            )
            .isEqualTo(sampleRate)
        return this
    }

    fun hasGraphql(
        operationType: ResourceEvent.OperationType,
        operationName: String?,
        payload: String?,
        variables: String?
    ): ResourceEventAssert {
        assertThat(actual.resource.graphql?.operationType)
            .overridingErrorMessage(
                "Expected event data to have resource.graphql.operationType $operationType " +
                    "but was ${actual.resource.graphql?.operationType}"
            )
            .isEqualTo(operationType)

        assertThat(actual.resource.graphql?.operationName)
            .overridingErrorMessage(
                "Expected event data to have resource.graphql.operationName $operationName " +
                    "but was ${actual.resource.graphql?.operationName}"
            )
            .isEqualTo(operationName)

        assertThat(actual.resource.graphql?.payload)
            .overridingErrorMessage(
                "Expected event data to have resource.graphql.payload $payload " +
                    "but was ${actual.resource.graphql?.payload}"
            )
            .isEqualTo(payload)

        assertThat(actual.resource.graphql?.variables)
            .overridingErrorMessage(
                "Expected event data to have resource.graphql.variables $variables " +
                    "but was ${actual.resource.graphql?.variables}"
            )
            .isEqualTo(variables)

        return this
    }

    companion object {

        internal const val TIMESTAMP_THRESHOLD_MS = 50L
        internal fun assertThat(actual: ResourceEvent): ResourceEventAssert =
            ResourceEventAssert(actual)
    }
}
