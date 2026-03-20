/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.assertj

import com.datadog.android.rum.model.ViewUpdateEvent
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

class ViewUpdateAssert(
    actual: ViewUpdateEvent
) : AbstractObjectAssert<ViewUpdateAssert, ViewUpdateEvent>(actual, ViewUpdateAssert::class.java) {

    fun hasService(service: String?): ViewUpdateAssert {
        assertThat(actual.service)
            .overridingErrorMessage("Expected service to be <%s> but was <%s>", service, actual.service)
            .isEqualTo(service)
        return this
    }

    fun hasVersion(version: String?): ViewUpdateAssert {
        assertThat(actual.version)
            .overridingErrorMessage("Expected version to be <%s> but was <%s>", version, actual.version)
            .isEqualTo(version)
        return this
    }

    fun hasBuildVersion(buildVersion: String?): ViewUpdateAssert {
        assertThat(actual.buildVersion)
            .overridingErrorMessage("Expected buildVersion to be <%s> but was <%s>", buildVersion, actual.buildVersion)
            .isEqualTo(buildVersion)
        return this
    }

    fun hasBuildId(buildId: String?): ViewUpdateAssert {
        assertThat(actual.buildId)
            .overridingErrorMessage("Expected buildId to be <%s> but was <%s>", buildId, actual.buildId)
            .isEqualTo(buildId)
        return this
    }

    fun hasDdtags(ddtags: String?): ViewUpdateAssert {
        assertThat(actual.ddtags)
            .overridingErrorMessage("Expected ddtags to be <%s> but was <%s>", ddtags, actual.ddtags)
            .isEqualTo(ddtags)
        return this
    }

    fun hasDate(date: Long): ViewUpdateAssert {
        assertThat(actual.date)
            .overridingErrorMessage("Expected date to be <%s> but was <%s>", date, actual.date)
            .isEqualTo(date)
        return this
    }

    fun hasSource(source: ViewUpdateEvent.ViewUpdateEventSource?): ViewUpdateAssert {
        assertThat(actual.source)
            .overridingErrorMessage("Expected source to be <%s> but was <%s>", source, actual.source)
            .isEqualTo(source)
        return this
    }

    fun hasType(type: String): ViewUpdateAssert {
        assertThat(actual.type)
            .overridingErrorMessage("Expected type to be <%s> but was <%s>", type, actual.type)
            .isEqualTo(type)
        return this
    }

    fun view(block: ViewUpdateEventViewAssert.() -> Unit): ViewUpdateAssert {
        ViewUpdateEventViewAssert(actual.view).block()
        return this
    }

    fun session(block: ViewUpdateEventSessionAssert.() -> Unit): ViewUpdateAssert {
        ViewUpdateEventSessionAssert(actual.session).block()
        return this
    }

    fun application(block: ApplicationAssert.() -> Unit): ViewUpdateAssert {
        ApplicationAssert(actual.application).block()
        return this
    }

    fun dd(block: DdAssert.() -> Unit): ViewUpdateAssert {
        DdAssert(actual.dd).block()
        return this
    }

    fun container(block: ContainerAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.container)
            .overridingErrorMessage("Expected container to be non-null but was null")
            .isNotNull
        ContainerAssert(actual.container!!).block()
        return this
    }

    fun hasNoContainer(): ViewUpdateAssert {
        assertThat(actual.container)
            .overridingErrorMessage("Expected container to be null but was <%s>", actual.container)
            .isNull()
        return this
    }

    fun featureFlags(block: FeatureFlagsAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.featureFlags)
            .overridingErrorMessage("Expected featureFlags to be non-null but was null")
            .isNotNull
        FeatureFlagsAssert(actual.featureFlags!!).block()
        return this
    }

    fun hasNoFeatureFlags(): ViewUpdateAssert {
        assertThat(actual.featureFlags)
            .overridingErrorMessage("Expected featureFlags to be null but was <%s>", actual.featureFlags)
            .isNull()
        return this
    }

    fun privacy(block: PrivacyAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.privacy)
            .overridingErrorMessage("Expected privacy to be non-null but was null")
            .isNotNull
        PrivacyAssert(actual.privacy!!).block()
        return this
    }

    fun hasNoPrivacy(): ViewUpdateAssert {
        assertThat(actual.privacy)
            .overridingErrorMessage("Expected privacy to be null but was <%s>", actual.privacy)
            .isNull()
        return this
    }

    fun display(block: DisplayAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.display)
            .overridingErrorMessage("Expected display to be non-null but was null")
            .isNotNull
        DisplayAssert(actual.display!!).block()
        return this
    }

    fun hasNoDisplay(): ViewUpdateAssert {
        assertThat(actual.display)
            .overridingErrorMessage("Expected display to be null but was <%s>", actual.display)
            .isNull()
        return this
    }

    fun usr(block: UsrAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.usr)
            .overridingErrorMessage("Expected usr to be non-null but was null")
            .isNotNull
        UsrAssert(actual.usr!!).block()
        return this
    }

    fun hasNoUsr(): ViewUpdateAssert {
        assertThat(actual.usr)
            .overridingErrorMessage("Expected usr to be null but was <%s>", actual.usr)
            .isNull()
        return this
    }

    fun account(block: AccountAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.account)
            .overridingErrorMessage("Expected account to be non-null but was null")
            .isNotNull
        AccountAssert(actual.account!!).block()
        return this
    }

    fun hasNoAccount(): ViewUpdateAssert {
        assertThat(actual.account)
            .overridingErrorMessage("Expected account to be null but was <%s>", actual.account)
            .isNull()
        return this
    }

    fun connectivity(block: ConnectivityAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.connectivity)
            .overridingErrorMessage("Expected connectivity to be non-null but was null")
            .isNotNull
        ConnectivityAssert(actual.connectivity!!).block()
        return this
    }

    fun hasNoConnectivity(): ViewUpdateAssert {
        assertThat(actual.connectivity)
            .overridingErrorMessage("Expected connectivity to be null but was <%s>", actual.connectivity)
            .isNull()
        return this
    }

    fun synthetics(block: SyntheticsAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.synthetics)
            .overridingErrorMessage("Expected synthetics to be non-null but was null")
            .isNotNull
        SyntheticsAssert(actual.synthetics!!).block()
        return this
    }

    fun hasNoSynthetics(): ViewUpdateAssert {
        assertThat(actual.synthetics)
            .overridingErrorMessage("Expected synthetics to be null but was <%s>", actual.synthetics)
            .isNull()
        return this
    }

    fun ciTest(block: CiTestAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.ciTest)
            .overridingErrorMessage("Expected ciTest to be non-null but was null")
            .isNotNull
        CiTestAssert(actual.ciTest!!).block()
        return this
    }

    fun hasNoCiTest(): ViewUpdateAssert {
        assertThat(actual.ciTest)
            .overridingErrorMessage("Expected ciTest to be null but was <%s>", actual.ciTest)
            .isNull()
        return this
    }

    fun os(block: OsAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.os)
            .overridingErrorMessage("Expected os to be non-null but was null")
            .isNotNull
        OsAssert(actual.os!!).block()
        return this
    }

    fun hasNoOs(): ViewUpdateAssert {
        assertThat(actual.os)
            .overridingErrorMessage("Expected os to be null but was <%s>", actual.os)
            .isNull()
        return this
    }

    fun device(block: DeviceAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.device)
            .overridingErrorMessage("Expected device to be non-null but was null")
            .isNotNull
        DeviceAssert(actual.device!!).block()
        return this
    }

    fun hasNoDevice(): ViewUpdateAssert {
        assertThat(actual.device)
            .overridingErrorMessage("Expected device to be null but was <%s>", actual.device)
            .isNull()
        return this
    }

    fun context(block: FeatureFlagsAssert.() -> Unit): ViewUpdateAssert {
        assertThat(actual.context)
            .overridingErrorMessage("Expected context to be non-null but was null")
            .isNotNull
        FeatureFlagsAssert(actual.context!!).block()
        return this
    }

    fun hasNoContext(): ViewUpdateAssert {
        assertThat(actual.context)
            .overridingErrorMessage("Expected context to be null but was <%s>", actual.context)
            .isNull()
        return this
    }

    // region Nested assert classes

    class ViewUpdateEventViewAssert(
        actual: ViewUpdateEvent.ViewUpdateEventView
    ) : AbstractObjectAssert<ViewUpdateEventViewAssert, ViewUpdateEvent.ViewUpdateEventView>(
        actual,
        ViewUpdateEventViewAssert::class.java
    ) {
        fun hasId(id: String): ViewUpdateEventViewAssert {
            assertThat(actual.id)
                .overridingErrorMessage("Expected view.id to be <%s> but was <%s>", id, actual.id)
                .isEqualTo(id)
            return this
        }

        fun hasUrl(url: String): ViewUpdateEventViewAssert {
            assertThat(actual.url)
                .overridingErrorMessage("Expected view.url to be <%s> but was <%s>", url, actual.url)
                .isEqualTo(url)
            return this
        }

        fun hasName(name: String?): ViewUpdateEventViewAssert {
            assertThat(actual.name)
                .overridingErrorMessage("Expected view.name to be <%s> but was <%s>", name, actual.name)
                .isEqualTo(name)
            return this
        }

        fun hasReferrer(referrer: String?): ViewUpdateEventViewAssert {
            assertThat(actual.referrer)
                .overridingErrorMessage("Expected view.referrer to be <%s> but was <%s>", referrer, actual.referrer)
                .isEqualTo(referrer)
            return this
        }

        fun hasLoadingTime(loadingTime: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.loadingTime)
                .overridingErrorMessage(
                    "Expected view.loadingTime to be <%s> but was <%s>",
                    loadingTime,
                    actual.loadingTime
                )
                .isEqualTo(loadingTime)
            return this
        }

        fun hasNetworkSettledTime(networkSettledTime: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.networkSettledTime)
                .overridingErrorMessage(
                    "Expected view.networkSettledTime to be <%s> but was <%s>",
                    networkSettledTime,
                    actual.networkSettledTime
                )
                .isEqualTo(networkSettledTime)
            return this
        }

        fun hasInteractionToNextViewTime(interactionToNextViewTime: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.interactionToNextViewTime)
                .overridingErrorMessage(
                    "Expected view.interactionToNextViewTime to be <%s> but was <%s>",
                    interactionToNextViewTime,
                    actual.interactionToNextViewTime
                )
                .isEqualTo(interactionToNextViewTime)
            return this
        }

        fun hasLoadingType(loadingType: ViewUpdateEvent.LoadingType?): ViewUpdateEventViewAssert {
            assertThat(actual.loadingType)
                .overridingErrorMessage(
                    "Expected view.loadingType to be <%s> but was <%s>",
                    loadingType,
                    actual.loadingType
                )
                .isEqualTo(loadingType)
            return this
        }

        fun hasTimeSpent(timeSpent: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.timeSpent)
                .overridingErrorMessage("Expected view.timeSpent to be <%s> but was <%s>", timeSpent, actual.timeSpent)
                .isEqualTo(timeSpent)
            return this
        }

        fun hasFirstContentfulPaint(firstContentfulPaint: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.firstContentfulPaint)
                .overridingErrorMessage(
                    "Expected view.firstContentfulPaint to be <%s> but was <%s>",
                    firstContentfulPaint,
                    actual.firstContentfulPaint
                )
                .isEqualTo(firstContentfulPaint)
            return this
        }

        fun hasLargestContentfulPaint(largestContentfulPaint: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.largestContentfulPaint)
                .overridingErrorMessage(
                    "Expected view.largestContentfulPaint to be <%s> but was <%s>",
                    largestContentfulPaint,
                    actual.largestContentfulPaint
                )
                .isEqualTo(largestContentfulPaint)
            return this
        }

        fun hasLargestContentfulPaintTargetSelector(selector: String?): ViewUpdateEventViewAssert {
            assertThat(actual.largestContentfulPaintTargetSelector)
                .overridingErrorMessage(
                    "Expected view.largestContentfulPaintTargetSelector to be <%s> but was <%s>",
                    selector,
                    actual.largestContentfulPaintTargetSelector
                )
                .isEqualTo(selector)
            return this
        }

        fun hasFirstInputDelay(firstInputDelay: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.firstInputDelay)
                .overridingErrorMessage(
                    "Expected view.firstInputDelay to be <%s> but was <%s>",
                    firstInputDelay,
                    actual.firstInputDelay
                )
                .isEqualTo(firstInputDelay)
            return this
        }

        fun hasFirstInputTime(firstInputTime: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.firstInputTime)
                .overridingErrorMessage(
                    "Expected view.firstInputTime to be <%s> but was <%s>",
                    firstInputTime,
                    actual.firstInputTime
                )
                .isEqualTo(firstInputTime)
            return this
        }

        fun hasFirstInputTargetSelector(selector: String?): ViewUpdateEventViewAssert {
            assertThat(actual.firstInputTargetSelector)
                .overridingErrorMessage(
                    "Expected view.firstInputTargetSelector to be <%s> but was <%s>",
                    selector,
                    actual.firstInputTargetSelector
                )
                .isEqualTo(selector)
            return this
        }

        fun hasInteractionToNextPaint(interactionToNextPaint: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.interactionToNextPaint)
                .overridingErrorMessage(
                    "Expected view.interactionToNextPaint to be <%s> but was <%s>",
                    interactionToNextPaint,
                    actual.interactionToNextPaint
                )
                .isEqualTo(interactionToNextPaint)
            return this
        }

        fun hasInteractionToNextPaintTime(time: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.interactionToNextPaintTime)
                .overridingErrorMessage(
                    "Expected view.interactionToNextPaintTime to be <%s> but was <%s>",
                    time,
                    actual.interactionToNextPaintTime
                )
                .isEqualTo(time)
            return this
        }

        fun hasInteractionToNextPaintTargetSelector(selector: String?): ViewUpdateEventViewAssert {
            assertThat(actual.interactionToNextPaintTargetSelector)
                .overridingErrorMessage(
                    "Expected view.interactionToNextPaintTargetSelector to be <%s> but was <%s>",
                    selector,
                    actual.interactionToNextPaintTargetSelector
                )
                .isEqualTo(selector)
            return this
        }

        fun hasCumulativeLayoutShift(cumulativeLayoutShift: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.cumulativeLayoutShift)
                .overridingErrorMessage(
                    "Expected view.cumulativeLayoutShift to be <%s> but was <%s>",
                    cumulativeLayoutShift,
                    actual.cumulativeLayoutShift
                )
                .isEqualTo(cumulativeLayoutShift)
            return this
        }

        fun hasCumulativeLayoutShiftTime(time: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.cumulativeLayoutShiftTime)
                .overridingErrorMessage(
                    "Expected view.cumulativeLayoutShiftTime to be <%s> but was <%s>",
                    time,
                    actual.cumulativeLayoutShiftTime
                )
                .isEqualTo(time)
            return this
        }

        fun hasCumulativeLayoutShiftTargetSelector(selector: String?): ViewUpdateEventViewAssert {
            assertThat(actual.cumulativeLayoutShiftTargetSelector)
                .overridingErrorMessage(
                    "Expected view.cumulativeLayoutShiftTargetSelector to be <%s> but was <%s>",
                    selector,
                    actual.cumulativeLayoutShiftTargetSelector
                )
                .isEqualTo(selector)
            return this
        }

        fun hasDomComplete(domComplete: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.domComplete)
                .overridingErrorMessage(
                    "Expected view.domComplete to be <%s> but was <%s>",
                    domComplete,
                    actual.domComplete
                )
                .isEqualTo(domComplete)
            return this
        }

        fun hasDomContentLoaded(domContentLoaded: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.domContentLoaded)
                .overridingErrorMessage(
                    "Expected view.domContentLoaded to be <%s> but was <%s>",
                    domContentLoaded,
                    actual.domContentLoaded
                )
                .isEqualTo(domContentLoaded)
            return this
        }

        fun hasDomInteractive(domInteractive: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.domInteractive)
                .overridingErrorMessage(
                    "Expected view.domInteractive to be <%s> but was <%s>",
                    domInteractive,
                    actual.domInteractive
                )
                .isEqualTo(domInteractive)
            return this
        }

        fun hasLoadEvent(loadEvent: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.loadEvent)
                .overridingErrorMessage("Expected view.loadEvent to be <%s> but was <%s>", loadEvent, actual.loadEvent)
                .isEqualTo(loadEvent)
            return this
        }

        fun hasFirstByte(firstByte: Long?): ViewUpdateEventViewAssert {
            assertThat(actual.firstByte)
                .overridingErrorMessage("Expected view.firstByte to be <%s> but was <%s>", firstByte, actual.firstByte)
                .isEqualTo(firstByte)
            return this
        }

        fun hasIsActive(isActive: Boolean?): ViewUpdateEventViewAssert {
            assertThat(actual.isActive)
                .overridingErrorMessage("Expected view.isActive to be <%s> but was <%s>", isActive, actual.isActive)
                .isEqualTo(isActive)
            return this
        }

        fun hasIsSlowRendered(isSlowRendered: Boolean?): ViewUpdateEventViewAssert {
            assertThat(actual.isSlowRendered)
                .overridingErrorMessage(
                    "Expected view.isSlowRendered to be <%s> but was <%s>",
                    isSlowRendered,
                    actual.isSlowRendered
                )
                .isEqualTo(isSlowRendered)
            return this
        }

        fun hasMemoryAverage(memoryAverage: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.memoryAverage)
                .overridingErrorMessage(
                    "Expected view.memoryAverage to be <%s> but was <%s>",
                    memoryAverage,
                    actual.memoryAverage
                )
                .isEqualTo(memoryAverage)
            return this
        }

        fun hasMemoryMax(memoryMax: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.memoryMax)
                .overridingErrorMessage("Expected view.memoryMax to be <%s> but was <%s>", memoryMax, actual.memoryMax)
                .isEqualTo(memoryMax)
            return this
        }

        fun hasCpuTicksCount(cpuTicksCount: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.cpuTicksCount)
                .overridingErrorMessage(
                    "Expected view.cpuTicksCount to be <%s> but was <%s>",
                    cpuTicksCount,
                    actual.cpuTicksCount
                )
                .isEqualTo(cpuTicksCount)
            return this
        }

        fun hasCpuTicksPerSecond(cpuTicksPerSecond: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.cpuTicksPerSecond)
                .overridingErrorMessage(
                    "Expected view.cpuTicksPerSecond to be <%s> but was <%s>",
                    cpuTicksPerSecond,
                    actual.cpuTicksPerSecond
                )
                .isEqualTo(cpuTicksPerSecond)
            return this
        }

        fun hasRefreshRateAverage(refreshRateAverage: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.refreshRateAverage)
                .overridingErrorMessage(
                    "Expected view.refreshRateAverage to be <%s> but was <%s>",
                    refreshRateAverage,
                    actual.refreshRateAverage
                )
                .isEqualTo(refreshRateAverage)
            return this
        }

        fun hasRefreshRateMin(refreshRateMin: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.refreshRateMin)
                .overridingErrorMessage(
                    "Expected view.refreshRateMin to be <%s> but was <%s>",
                    refreshRateMin,
                    actual.refreshRateMin
                )
                .isEqualTo(refreshRateMin)
            return this
        }

        fun hasSlowFramesRate(slowFramesRate: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.slowFramesRate)
                .overridingErrorMessage(
                    "Expected view.slowFramesRate to be <%s> but was <%s>",
                    slowFramesRate,
                    actual.slowFramesRate
                )
                .isEqualTo(slowFramesRate)
            return this
        }

        fun hasTimeSpentNotNull(): ViewUpdateEventViewAssert {
            assertThat(actual.timeSpent)
                .overridingErrorMessage("Expected view.timeSpent to be non-null but was null")
                .isNotNull
            return this
        }

        fun hasNetworkSettledTimeNotNull(): ViewUpdateEventViewAssert {
            assertThat(actual.networkSettledTime)
                .overridingErrorMessage("Expected view.networkSettledTime to be non-null but was null")
                .isNotNull
            return this
        }

        fun hasLoadingTimeCloseTo(expected: Long, offset: Offset<Long>): ViewUpdateEventViewAssert {
            assertThat(actual.loadingTime)
                .overridingErrorMessage(
                    "Expected view.loadingTime to be close to <%s> but was <%s>",
                    expected,
                    actual.loadingTime
                )
                .isNotNull
                .isCloseTo(expected, offset)
            return this
        }

        fun hasNetworkSettledTimeCloseTo(expected: Long, offset: Offset<Long>): ViewUpdateEventViewAssert {
            assertThat(actual.networkSettledTime)
                .overridingErrorMessage(
                    "Expected view.networkSettledTime to be close to <%s> but was <%s>",
                    expected,
                    actual.networkSettledTime
                )
                .isNotNull
                .isCloseTo(expected, offset)
            return this
        }

        fun hasInteractionToNextViewTimeCloseTo(expected: Long, offset: Offset<Long>): ViewUpdateEventViewAssert {
            assertThat(actual.interactionToNextViewTime)
                .overridingErrorMessage(
                    "Expected view.interactionToNextViewTime to be close to <%s> but was <%s>",
                    expected,
                    actual.interactionToNextViewTime
                )
                .isNotNull
                .isCloseTo(expected, offset)
            return this
        }

        fun hasFreezeRate(freezeRate: Number?): ViewUpdateEventViewAssert {
            assertThat(actual.freezeRate)
                .overridingErrorMessage(
                    "Expected view.freezeRate to be <%s> but was <%s>",
                    freezeRate,
                    actual.freezeRate
                )
                .isEqualTo(freezeRate)
            return this
        }

        fun customTimings(block: CustomTimingsAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.customTimings)
                .overridingErrorMessage("Expected view.customTimings to be non-null but was null")
                .isNotNull
            CustomTimingsAssert(actual.customTimings!!).block()
            return this
        }

        fun hasNoCustomTimings(): ViewUpdateEventViewAssert {
            assertThat(actual.customTimings)
                .overridingErrorMessage("Expected view.customTimings to be null but was <%s>", actual.customTimings)
                .isNull()
            return this
        }

        fun action(block: ActionAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.action)
                .overridingErrorMessage("Expected view.action to be non-null but was null")
                .isNotNull
            ActionAssert(actual.action!!).block()
            return this
        }

        fun hasNoAction(): ViewUpdateEventViewAssert {
            assertThat(actual.action)
                .overridingErrorMessage("Expected view.action to be null but was <%s>", actual.action)
                .isNull()
            return this
        }

        fun error(block: ErrorAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.error)
                .overridingErrorMessage("Expected view.error to be non-null but was null")
                .isNotNull
            ErrorAssert(actual.error!!).block()
            return this
        }

        fun hasNoError(): ViewUpdateEventViewAssert {
            assertThat(actual.error)
                .overridingErrorMessage("Expected view.error to be null but was <%s>", actual.error)
                .isNull()
            return this
        }

        fun crash(block: CrashAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.crash)
                .overridingErrorMessage("Expected view.crash to be non-null but was null")
                .isNotNull
            CrashAssert(actual.crash!!).block()
            return this
        }

        fun hasNoCrash(): ViewUpdateEventViewAssert {
            assertThat(actual.crash)
                .overridingErrorMessage("Expected view.crash to be null but was <%s>", actual.crash)
                .isNull()
            return this
        }

        fun longTask(block: LongTaskAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.longTask)
                .overridingErrorMessage("Expected view.longTask to be non-null but was null")
                .isNotNull
            LongTaskAssert(actual.longTask!!).block()
            return this
        }

        fun hasNoLongTask(): ViewUpdateEventViewAssert {
            assertThat(actual.longTask)
                .overridingErrorMessage("Expected view.longTask to be null but was <%s>", actual.longTask)
                .isNull()
            return this
        }

        fun frozenFrame(block: FrozenFrameAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.frozenFrame)
                .overridingErrorMessage("Expected view.frozenFrame to be non-null but was null")
                .isNotNull
            FrozenFrameAssert(actual.frozenFrame!!).block()
            return this
        }

        fun hasNoFrozenFrame(): ViewUpdateEventViewAssert {
            assertThat(actual.frozenFrame)
                .overridingErrorMessage("Expected view.frozenFrame to be null but was <%s>", actual.frozenFrame)
                .isNull()
            return this
        }

        fun hasSlowFrames(slowFrames: List<ViewUpdateEvent.SlowFrame>?): ViewUpdateEventViewAssert {
            assertThat(actual.slowFrames)
                .overridingErrorMessage(
                    "Expected view.slowFrames to be <%s> but was <%s>",
                    slowFrames,
                    actual.slowFrames
                )
                .isEqualTo(slowFrames)
            return this
        }

        fun resource(block: ResourceAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.resource)
                .overridingErrorMessage("Expected view.resource to be non-null but was null")
                .isNotNull
            ResourceAssert(actual.resource!!).block()
            return this
        }

        fun hasNoResource(): ViewUpdateEventViewAssert {
            assertThat(actual.resource)
                .overridingErrorMessage("Expected view.resource to be null but was <%s>", actual.resource)
                .isNull()
            return this
        }

        fun frustration(block: FrustrationAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.frustration)
                .overridingErrorMessage("Expected view.frustration to be non-null but was null")
                .isNotNull
            FrustrationAssert(actual.frustration!!).block()
            return this
        }

        fun hasNoFrustration(): ViewUpdateEventViewAssert {
            assertThat(actual.frustration)
                .overridingErrorMessage("Expected view.frustration to be null but was <%s>", actual.frustration)
                .isNull()
            return this
        }

        fun hasInForegroundPeriods(periods: List<ViewUpdateEvent.InForegroundPeriod>?): ViewUpdateEventViewAssert {
            assertThat(actual.inForegroundPeriods)
                .overridingErrorMessage(
                    "Expected view.inForegroundPeriods to be <%s> but was <%s>",
                    periods,
                    actual.inForegroundPeriods
                )
                .isEqualTo(periods)
            return this
        }

        fun flutterBuildTime(block: FlutterBuildTimeAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.flutterBuildTime)
                .overridingErrorMessage("Expected view.flutterBuildTime to be non-null but was null")
                .isNotNull
            FlutterBuildTimeAssert(actual.flutterBuildTime!!).block()
            return this
        }

        fun hasNoFlutterBuildTime(): ViewUpdateEventViewAssert {
            assertThat(actual.flutterBuildTime)
                .overridingErrorMessage(
                    "Expected view.flutterBuildTime to be null but was <%s>",
                    actual.flutterBuildTime
                )
                .isNull()
            return this
        }

        fun flutterRasterTime(block: FlutterBuildTimeAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.flutterRasterTime)
                .overridingErrorMessage("Expected view.flutterRasterTime to be non-null but was null")
                .isNotNull
            FlutterBuildTimeAssert(actual.flutterRasterTime!!).block()
            return this
        }

        fun hasNoFlutterRasterTime(): ViewUpdateEventViewAssert {
            assertThat(actual.flutterRasterTime)
                .overridingErrorMessage(
                    "Expected view.flutterRasterTime to be null but was <%s>",
                    actual.flutterRasterTime
                )
                .isNull()
            return this
        }

        fun jsRefreshRate(block: FlutterBuildTimeAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.jsRefreshRate)
                .overridingErrorMessage("Expected view.jsRefreshRate to be non-null but was null")
                .isNotNull
            FlutterBuildTimeAssert(actual.jsRefreshRate!!).block()
            return this
        }

        fun hasNoJsRefreshRate(): ViewUpdateEventViewAssert {
            assertThat(actual.jsRefreshRate)
                .overridingErrorMessage("Expected view.jsRefreshRate to be null but was <%s>", actual.jsRefreshRate)
                .isNull()
            return this
        }

        fun performance(block: PerformanceAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.performance)
                .overridingErrorMessage("Expected view.performance to be non-null but was null")
                .isNotNull
            PerformanceAssert(actual.performance!!).block()
            return this
        }

        fun hasNoPerformance(): ViewUpdateEventViewAssert {
            assertThat(actual.performance)
                .overridingErrorMessage("Expected view.performance to be null but was <%s>", actual.performance)
                .isNull()
            return this
        }

        fun accessibility(block: AccessibilityAssert.() -> Unit): ViewUpdateEventViewAssert {
            assertThat(actual.accessibility)
                .overridingErrorMessage("Expected view.accessibility to be non-null but was null")
                .isNotNull
            AccessibilityAssert(actual.accessibility!!).block()
            return this
        }

        fun hasNoAccessibility(): ViewUpdateEventViewAssert {
            assertThat(actual.accessibility)
                .overridingErrorMessage("Expected view.accessibility to be null but was <%s>", actual.accessibility)
                .isNull()
            return this
        }
    }

    class ViewUpdateEventSessionAssert(
        actual: ViewUpdateEvent.ViewUpdateEventSession
    ) : AbstractObjectAssert<ViewUpdateEventSessionAssert, ViewUpdateEvent.ViewUpdateEventSession>(
        actual,
        ViewUpdateEventSessionAssert::class.java
    ) {
        fun hasId(id: String): ViewUpdateEventSessionAssert {
            assertThat(actual.id)
                .overridingErrorMessage("Expected session.id to be <%s> but was <%s>", id, actual.id)
                .isEqualTo(id)
            return this
        }

        fun hasType(type: ViewUpdateEvent.ViewUpdateEventSessionType): ViewUpdateEventSessionAssert {
            assertThat(actual.type)
                .overridingErrorMessage("Expected session.type to be <%s> but was <%s>", type, actual.type)
                .isEqualTo(type)
            return this
        }

        fun hasIsActive(isActive: Boolean?): ViewUpdateEventSessionAssert {
            assertThat(actual.isActive)
                .overridingErrorMessage("Expected session.isActive to be <%s> but was <%s>", isActive, actual.isActive)
                .isEqualTo(isActive)
            return this
        }

        fun hasSampledForReplay(sampledForReplay: Boolean?): ViewUpdateEventSessionAssert {
            assertThat(actual.sampledForReplay)
                .overridingErrorMessage(
                    "Expected session.sampledForReplay to be <%s> but was <%s>",
                    sampledForReplay,
                    actual.sampledForReplay
                )
                .isEqualTo(sampledForReplay)
            return this
        }

        fun hasHasReplay(hasReplay: Boolean?): ViewUpdateEventSessionAssert {
            assertThat(actual.hasReplay)
                .overridingErrorMessage(
                    "Expected session.hasReplay to be <%s> but was <%s>",
                    hasReplay,
                    actual.hasReplay
                )
                .isEqualTo(hasReplay)
            return this
        }
    }

    class ApplicationAssert(
        actual: ViewUpdateEvent.Application
    ) : AbstractObjectAssert<ApplicationAssert, ViewUpdateEvent.Application>(
        actual,
        ApplicationAssert::class.java
    ) {
        fun hasId(id: String): ApplicationAssert {
            assertThat(actual.id)
                .overridingErrorMessage("Expected application.id to be <%s> but was <%s>", id, actual.id)
                .isEqualTo(id)
            return this
        }

        fun hasCurrentLocale(currentLocale: String?): ApplicationAssert {
            assertThat(actual.currentLocale)
                .overridingErrorMessage(
                    "Expected application.currentLocale to be <%s> but was <%s>",
                    currentLocale,
                    actual.currentLocale
                )
                .isEqualTo(currentLocale)
            return this
        }
    }

    class DdAssert(
        actual: ViewUpdateEvent.Dd
    ) : AbstractObjectAssert<DdAssert, ViewUpdateEvent.Dd>(
        actual,
        DdAssert::class.java
    ) {
        fun hasDocumentVersion(documentVersion: Long): DdAssert {
            assertThat(actual.documentVersion)
                .overridingErrorMessage(
                    "Expected dd.documentVersion to be <%s> but was <%s>",
                    documentVersion,
                    actual.documentVersion
                )
                .isEqualTo(documentVersion)
            return this
        }

        fun hasFormatVersion(formatVersion: Long): DdAssert {
            assertThat(actual.formatVersion)
                .overridingErrorMessage(
                    "Expected dd.formatVersion to be <%s> but was <%s>",
                    formatVersion,
                    actual.formatVersion
                )
                .isEqualTo(formatVersion)
            return this
        }

        fun hasBrowserSdkVersion(browserSdkVersion: String?): DdAssert {
            assertThat(actual.browserSdkVersion)
                .overridingErrorMessage(
                    "Expected dd.browserSdkVersion to be <%s> but was <%s>",
                    browserSdkVersion,
                    actual.browserSdkVersion
                )
                .isEqualTo(browserSdkVersion)
            return this
        }

        fun hasSdkName(sdkName: String?): DdAssert {
            assertThat(actual.sdkName)
                .overridingErrorMessage("Expected dd.sdkName to be <%s> but was <%s>", sdkName, actual.sdkName)
                .isEqualTo(sdkName)
            return this
        }

        fun session(block: DdSessionAssert.() -> Unit): DdAssert {
            assertThat(actual.session)
                .overridingErrorMessage("Expected dd.session to be non-null but was null")
                .isNotNull
            DdSessionAssert(actual.session!!).block()
            return this
        }

        fun configuration(block: ConfigurationAssert.() -> Unit): DdAssert {
            assertThat(actual.configuration)
                .overridingErrorMessage("Expected dd.configuration to be non-null but was null")
                .isNotNull
            ConfigurationAssert(actual.configuration!!).block()
            return this
        }
    }

    class ContainerAssert(
        actual: ViewUpdateEvent.Container
    ) : AbstractObjectAssert<ContainerAssert, ViewUpdateEvent.Container>(
        actual,
        ContainerAssert::class.java
    ) {
        fun hasSource(source: ViewUpdateEvent.ViewUpdateEventSource): ContainerAssert {
            assertThat(actual.source)
                .overridingErrorMessage("Expected container.source to be <%s> but was <%s>", source, actual.source)
                .isEqualTo(source)
            return this
        }

        fun view(block: ContainerViewAssert.() -> Unit): ContainerAssert {
            ContainerViewAssert(actual.view).block()
            return this
        }
    }

    class ContainerViewAssert(
        actual: ViewUpdateEvent.ContainerView
    ) : AbstractObjectAssert<ContainerViewAssert, ViewUpdateEvent.ContainerView>(
        actual,
        ContainerViewAssert::class.java
    ) {
        fun hasId(id: String): ContainerViewAssert {
            assertThat(actual.id)
                .overridingErrorMessage("Expected container.view.id to be <%s> but was <%s>", id, actual.id)
                .isEqualTo(id)
            return this
        }
    }

    class FeatureFlagsAssert(
        actual: ViewUpdateEvent.FeatureFlags
    ) : AbstractObjectAssert<FeatureFlagsAssert, ViewUpdateEvent.FeatureFlags>(
        actual,
        FeatureFlagsAssert::class.java
    ) {
        fun hasAdditionalProperties(additionalProperties: Map<String, Any?>): FeatureFlagsAssert {
            assertThat(actual.additionalProperties)
                .overridingErrorMessage(
                    "Expected featureFlags.additionalProperties to be <%s> but was <%s>",
                    additionalProperties,
                    actual.additionalProperties
                )
                .isEqualTo(additionalProperties)
            return this
        }

        fun containsProperty(key: String, value: Any?): FeatureFlagsAssert {
            assertThat(actual.additionalProperties)
                .overridingErrorMessage("Expected featureFlags to contain property <%s>=<%s>", key, value)
                .containsEntry(key, value)
            return this
        }
    }

    class PrivacyAssert(
        actual: ViewUpdateEvent.Privacy
    ) : AbstractObjectAssert<PrivacyAssert, ViewUpdateEvent.Privacy>(
        actual,
        PrivacyAssert::class.java
    ) {
        fun hasReplayLevel(replayLevel: ViewUpdateEvent.ReplayLevel): PrivacyAssert {
            assertThat(actual.replayLevel)
                .overridingErrorMessage(
                    "Expected privacy.replayLevel to be <%s> but was <%s>",
                    replayLevel,
                    actual.replayLevel
                )
                .isEqualTo(replayLevel)
            return this
        }
    }

    class DisplayAssert(
        actual: ViewUpdateEvent.Display
    ) : AbstractObjectAssert<DisplayAssert, ViewUpdateEvent.Display>(
        actual,
        DisplayAssert::class.java
    ) {
        fun scroll(block: ScrollAssert.() -> Unit): DisplayAssert {
            assertThat(actual.scroll)
                .overridingErrorMessage("Expected display.scroll to be non-null but was null")
                .isNotNull
            ScrollAssert(actual.scroll!!).block()
            return this
        }

        fun viewport(block: ViewportAssert.() -> Unit): DisplayAssert {
            assertThat(actual.viewport)
                .overridingErrorMessage("Expected display.viewport to be non-null but was null")
                .isNotNull
            ViewportAssert(actual.viewport!!).block()
            return this
        }
    }

    class ScrollAssert(
        actual: ViewUpdateEvent.Scroll
    ) : AbstractObjectAssert<ScrollAssert, ViewUpdateEvent.Scroll>(
        actual,
        ScrollAssert::class.java
    ) {
        fun hasMaxDepth(maxDepth: Number): ScrollAssert {
            assertThat(actual.maxDepth)
                .overridingErrorMessage("Expected scroll.maxDepth to be <%s> but was <%s>", maxDepth, actual.maxDepth)
                .isEqualTo(maxDepth)
            return this
        }

        fun hasMaxDepthScrollTop(maxDepthScrollTop: Number): ScrollAssert {
            assertThat(actual.maxDepthScrollTop)
                .overridingErrorMessage(
                    "Expected scroll.maxDepthScrollTop to be <%s> but was <%s>",
                    maxDepthScrollTop,
                    actual.maxDepthScrollTop
                )
                .isEqualTo(maxDepthScrollTop)
            return this
        }

        fun hasMaxScrollHeight(maxScrollHeight: Number): ScrollAssert {
            assertThat(actual.maxScrollHeight)
                .overridingErrorMessage(
                    "Expected scroll.maxScrollHeight to be <%s> but was <%s>",
                    maxScrollHeight,
                    actual.maxScrollHeight
                )
                .isEqualTo(maxScrollHeight)
            return this
        }

        fun hasMaxScrollHeightTime(maxScrollHeightTime: Number): ScrollAssert {
            assertThat(actual.maxScrollHeightTime)
                .overridingErrorMessage(
                    "Expected scroll.maxScrollHeightTime to be <%s> but was <%s>",
                    maxScrollHeightTime,
                    actual.maxScrollHeightTime
                )
                .isEqualTo(maxScrollHeightTime)
            return this
        }
    }

    class ViewportAssert(
        actual: ViewUpdateEvent.Viewport
    ) : AbstractObjectAssert<ViewportAssert, ViewUpdateEvent.Viewport>(
        actual,
        ViewportAssert::class.java
    ) {
        fun hasWidth(width: Number): ViewportAssert {
            assertThat(actual.width)
                .overridingErrorMessage("Expected viewport.width to be <%s> but was <%s>", width, actual.width)
                .isEqualTo(width)
            return this
        }

        fun hasHeight(height: Number): ViewportAssert {
            assertThat(actual.height)
                .overridingErrorMessage("Expected viewport.height to be <%s> but was <%s>", height, actual.height)
                .isEqualTo(height)
            return this
        }
    }

    class UsrAssert(
        actual: ViewUpdateEvent.Usr
    ) : AbstractObjectAssert<UsrAssert, ViewUpdateEvent.Usr>(
        actual,
        UsrAssert::class.java
    ) {
        fun hasId(id: String?): UsrAssert {
            assertThat(actual.id)
                .overridingErrorMessage("Expected usr.id to be <%s> but was <%s>", id, actual.id)
                .isEqualTo(id)
            return this
        }

        fun hasName(name: String?): UsrAssert {
            assertThat(actual.name)
                .overridingErrorMessage("Expected usr.name to be <%s> but was <%s>", name, actual.name)
                .isEqualTo(name)
            return this
        }

        fun hasEmail(email: String?): UsrAssert {
            assertThat(actual.email)
                .overridingErrorMessage("Expected usr.email to be <%s> but was <%s>", email, actual.email)
                .isEqualTo(email)
            return this
        }

        fun hasAnonymousId(anonymousId: String?): UsrAssert {
            assertThat(actual.anonymousId)
                .overridingErrorMessage(
                    "Expected usr.anonymousId to be <%s> but was <%s>",
                    anonymousId,
                    actual.anonymousId
                )
                .isEqualTo(anonymousId)
            return this
        }

        fun containsAdditionalProperty(key: String, value: Any?): UsrAssert {
            assertThat(actual.additionalProperties)
                .overridingErrorMessage("Expected usr to contain additional property <%s>=<%s>", key, value)
                .containsEntry(key, value)
            return this
        }
    }

    class AccountAssert(
        actual: ViewUpdateEvent.Account
    ) : AbstractObjectAssert<AccountAssert, ViewUpdateEvent.Account>(
        actual,
        AccountAssert::class.java
    ) {
        fun hasId(id: String): AccountAssert {
            assertThat(actual.id)
                .overridingErrorMessage("Expected account.id to be <%s> but was <%s>", id, actual.id)
                .isEqualTo(id)
            return this
        }

        fun hasName(name: String?): AccountAssert {
            assertThat(actual.name)
                .overridingErrorMessage("Expected account.name to be <%s> but was <%s>", name, actual.name)
                .isEqualTo(name)
            return this
        }

        fun containsAdditionalProperty(key: String, value: Any?): AccountAssert {
            assertThat(actual.additionalProperties)
                .overridingErrorMessage("Expected account to contain additional property <%s>=<%s>", key, value)
                .containsEntry(key, value)
            return this
        }
    }

    class ConnectivityAssert(
        actual: ViewUpdateEvent.Connectivity
    ) : AbstractObjectAssert<ConnectivityAssert, ViewUpdateEvent.Connectivity>(
        actual,
        ConnectivityAssert::class.java
    ) {
        fun hasStatus(status: ViewUpdateEvent.Status): ConnectivityAssert {
            assertThat(actual.status)
                .overridingErrorMessage("Expected connectivity.status to be <%s> but was <%s>", status, actual.status)
                .isEqualTo(status)
            return this
        }

        fun hasInterfaces(interfaces: List<ViewUpdateEvent.Interface>?): ConnectivityAssert {
            assertThat(actual.interfaces)
                .overridingErrorMessage(
                    "Expected connectivity.interfaces to be <%s> but was <%s>",
                    interfaces,
                    actual.interfaces
                )
                .isEqualTo(interfaces)
            return this
        }

        fun hasEffectiveType(effectiveType: ViewUpdateEvent.EffectiveType?): ConnectivityAssert {
            assertThat(actual.effectiveType)
                .overridingErrorMessage(
                    "Expected connectivity.effectiveType to be <%s> but was <%s>",
                    effectiveType,
                    actual.effectiveType
                )
                .isEqualTo(effectiveType)
            return this
        }

        fun cellular(block: CellularAssert.() -> Unit): ConnectivityAssert {
            assertThat(actual.cellular)
                .overridingErrorMessage("Expected connectivity.cellular to be non-null but was null")
                .isNotNull
            CellularAssert(actual.cellular!!).block()
            return this
        }
    }

    class CellularAssert(
        actual: ViewUpdateEvent.Cellular
    ) : AbstractObjectAssert<CellularAssert, ViewUpdateEvent.Cellular>(
        actual,
        CellularAssert::class.java
    ) {
        fun hasTechnology(technology: String?): CellularAssert {
            assertThat(actual.technology)
                .overridingErrorMessage(
                    "Expected cellular.technology to be <%s> but was <%s>",
                    technology,
                    actual.technology
                )
                .isEqualTo(technology)
            return this
        }

        fun hasCarrierName(carrierName: String?): CellularAssert {
            assertThat(actual.carrierName)
                .overridingErrorMessage(
                    "Expected cellular.carrierName to be <%s> but was <%s>",
                    carrierName,
                    actual.carrierName
                )
                .isEqualTo(carrierName)
            return this
        }
    }

    class SyntheticsAssert(
        actual: ViewUpdateEvent.Synthetics
    ) : AbstractObjectAssert<SyntheticsAssert, ViewUpdateEvent.Synthetics>(
        actual,
        SyntheticsAssert::class.java
    ) {
        fun hasTestId(testId: String): SyntheticsAssert {
            assertThat(actual.testId)
                .overridingErrorMessage("Expected synthetics.testId to be <%s> but was <%s>", testId, actual.testId)
                .isEqualTo(testId)
            return this
        }

        fun hasResultId(resultId: String): SyntheticsAssert {
            assertThat(actual.resultId)
                .overridingErrorMessage(
                    "Expected synthetics.resultId to be <%s> but was <%s>",
                    resultId,
                    actual.resultId
                )
                .isEqualTo(resultId)
            return this
        }

        fun hasInjected(injected: Boolean?): SyntheticsAssert {
            assertThat(actual.injected)
                .overridingErrorMessage(
                    "Expected synthetics.injected to be <%s> but was <%s>",
                    injected,
                    actual.injected
                )
                .isEqualTo(injected)
            return this
        }
    }

    class CiTestAssert(
        actual: ViewUpdateEvent.CiTest
    ) : AbstractObjectAssert<CiTestAssert, ViewUpdateEvent.CiTest>(
        actual,
        CiTestAssert::class.java
    ) {
        fun hasTestExecutionId(testExecutionId: String): CiTestAssert {
            assertThat(actual.testExecutionId)
                .overridingErrorMessage(
                    "Expected ciTest.testExecutionId to be <%s> but was <%s>",
                    testExecutionId,
                    actual.testExecutionId
                )
                .isEqualTo(testExecutionId)
            return this
        }
    }

    class OsAssert(
        actual: ViewUpdateEvent.Os
    ) : AbstractObjectAssert<OsAssert, ViewUpdateEvent.Os>(
        actual,
        OsAssert::class.java
    ) {
        fun hasName(name: String): OsAssert {
            assertThat(actual.name)
                .overridingErrorMessage("Expected os.name to be <%s> but was <%s>", name, actual.name)
                .isEqualTo(name)
            return this
        }

        fun hasVersion(version: String): OsAssert {
            assertThat(actual.version)
                .overridingErrorMessage("Expected os.version to be <%s> but was <%s>", version, actual.version)
                .isEqualTo(version)
            return this
        }

        fun hasBuild(build: String?): OsAssert {
            assertThat(actual.build)
                .overridingErrorMessage("Expected os.build to be <%s> but was <%s>", build, actual.build)
                .isEqualTo(build)
            return this
        }

        fun hasVersionMajor(versionMajor: String): OsAssert {
            assertThat(actual.versionMajor)
                .overridingErrorMessage(
                    "Expected os.versionMajor to be <%s> but was <%s>",
                    versionMajor,
                    actual.versionMajor
                )
                .isEqualTo(versionMajor)
            return this
        }
    }

    class DeviceAssert(
        actual: ViewUpdateEvent.Device
    ) : AbstractObjectAssert<DeviceAssert, ViewUpdateEvent.Device>(
        actual,
        DeviceAssert::class.java
    ) {
        fun hasType(type: ViewUpdateEvent.DeviceType?): DeviceAssert {
            assertThat(actual.type)
                .overridingErrorMessage("Expected device.type to be <%s> but was <%s>", type, actual.type)
                .isEqualTo(type)
            return this
        }

        fun hasName(name: String?): DeviceAssert {
            assertThat(actual.name)
                .overridingErrorMessage("Expected device.name to be <%s> but was <%s>", name, actual.name)
                .isEqualTo(name)
            return this
        }

        fun hasModel(model: String?): DeviceAssert {
            assertThat(actual.model)
                .overridingErrorMessage("Expected device.model to be <%s> but was <%s>", model, actual.model)
                .isEqualTo(model)
            return this
        }

        fun hasBrand(brand: String?): DeviceAssert {
            assertThat(actual.brand)
                .overridingErrorMessage("Expected device.brand to be <%s> but was <%s>", brand, actual.brand)
                .isEqualTo(brand)
            return this
        }

        fun hasArchitecture(architecture: String?): DeviceAssert {
            assertThat(actual.architecture)
                .overridingErrorMessage(
                    "Expected device.architecture to be <%s> but was <%s>",
                    architecture,
                    actual.architecture
                )
                .isEqualTo(architecture)
            return this
        }

        fun hasLocale(locale: String?): DeviceAssert {
            assertThat(actual.locale)
                .overridingErrorMessage("Expected device.locale to be <%s> but was <%s>", locale, actual.locale)
                .isEqualTo(locale)
            return this
        }

        fun hasLocales(locales: List<String>?): DeviceAssert {
            assertThat(actual.locales)
                .overridingErrorMessage("Expected device.locales to be <%s> but was <%s>", locales, actual.locales)
                .isEqualTo(locales)
            return this
        }

        fun hasTimeZone(timeZone: String?): DeviceAssert {
            assertThat(actual.timeZone)
                .overridingErrorMessage("Expected device.timeZone to be <%s> but was <%s>", timeZone, actual.timeZone)
                .isEqualTo(timeZone)
            return this
        }

        fun hasBatteryLevel(batteryLevel: Number?): DeviceAssert {
            assertThat(actual.batteryLevel)
                .overridingErrorMessage(
                    "Expected device.batteryLevel to be <%s> but was <%s>",
                    batteryLevel,
                    actual.batteryLevel
                )
                .isEqualTo(batteryLevel)
            return this
        }

        fun hasPowerSavingMode(powerSavingMode: Boolean?): DeviceAssert {
            assertThat(actual.powerSavingMode)
                .overridingErrorMessage(
                    "Expected device.powerSavingMode to be <%s> but was <%s>",
                    powerSavingMode,
                    actual.powerSavingMode
                )
                .isEqualTo(powerSavingMode)
            return this
        }

        fun hasBrightnessLevel(brightnessLevel: Number?): DeviceAssert {
            assertThat(actual.brightnessLevel)
                .overridingErrorMessage(
                    "Expected device.brightnessLevel to be <%s> but was <%s>",
                    brightnessLevel,
                    actual.brightnessLevel
                )
                .isEqualTo(brightnessLevel)
            return this
        }

        fun hasLogicalCpuCount(logicalCpuCount: Number?): DeviceAssert {
            assertThat(actual.logicalCpuCount)
                .overridingErrorMessage(
                    "Expected device.logicalCpuCount to be <%s> but was <%s>",
                    logicalCpuCount,
                    actual.logicalCpuCount
                )
                .isEqualTo(logicalCpuCount)
            return this
        }

        fun hasTotalRam(totalRam: Number?): DeviceAssert {
            assertThat(actual.totalRam)
                .overridingErrorMessage("Expected device.totalRam to be <%s> but was <%s>", totalRam, actual.totalRam)
                .isEqualTo(totalRam)
            return this
        }

        fun hasIsLowRam(isLowRam: Boolean?): DeviceAssert {
            assertThat(actual.isLowRam)
                .overridingErrorMessage("Expected device.isLowRam to be <%s> but was <%s>", isLowRam, actual.isLowRam)
                .isEqualTo(isLowRam)
            return this
        }
    }

    class DdSessionAssert(
        actual: ViewUpdateEvent.DdSession
    ) : AbstractObjectAssert<DdSessionAssert, ViewUpdateEvent.DdSession>(
        actual,
        DdSessionAssert::class.java
    ) {
        fun hasPlan(plan: ViewUpdateEvent.Plan?): DdSessionAssert {
            assertThat(actual.plan)
                .overridingErrorMessage("Expected dd.session.plan to be <%s> but was <%s>", plan, actual.plan)
                .isEqualTo(plan)
            return this
        }

        fun hasSessionPrecondition(sessionPrecondition: ViewUpdateEvent.SessionPrecondition?): DdSessionAssert {
            assertThat(actual.sessionPrecondition)
                .overridingErrorMessage(
                    "Expected dd.session.sessionPrecondition to be <%s> but was <%s>",
                    sessionPrecondition,
                    actual.sessionPrecondition
                )
                .isEqualTo(sessionPrecondition)
            return this
        }
    }

    class ConfigurationAssert(
        actual: ViewUpdateEvent.Configuration
    ) : AbstractObjectAssert<ConfigurationAssert, ViewUpdateEvent.Configuration>(
        actual,
        ConfigurationAssert::class.java
    ) {
        fun hasSessionSampleRate(sessionSampleRate: Number): ConfigurationAssert {
            assertThat(actual.sessionSampleRate)
                .overridingErrorMessage(
                    "Expected configuration.sessionSampleRate to be <%s> but was <%s>",
                    sessionSampleRate,
                    actual.sessionSampleRate
                )
                .isEqualTo(sessionSampleRate)
            return this
        }

        fun hasSessionReplaySampleRate(sessionReplaySampleRate: Number?): ConfigurationAssert {
            assertThat(actual.sessionReplaySampleRate)
                .overridingErrorMessage(
                    "Expected configuration.sessionReplaySampleRate to be <%s> but was <%s>",
                    sessionReplaySampleRate,
                    actual.sessionReplaySampleRate
                )
                .isEqualTo(sessionReplaySampleRate)
            return this
        }

        fun hasProfilingSampleRate(profilingSampleRate: Number?): ConfigurationAssert {
            assertThat(actual.profilingSampleRate)
                .overridingErrorMessage(
                    "Expected configuration.profilingSampleRate to be <%s> but was <%s>",
                    profilingSampleRate,
                    actual.profilingSampleRate
                )
                .isEqualTo(profilingSampleRate)
            return this
        }

        fun hasTraceSampleRate(traceSampleRate: Number?): ConfigurationAssert {
            assertThat(actual.traceSampleRate)
                .overridingErrorMessage(
                    "Expected configuration.traceSampleRate to be <%s> but was <%s>",
                    traceSampleRate,
                    actual.traceSampleRate
                )
                .isEqualTo(traceSampleRate)
            return this
        }
    }

    class CustomTimingsAssert(
        actual: ViewUpdateEvent.CustomTimings
    ) : AbstractObjectAssert<CustomTimingsAssert, ViewUpdateEvent.CustomTimings>(
        actual,
        CustomTimingsAssert::class.java
    ) {
        fun hasAdditionalProperties(additionalProperties: Map<String, Long>): CustomTimingsAssert {
            assertThat(actual.additionalProperties)
                .overridingErrorMessage(
                    "Expected customTimings.additionalProperties to be <%s> but was <%s>",
                    additionalProperties,
                    actual.additionalProperties
                )
                .isEqualTo(additionalProperties)
            return this
        }

        fun containsTiming(key: String, value: Long): CustomTimingsAssert {
            assertThat(actual.additionalProperties)
                .overridingErrorMessage("Expected customTimings to contain timing <%s>=<%s>", key, value)
                .containsEntry(key, value)
            return this
        }
    }

    class ActionAssert(
        actual: ViewUpdateEvent.Action
    ) : AbstractObjectAssert<ActionAssert, ViewUpdateEvent.Action>(
        actual,
        ActionAssert::class.java
    ) {
        fun hasCount(count: Long): ActionAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.action.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class ErrorAssert(
        actual: ViewUpdateEvent.Error
    ) : AbstractObjectAssert<ErrorAssert, ViewUpdateEvent.Error>(
        actual,
        ErrorAssert::class.java
    ) {
        fun hasCount(count: Long): ErrorAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.error.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class CrashAssert(
        actual: ViewUpdateEvent.Crash
    ) : AbstractObjectAssert<CrashAssert, ViewUpdateEvent.Crash>(
        actual,
        CrashAssert::class.java
    ) {
        fun hasCount(count: Long): CrashAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.crash.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class LongTaskAssert(
        actual: ViewUpdateEvent.LongTask
    ) : AbstractObjectAssert<LongTaskAssert, ViewUpdateEvent.LongTask>(
        actual,
        LongTaskAssert::class.java
    ) {
        fun hasCount(count: Long): LongTaskAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.longTask.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class FrozenFrameAssert(
        actual: ViewUpdateEvent.FrozenFrame
    ) : AbstractObjectAssert<FrozenFrameAssert, ViewUpdateEvent.FrozenFrame>(
        actual,
        FrozenFrameAssert::class.java
    ) {
        fun hasCount(count: Long): FrozenFrameAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.frozenFrame.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class ResourceAssert(
        actual: ViewUpdateEvent.Resource
    ) : AbstractObjectAssert<ResourceAssert, ViewUpdateEvent.Resource>(
        actual,
        ResourceAssert::class.java
    ) {
        fun hasCount(count: Long): ResourceAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.resource.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class FrustrationAssert(
        actual: ViewUpdateEvent.Frustration
    ) : AbstractObjectAssert<FrustrationAssert, ViewUpdateEvent.Frustration>(
        actual,
        FrustrationAssert::class.java
    ) {
        fun hasCount(count: Long?): FrustrationAssert {
            assertThat(actual.count)
                .overridingErrorMessage("Expected view.frustration.count to be <%s> but was <%s>", count, actual.count)
                .isEqualTo(count)
            return this
        }
    }

    class FlutterBuildTimeAssert(
        actual: ViewUpdateEvent.FlutterBuildTime
    ) : AbstractObjectAssert<FlutterBuildTimeAssert, ViewUpdateEvent.FlutterBuildTime>(
        actual,
        FlutterBuildTimeAssert::class.java
    ) {
        fun hasMin(min: Number): FlutterBuildTimeAssert {
            assertThat(actual.min)
                .overridingErrorMessage("Expected flutterBuildTime.min to be <%s> but was <%s>", min, actual.min)
                .isEqualTo(min)
            return this
        }

        fun hasMax(max: Number): FlutterBuildTimeAssert {
            assertThat(actual.max)
                .overridingErrorMessage("Expected flutterBuildTime.max to be <%s> but was <%s>", max, actual.max)
                .isEqualTo(max)
            return this
        }

        fun hasAverage(average: Number): FlutterBuildTimeAssert {
            assertThat(actual.average)
                .overridingErrorMessage(
                    "Expected flutterBuildTime.average to be <%s> but was <%s>",
                    average,
                    actual.average
                )
                .isEqualTo(average)
            return this
        }

        fun hasMetricMax(metricMax: Number?): FlutterBuildTimeAssert {
            assertThat(actual.metricMax)
                .overridingErrorMessage(
                    "Expected flutterBuildTime.metricMax to be <%s> but was <%s>",
                    metricMax,
                    actual.metricMax
                )
                .isEqualTo(metricMax)
            return this
        }
    }

    class PerformanceAssert(
        actual: ViewUpdateEvent.Performance
    ) : AbstractObjectAssert<PerformanceAssert, ViewUpdateEvent.Performance>(
        actual,
        PerformanceAssert::class.java
    ) {
        fun cls(block: ClsAssert.() -> Unit): PerformanceAssert {
            assertThat(actual.cls)
                .overridingErrorMessage("Expected performance.cls to be non-null but was null")
                .isNotNull
            ClsAssert(actual.cls!!).block()
            return this
        }

        fun fcp(block: FcpAssert.() -> Unit): PerformanceAssert {
            assertThat(actual.fcp)
                .overridingErrorMessage("Expected performance.fcp to be non-null but was null")
                .isNotNull
            FcpAssert(actual.fcp!!).block()
            return this
        }

        fun fid(block: FidAssert.() -> Unit): PerformanceAssert {
            assertThat(actual.fid)
                .overridingErrorMessage("Expected performance.fid to be non-null but was null")
                .isNotNull
            FidAssert(actual.fid!!).block()
            return this
        }

        fun inp(block: InpAssert.() -> Unit): PerformanceAssert {
            assertThat(actual.inp)
                .overridingErrorMessage("Expected performance.inp to be non-null but was null")
                .isNotNull
            InpAssert(actual.inp!!).block()
            return this
        }

        fun lcp(block: LcpAssert.() -> Unit): PerformanceAssert {
            assertThat(actual.lcp)
                .overridingErrorMessage("Expected performance.lcp to be non-null but was null")
                .isNotNull
            LcpAssert(actual.lcp!!).block()
            return this
        }

        fun fbc(block: FbcAssert.() -> Unit): PerformanceAssert {
            assertThat(actual.fbc)
                .overridingErrorMessage("Expected performance.fbc to be non-null but was null")
                .isNotNull
            FbcAssert(actual.fbc!!).block()
            return this
        }
    }

    class ClsAssert(
        actual: ViewUpdateEvent.Cls
    ) : AbstractObjectAssert<ClsAssert, ViewUpdateEvent.Cls>(
        actual,
        ClsAssert::class.java
    ) {
        fun hasScore(score: Number): ClsAssert {
            assertThat(actual.score)
                .overridingErrorMessage("Expected cls.score to be <%s> but was <%s>", score, actual.score)
                .isEqualTo(score)
            return this
        }

        fun hasTimestamp(timestamp: Long?): ClsAssert {
            assertThat(actual.timestamp)
                .overridingErrorMessage("Expected cls.timestamp to be <%s> but was <%s>", timestamp, actual.timestamp)
                .isEqualTo(timestamp)
            return this
        }

        fun hasTargetSelector(targetSelector: String?): ClsAssert {
            assertThat(actual.targetSelector)
                .overridingErrorMessage(
                    "Expected cls.targetSelector to be <%s> but was <%s>",
                    targetSelector,
                    actual.targetSelector
                )
                .isEqualTo(targetSelector)
            return this
        }

        fun previousRect(block: PreviousRectAssert.() -> Unit): ClsAssert {
            assertThat(actual.previousRect)
                .overridingErrorMessage("Expected cls.previousRect to be non-null but was null")
                .isNotNull
            PreviousRectAssert(actual.previousRect!!).block()
            return this
        }

        fun currentRect(block: PreviousRectAssert.() -> Unit): ClsAssert {
            assertThat(actual.currentRect)
                .overridingErrorMessage("Expected cls.currentRect to be non-null but was null")
                .isNotNull
            PreviousRectAssert(actual.currentRect!!).block()
            return this
        }
    }

    class PreviousRectAssert(
        actual: ViewUpdateEvent.PreviousRect
    ) : AbstractObjectAssert<PreviousRectAssert, ViewUpdateEvent.PreviousRect>(
        actual,
        PreviousRectAssert::class.java
    ) {
        fun hasX(x: Number): PreviousRectAssert {
            assertThat(actual.x)
                .overridingErrorMessage("Expected rect.x to be <%s> but was <%s>", x, actual.x)
                .isEqualTo(x)
            return this
        }

        fun hasY(y: Number): PreviousRectAssert {
            assertThat(actual.y)
                .overridingErrorMessage("Expected rect.y to be <%s> but was <%s>", y, actual.y)
                .isEqualTo(y)
            return this
        }

        fun hasWidth(width: Number): PreviousRectAssert {
            assertThat(actual.width)
                .overridingErrorMessage("Expected rect.width to be <%s> but was <%s>", width, actual.width)
                .isEqualTo(width)
            return this
        }

        fun hasHeight(height: Number): PreviousRectAssert {
            assertThat(actual.height)
                .overridingErrorMessage("Expected rect.height to be <%s> but was <%s>", height, actual.height)
                .isEqualTo(height)
            return this
        }
    }

    class FcpAssert(
        actual: ViewUpdateEvent.Fcp
    ) : AbstractObjectAssert<FcpAssert, ViewUpdateEvent.Fcp>(
        actual,
        FcpAssert::class.java
    ) {
        fun hasTimestamp(timestamp: Long): FcpAssert {
            assertThat(actual.timestamp)
                .overridingErrorMessage("Expected fcp.timestamp to be <%s> but was <%s>", timestamp, actual.timestamp)
                .isEqualTo(timestamp)
            return this
        }
    }

    class FidAssert(
        actual: ViewUpdateEvent.Fid
    ) : AbstractObjectAssert<FidAssert, ViewUpdateEvent.Fid>(
        actual,
        FidAssert::class.java
    ) {
        fun hasDuration(duration: Long): FidAssert {
            assertThat(actual.duration)
                .overridingErrorMessage("Expected fid.duration to be <%s> but was <%s>", duration, actual.duration)
                .isEqualTo(duration)
            return this
        }

        fun hasTimestamp(timestamp: Long): FidAssert {
            assertThat(actual.timestamp)
                .overridingErrorMessage("Expected fid.timestamp to be <%s> but was <%s>", timestamp, actual.timestamp)
                .isEqualTo(timestamp)
            return this
        }

        fun hasTargetSelector(targetSelector: String?): FidAssert {
            assertThat(actual.targetSelector)
                .overridingErrorMessage(
                    "Expected fid.targetSelector to be <%s> but was <%s>",
                    targetSelector,
                    actual.targetSelector
                )
                .isEqualTo(targetSelector)
            return this
        }
    }

    class InpAssert(
        actual: ViewUpdateEvent.Inp
    ) : AbstractObjectAssert<InpAssert, ViewUpdateEvent.Inp>(
        actual,
        InpAssert::class.java
    ) {
        fun hasDuration(duration: Long): InpAssert {
            assertThat(actual.duration)
                .overridingErrorMessage("Expected inp.duration to be <%s> but was <%s>", duration, actual.duration)
                .isEqualTo(duration)
            return this
        }

        fun hasTimestamp(timestamp: Long?): InpAssert {
            assertThat(actual.timestamp)
                .overridingErrorMessage("Expected inp.timestamp to be <%s> but was <%s>", timestamp, actual.timestamp)
                .isEqualTo(timestamp)
            return this
        }

        fun hasTargetSelector(targetSelector: String?): InpAssert {
            assertThat(actual.targetSelector)
                .overridingErrorMessage(
                    "Expected inp.targetSelector to be <%s> but was <%s>",
                    targetSelector,
                    actual.targetSelector
                )
                .isEqualTo(targetSelector)
            return this
        }
    }

    class LcpAssert(
        actual: ViewUpdateEvent.Lcp
    ) : AbstractObjectAssert<LcpAssert, ViewUpdateEvent.Lcp>(
        actual,
        LcpAssert::class.java
    ) {
        fun hasTimestamp(timestamp: Long): LcpAssert {
            assertThat(actual.timestamp)
                .overridingErrorMessage("Expected lcp.timestamp to be <%s> but was <%s>", timestamp, actual.timestamp)
                .isEqualTo(timestamp)
            return this
        }

        fun hasTargetSelector(targetSelector: String?): LcpAssert {
            assertThat(actual.targetSelector)
                .overridingErrorMessage(
                    "Expected lcp.targetSelector to be <%s> but was <%s>",
                    targetSelector,
                    actual.targetSelector
                )
                .isEqualTo(targetSelector)
            return this
        }

        fun hasResourceUrl(resourceUrl: String?): LcpAssert {
            assertThat(actual.resourceUrl)
                .overridingErrorMessage(
                    "Expected lcp.resourceUrl to be <%s> but was <%s>",
                    resourceUrl,
                    actual.resourceUrl
                )
                .isEqualTo(resourceUrl)
            return this
        }
    }

    class FbcAssert(
        actual: ViewUpdateEvent.Fbc
    ) : AbstractObjectAssert<FbcAssert, ViewUpdateEvent.Fbc>(
        actual,
        FbcAssert::class.java
    ) {
        fun hasTimestamp(timestamp: Long): FbcAssert {
            assertThat(actual.timestamp)
                .overridingErrorMessage("Expected fbc.timestamp to be <%s> but was <%s>", timestamp, actual.timestamp)
                .isEqualTo(timestamp)
            return this
        }
    }

    class AccessibilityAssert(
        actual: ViewUpdateEvent.Accessibility
    ) : AbstractObjectAssert<AccessibilityAssert, ViewUpdateEvent.Accessibility>(
        actual,
        AccessibilityAssert::class.java
    ) {
        fun hasTextSize(textSize: String?): AccessibilityAssert {
            assertThat(actual.textSize)
                .overridingErrorMessage(
                    "Expected accessibility.textSize to be <%s> but was <%s>",
                    textSize,
                    actual.textSize
                )
                .isEqualTo(textSize)
            return this
        }

        fun hasScreenReaderEnabled(screenReaderEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.screenReaderEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.screenReaderEnabled to be <%s> but was <%s>",
                    screenReaderEnabled,
                    actual.screenReaderEnabled
                )
                .isEqualTo(screenReaderEnabled)
            return this
        }

        fun hasBoldTextEnabled(boldTextEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.boldTextEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.boldTextEnabled to be <%s> but was <%s>",
                    boldTextEnabled,
                    actual.boldTextEnabled
                )
                .isEqualTo(boldTextEnabled)
            return this
        }

        fun hasReduceTransparencyEnabled(reduceTransparencyEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.reduceTransparencyEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.reduceTransparencyEnabled to be <%s> but was <%s>",
                    reduceTransparencyEnabled,
                    actual.reduceTransparencyEnabled
                )
                .isEqualTo(reduceTransparencyEnabled)
            return this
        }

        fun hasReduceMotionEnabled(reduceMotionEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.reduceMotionEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.reduceMotionEnabled to be <%s> but was <%s>",
                    reduceMotionEnabled,
                    actual.reduceMotionEnabled
                )
                .isEqualTo(reduceMotionEnabled)
            return this
        }

        fun hasButtonShapesEnabled(buttonShapesEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.buttonShapesEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.buttonShapesEnabled to be <%s> but was <%s>",
                    buttonShapesEnabled,
                    actual.buttonShapesEnabled
                )
                .isEqualTo(buttonShapesEnabled)
            return this
        }

        fun hasInvertColorsEnabled(invertColorsEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.invertColorsEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.invertColorsEnabled to be <%s> but was <%s>",
                    invertColorsEnabled,
                    actual.invertColorsEnabled
                )
                .isEqualTo(invertColorsEnabled)
            return this
        }

        fun hasIncreaseContrastEnabled(increaseContrastEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.increaseContrastEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.increaseContrastEnabled to be <%s> but was <%s>",
                    increaseContrastEnabled,
                    actual.increaseContrastEnabled
                )
                .isEqualTo(increaseContrastEnabled)
            return this
        }

        fun hasAssistiveSwitchEnabled(assistiveSwitchEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.assistiveSwitchEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.assistiveSwitchEnabled to be <%s> but was <%s>",
                    assistiveSwitchEnabled,
                    actual.assistiveSwitchEnabled
                )
                .isEqualTo(assistiveSwitchEnabled)
            return this
        }

        fun hasAssistiveTouchEnabled(assistiveTouchEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.assistiveTouchEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.assistiveTouchEnabled to be <%s> but was <%s>",
                    assistiveTouchEnabled,
                    actual.assistiveTouchEnabled
                )
                .isEqualTo(assistiveTouchEnabled)
            return this
        }

        fun hasVideoAutoplayEnabled(videoAutoplayEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.videoAutoplayEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.videoAutoplayEnabled to be <%s> but was <%s>",
                    videoAutoplayEnabled,
                    actual.videoAutoplayEnabled
                )
                .isEqualTo(videoAutoplayEnabled)
            return this
        }

        fun hasClosedCaptioningEnabled(closedCaptioningEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.closedCaptioningEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.closedCaptioningEnabled to be <%s> but was <%s>",
                    closedCaptioningEnabled,
                    actual.closedCaptioningEnabled
                )
                .isEqualTo(closedCaptioningEnabled)
            return this
        }

        fun hasMonoAudioEnabled(monoAudioEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.monoAudioEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.monoAudioEnabled to be <%s> but was <%s>",
                    monoAudioEnabled,
                    actual.monoAudioEnabled
                )
                .isEqualTo(monoAudioEnabled)
            return this
        }

        fun hasShakeToUndoEnabled(shakeToUndoEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.shakeToUndoEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.shakeToUndoEnabled to be <%s> but was <%s>",
                    shakeToUndoEnabled,
                    actual.shakeToUndoEnabled
                )
                .isEqualTo(shakeToUndoEnabled)
            return this
        }

        fun hasReducedAnimationsEnabled(reducedAnimationsEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.reducedAnimationsEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.reducedAnimationsEnabled to be <%s> but was <%s>",
                    reducedAnimationsEnabled,
                    actual.reducedAnimationsEnabled
                )
                .isEqualTo(reducedAnimationsEnabled)
            return this
        }

        fun hasShouldDifferentiateWithoutColor(shouldDifferentiateWithoutColor: Boolean?): AccessibilityAssert {
            assertThat(actual.shouldDifferentiateWithoutColor)
                .overridingErrorMessage(
                    "Expected accessibility.shouldDifferentiateWithoutColor to be <%s> but was <%s>",
                    shouldDifferentiateWithoutColor,
                    actual.shouldDifferentiateWithoutColor
                )
                .isEqualTo(shouldDifferentiateWithoutColor)
            return this
        }

        fun hasGrayscaleEnabled(grayscaleEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.grayscaleEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.grayscaleEnabled to be <%s> but was <%s>",
                    grayscaleEnabled,
                    actual.grayscaleEnabled
                )
                .isEqualTo(grayscaleEnabled)
            return this
        }

        fun hasSingleAppModeEnabled(singleAppModeEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.singleAppModeEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.singleAppModeEnabled to be <%s> but was <%s>",
                    singleAppModeEnabled,
                    actual.singleAppModeEnabled
                )
                .isEqualTo(singleAppModeEnabled)
            return this
        }

        fun hasOnOffSwitchLabelsEnabled(onOffSwitchLabelsEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.onOffSwitchLabelsEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.onOffSwitchLabelsEnabled to be <%s> but was <%s>",
                    onOffSwitchLabelsEnabled,
                    actual.onOffSwitchLabelsEnabled
                )
                .isEqualTo(onOffSwitchLabelsEnabled)
            return this
        }

        fun hasSpeakScreenEnabled(speakScreenEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.speakScreenEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.speakScreenEnabled to be <%s> but was <%s>",
                    speakScreenEnabled,
                    actual.speakScreenEnabled
                )
                .isEqualTo(speakScreenEnabled)
            return this
        }

        fun hasSpeakSelectionEnabled(speakSelectionEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.speakSelectionEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.speakSelectionEnabled to be <%s> but was <%s>",
                    speakSelectionEnabled,
                    actual.speakSelectionEnabled
                )
                .isEqualTo(speakSelectionEnabled)
            return this
        }

        fun hasRtlEnabled(rtlEnabled: Boolean?): AccessibilityAssert {
            assertThat(actual.rtlEnabled)
                .overridingErrorMessage(
                    "Expected accessibility.rtlEnabled to be <%s> but was <%s>",
                    rtlEnabled,
                    actual.rtlEnabled
                )
                .isEqualTo(rtlEnabled)
            return this
        }
    }

    // endregion

    companion object {
        fun assertThat(actual: ViewUpdateEvent) = ViewUpdateAssert(actual)
    }
}
