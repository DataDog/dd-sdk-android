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
import com.datadog.android.rum.internal.domain.scope.toViewSessionPrecondition
import com.datadog.android.rum.model.ViewEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.assertj.core.data.Percentage
import java.util.concurrent.TimeUnit

internal class ViewEventAssert(actual: ViewEvent) :
    AbstractObjectAssert<ViewEventAssert, ViewEvent>(
        actual,
        ViewEventAssert::class.java
    ) {

    fun hasTimestamp(
        expected: Long,
        offset: Long = TIMESTAMP_THRESHOLD_MS
    ): ViewEventAssert {
        assertThat(actual.date)
            .overridingErrorMessage(
                "Expected event to have timestamp $expected but was ${actual.date}"
            )
            .isCloseTo(expected, Offset.offset(offset))
        return this
    }

    fun hasName(expected: String): ViewEventAssert {
        assertThat(actual.view.name)
            .overridingErrorMessage(
                "Expected event data to have view.name $expected but was ${actual.view.name}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasUrl(expected: String): ViewEventAssert {
        assertThat(actual.view.url)
            .overridingErrorMessage(
                "Expected event data to have view.url $expected but was ${actual.view.url}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDuration(
        expected: Long
    ): ViewEventAssert {
        assertThat(actual.view.timeSpent)
            .overridingErrorMessage(
                "Expected event data to have view.time_spent $expected " +
                    "but was ${actual.view.timeSpent}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasDurationLowerThan(upperBound: Long): ViewEventAssert {
        assertThat(actual.view.timeSpent)
            .overridingErrorMessage(
                "Expected event data to have view.time_spent lower than $upperBound " +
                    "but was ${actual.view.timeSpent}"
            )
            .isLessThanOrEqualTo(upperBound)
        return this
    }

    fun hasDurationGreaterThan(upperBound: Long): ViewEventAssert {
        assertThat(actual.view.timeSpent)
            .overridingErrorMessage(
                "Expected event data to have view.time_spent greater than $upperBound " +
                    "but was ${actual.view.timeSpent}"
            )
            .isGreaterThanOrEqualTo(upperBound)
        return this
    }

    fun hasVersion(expected: Long): ViewEventAssert {
        assertThat(actual.dd.documentVersion)
            .overridingErrorMessage(
                "Expected event data to have dd.documentVersion $expected " +
                    "but was ${actual.dd.documentVersion}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasErrorCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.error.count)
            .overridingErrorMessage(
                "Expected event data to have view.error.count $expected " +
                    "but was ${actual.view.error.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasResourceCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.resource.count)
            .overridingErrorMessage(
                "Expected event data to have view.resource.count $expected " +
                    "but was ${actual.view.resource.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasActionCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.action.count)
            .overridingErrorMessage(
                "Expected event data to have view.action.count $expected " +
                    "but was ${actual.view.action.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFrustrationCount(expected: Long?): ViewEventAssert {
        assertThat(actual.view.frustration?.count)
            .overridingErrorMessage(
                "Expected event data to have view.frustration.count $expected " +
                    "but was ${actual.view.frustration?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasCrashCount(expected: Long?): ViewEventAssert {
        assertThat(actual.view.crash?.count)
            .overridingErrorMessage(
                "Expected event data to have view.crash.count $expected " +
                    "but was ${actual.view.crash?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLongTaskCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.longTask?.count)
            .overridingErrorMessage(
                "Expected event data to have view.longTask.count $expected " +
                    "but was ${actual.view.longTask?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasFrozenFrameCount(expected: Long): ViewEventAssert {
        assertThat(actual.view.frozenFrame?.count)
            .overridingErrorMessage(
                "Expected event data to have view.frozenFrame.count $expected " +
                    "but was ${actual.view.frozenFrame?.count}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasViewId(expectedId: String): ViewEventAssert {
        assertThat(actual.view.id)
            .overridingErrorMessage(
                "Expected event data to have view.id $expectedId but was ${actual.view.id}"
            )
            .isEqualTo(expectedId)
        return this
    }

    fun hasApplicationId(expected: String): ViewEventAssert {
        assertThat(actual.application.id)
            .overridingErrorMessage(
                "Expected event to have application.id $expected but was ${actual.application.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionId(expected: String): ViewEventAssert {
        assertThat(actual.session.id)
            .overridingErrorMessage(
                "Expected event to have session.id $expected but was ${actual.session.id}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasSessionActive(expected: Boolean): ViewEventAssert {
        assertThat(actual.session.isActive)
            .overridingErrorMessage(
                "Expected event to have session.isActive $expected but was ${actual.session.isActive}"
            ).isEqualTo(expected)
        return this
    }

    fun hasUserSession(): ViewEventAssert {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:user but was ${actual.session.type}"
            ).isEqualTo(ViewEvent.ViewEventSessionType.USER)
        return this
    }

    fun hasSyntheticsSession(): ViewEventAssert {
        assertThat(actual.session.type)
            .overridingErrorMessage(
                "Expected event to have session.type:synthetics but was ${actual.session.type}"
            ).isEqualTo(ViewEvent.ViewEventSessionType.SYNTHETICS)
        return this
    }

    fun hasNoSyntheticsTest(): ViewEventAssert {
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

    fun hasSyntheticsTest(testId: String, resultId: String): ViewEventAssert {
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

    fun hasLoadingTime(
        expected: Long?
    ): ViewEventAssert {
        assertThat(actual.view.loadingTime)
            .overridingErrorMessage(
                "Expected event to have loadingTime $expected" +
                    " but was ${actual.view.loadingTime}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNetworkSettledTime(
        expected: Long?
    ): ViewEventAssert {
        assertThat(actual.view.networkSettledTime)
            .overridingErrorMessage(
                "Expected event to have networkSettledTime $expected" +
                    " but was ${actual.view.networkSettledTime}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasInteractionToNextViewTime(
        expected: Long?
    ): ViewEventAssert {
        assertThat(actual.view.interactionToNextViewTime)
            .overridingErrorMessage(
                "Expected event to have interactionToNextViewTime $expected" +
                    " but was ${actual.view.interactionToNextViewTime}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasLoadingType(
        expected: ViewEvent.LoadingType?
    ): ViewEventAssert {
        assertThat(actual.view.loadingType)
            .overridingErrorMessage(
                "Expected event to have loadingType $expected" +
                    " but was ${actual.view.loadingType}"
            )
            .isEqualTo(expected)
        return this
    }

    fun isActive(
        expected: Boolean?
    ): ViewEventAssert {
        assertThat(actual.view.isActive)
            .overridingErrorMessage(
                "Expected event to have isActive $expected" +
                    " but was ${actual.view.isActive}"
            )
            .isEqualTo(expected)
        return this
    }

    fun isSlowRendered(
        expected: Boolean?
    ): ViewEventAssert {
        assertThat(actual.view.isSlowRendered)
            .overridingErrorMessage(
                "Expected event to have isSlowRendered $expected" +
                    " but was ${actual.view.isSlowRendered}"
            )
            .isEqualTo(expected)
        return this
    }

    fun hasNoCustomTimings(): ViewEventAssert {
        assertThat(actual.view.customTimings).isNull()
        return this
    }

    fun hasCustomTimings(customTimings: Map<String, Long>): ViewEventAssert {
        customTimings.entries.forEach { entry ->
            assertThat(actual.view.customTimings?.additionalProperties)
                .hasEntrySatisfying(entry.key) {
                    assertThat(it).isCloseTo(
                        entry.value,
                        Offset.offset(TimeUnit.MILLISECONDS.toNanos(10))
                    )
                }
        }

        return this
    }

    fun hasUserInfo(expected: UserInfo?): ViewEventAssert {
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
        return this
    }

    fun hasAccountInfo(expected: AccountInfo?): ViewEventAssert {
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

    fun hasUserInfo(expected: ViewEvent.Usr?): ViewEventAssert {
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

    fun hasCpuMetric(expectedTicks: Double?): ViewEventAssert {
        assertThat(actual.view.cpuTicksCount)
            .overridingErrorMessage(
                "Expected event to have view.cpu_ticks_count $expectedTicks " +
                    "but was ${actual.view.cpuTicksCount}"
            )
            .isEqualTo(expectedTicks)

        val expectedTicksPerSeconds = if (expectedTicks == null) {
            null
        } else if (actual.view.timeSpent < ONE_SECOND_NS) {
            null
        } else {
            (expectedTicks * ONE_SECOND_NS) / actual.view.timeSpent
        }
        assertThat(actual.view.cpuTicksPerSecond)
            .overridingErrorMessage(
                "Expected event to have view.cpu_ticks_per_second $expectedTicksPerSeconds " +
                    "but was ${actual.view.cpuTicksPerSecond}"
            )
            .isEqualTo(expectedTicksPerSeconds)
        return this
    }

    fun hasMemoryMetric(average: Double?, max: Double?): ViewEventAssert {
        assertThat(actual.view.memoryAverage)
            .overridingErrorMessage(
                "Expected event to have view.memory_average $average " +
                    "but was ${actual.view.memoryAverage}"
            )
            .isEqualTo(average)
        assertThat(actual.view.memoryMax)
            .overridingErrorMessage(
                "Expected event to have view.memory_max $max " +
                    "but was ${actual.view.memoryMax}"
            )
            .isEqualTo(max)
        return this
    }

    fun hasRefreshRateMetric(average: Double?, min: Double?): ViewEventAssert {
        if (average == null) {
            assertThat(actual.view.refreshRateAverage as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_average $average " +
                        "but was ${actual.view.refreshRateAverage}"
                )
                .isNull()
        } else {
            assertThat(actual.view.refreshRateAverage as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_average $average " +
                        "but was ${actual.view.refreshRateAverage}"
                )
                .isCloseTo(average, Percentage.withPercentage(1.0))
        }
        if (min == null) {
            assertThat(actual.view.refreshRateMin as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_min $min " +
                        "but was ${actual.view.refreshRateMin}"
                )
                .isNull()
        } else {
            assertThat(actual.view.refreshRateMin as? Double)
                .overridingErrorMessage(
                    "Expected event to have view.refresh_rate_min $min " +
                        "but was ${actual.view.refreshRateMin}"
                )
                .isCloseTo(min, Percentage.withPercentage(1.0))
        }
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

    fun hasConnectivityInfo(expected: NetworkInfo?): ViewEventAssert {
        val expectedStatus = if (expected?.isConnected() == true) {
            ViewEvent.Status.CONNECTED
        } else {
            ViewEvent.Status.NOT_CONNECTED
        }
        val expectedInterfaces = when (expected?.connectivity) {
            NetworkInfo.Connectivity.NETWORK_ETHERNET -> listOf(ViewEvent.Interface.ETHERNET)
            NetworkInfo.Connectivity.NETWORK_WIFI -> listOf(ViewEvent.Interface.WIFI)
            NetworkInfo.Connectivity.NETWORK_WIMAX -> listOf(ViewEvent.Interface.WIMAX)
            NetworkInfo.Connectivity.NETWORK_BLUETOOTH -> listOf(ViewEvent.Interface.BLUETOOTH)
            NetworkInfo.Connectivity.NETWORK_2G,
            NetworkInfo.Connectivity.NETWORK_3G,
            NetworkInfo.Connectivity.NETWORK_4G,
            NetworkInfo.Connectivity.NETWORK_5G,
            NetworkInfo.Connectivity.NETWORK_MOBILE_OTHER,
            NetworkInfo.Connectivity.NETWORK_CELLULAR -> listOf(ViewEvent.Interface.CELLULAR)
            NetworkInfo.Connectivity.NETWORK_OTHER -> listOf(ViewEvent.Interface.OTHER)
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

    fun hasStartReason(reason: RumSessionScope.StartReason): ViewEventAssert {
        assertThat(actual.dd.session?.sessionPrecondition)
            .overridingErrorMessage(
                "Expected event to have a session sessionPrecondition of ${reason.name} " +
                    "but was ${actual.dd.session?.sessionPrecondition}"
            )
            .isEqualTo(reason.toViewSessionPrecondition())
        return this
    }

    fun hasSource(source: ViewEvent.ViewEventSource?): ViewEventAssert {
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

    fun hasDeviceInfo(
        name: String,
        model: String,
        brand: String,
        type: ViewEvent.DeviceType,
        architecture: String
    ): ViewEventAssert {
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

    fun hasSlownessInfo(
        slowFrames: List<ViewEvent.SlowFrame>,
        slowFramesRate: Double? = null,
        freezeRate: Double? = null
    ): ViewEventAssert = apply {
        assertThat(actual.view.slowFrames)
            .overridingErrorMessage(
                "Expected event data to have slowFrames $slowFrames but was ${actual.view.slowFrames}"
            )
            .isEqualTo(slowFrames)
        assertThat(actual.view.slowFramesRate)
            .overridingErrorMessage(
                "Expected event data to have slowFramesRate $slowFramesRate but was ${actual.view.slowFramesRate}"
            )
            .isEqualTo(slowFramesRate)
        assertThat(actual.view.freezeRate)
            .overridingErrorMessage(
                "Expected event data to have freezeRate $freezeRate but was ${actual.view.freezeRate}"
            )
            .isEqualTo(freezeRate)
    }

    fun hasOsInfo(
        name: String,
        version: String,
        versionMajor: String
    ): ViewEventAssert {
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

    fun hasReplayStats(replayStats: ViewEvent.ReplayStats) {
        assertThat(actual.dd.replayStats)
            .overridingErrorMessage(
                "Expected event data to have replay stats $replayStats " +
                    "but was ${actual.dd.replayStats}"
            )
            .isEqualTo(replayStats)
    }

    fun hasFlutterBuildTime(value: ViewEvent.FlutterBuildTime?): ViewEventAssert {
        performanceMetricsAreClose("flutterBuildTime", actual.view.flutterBuildTime, value)
        return this
    }

    fun hasFlutterRasterTime(value: ViewEvent.FlutterBuildTime?): ViewEventAssert {
        performanceMetricsAreClose("flutterRasterTime", actual.view.flutterRasterTime, value)
        return this
    }

    fun hasJsRefreshRate(value: ViewEvent.FlutterBuildTime?): ViewEventAssert {
        performanceMetricsAreClose("jsRefreshRate", actual.view.jsRefreshRate, value)
        return this
    }

    private fun performanceMetricsAreClose(
        metric: String,
        actualBuildTime: ViewEvent.FlutterBuildTime?,
        expectedBuildTime: ViewEvent.FlutterBuildTime?
    ) {
        if (actualBuildTime == null || expectedBuildTime == null) {
            assertThat(actualBuildTime)
                .overridingErrorMessage(
                    "Expected the event data to have view.$metric of" +
                        " $expectedBuildTime but was $actualBuildTime"
                )
                .isEqualTo(expectedBuildTime)
        } else {
            assertThat(actualBuildTime.min.toDouble())
                .overridingErrorMessage(
                    "Expected the event data to have view.$metric.min to be close to" +
                        "${expectedBuildTime.min} but was ${actualBuildTime.min}"
                )
                .isCloseTo(expectedBuildTime.min.toDouble(), Percentage.withPercentage(0.1))

            assertThat(actualBuildTime.max.toDouble())
                .overridingErrorMessage(
                    "Expected the event data to have view.$metric.max to be close to" +
                        " ${expectedBuildTime.max} but was ${actualBuildTime.max}"
                )
                .isCloseTo(expectedBuildTime.max.toDouble(), Percentage.withPercentage(0.1))

            assertThat(actualBuildTime.average.toDouble())
                .overridingErrorMessage(
                    "Expected the event data to have view.$metric.min to be close to" +
                        " ${expectedBuildTime.average} but was ${actualBuildTime.average}"
                )
                .isCloseTo(expectedBuildTime.average.toDouble(), Percentage.withPercentage(0.1))

            assertThat(actualBuildTime.metricMax)
                .overridingErrorMessage(
                    "Expected the event data to have view.$metric.metricMax to be equal to" +
                        " ${expectedBuildTime.metricMax} but was ${actualBuildTime.metricMax}"
                )
                .isEqualTo(expectedBuildTime.metricMax)
        }
    }

    fun hasFBCTime(fbc: Long): ViewEventAssert {
        assertThat(actual.view.performance?.fbc?.timestamp)
            .overridingErrorMessage(
                "Expected event to have view.fbc $fbc but was ${actual.view.performance?.fbc}"
            )
            .isEqualTo(fbc)
        return this
    }

    fun hasServiceName(serviceName: String?): ViewEventAssert {
        assertThat(actual.service)
            .overridingErrorMessage(
                "Expected RUM event to have serviceName: $serviceName" +
                    " but instead was: ${actual.service}"
            )
            .isEqualTo(serviceName)
        return this
    }

    fun hasVersion(version: String?): ViewEventAssert {
        assertThat(actual.version)
            .overridingErrorMessage(
                "Expected RUM event to have version: $version" +
                    " but instead was: ${actual.version}"
            )
            .isEqualTo(version)
        return this
    }

    fun hasFeatureFlag(flagName: String, flagValue: Any): ViewEventAssert {
        assertThat(actual.featureFlags)
            .isNotNull
        assertThat(actual.featureFlags?.additionalProperties)
            .contains(Assertions.entry(flagName, flagValue))
        return this
    }

    fun hasSampleRate(sampleRate: Float?): ViewEventAssert {
        assertThat(actual.dd.configuration?.sessionSampleRate ?: 0)
            .overridingErrorMessage(
                "Expected RUM event to have sample rate: $sampleRate" +
                    " but instead was: ${actual.dd.configuration?.sessionSampleRate}"
            )
            .isEqualTo(sampleRate)
        return this
    }

    companion object {

        internal val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1)
        internal const val TIMESTAMP_THRESHOLD_MS = 50L
        internal fun assertThat(actual: ViewEvent): ViewEventAssert =
            ViewEventAssert(actual)
    }
}
