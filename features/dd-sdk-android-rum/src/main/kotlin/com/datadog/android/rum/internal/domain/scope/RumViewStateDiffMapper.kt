/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.model.RumViewUpdateEvent

internal object RumViewStateDiffMapper {

    @Suppress("LongMethod")
    fun mapDiffToUpdateEvent(diff: RumViewStateDiff): RumViewUpdateEvent {
        return RumViewUpdateEvent(
            container = if (diff.container.exists) diff.container.item?.toUpdateEvent() else null,
            view = diff.view.item!!.toUpdateEvent(),
            session = diff.session.toUpdateEvent(),
            featureFlags = if (diff.featureFlags.exists) diff.featureFlags.item?.let {
                RumViewUpdateEvent.FeatureFlags(it.additionalProperties)
            } else null,
            privacy = if (diff.privacy.exists) diff.privacy.item?.let {
                RumViewUpdateEvent.Privacy(replayLevel = it.replayLevel.toUpdateEvent())
            } else null,
            display = if (diff.display.exists) diff.display.item?.toUpdateEventDisplay() else null,
            date = diff.date,
            application = diff.application.toUpdateEvent(),
            service = if (diff.service.exists) diff.service.item else null,
            version = if (diff.version.exists) diff.version.item else null,
            buildVersion = if (diff.buildVersion.exists) diff.buildVersion.item else null,
            buildId = if (diff.buildId.exists) diff.buildId.item else null,
            ddtags = if (diff.ddtags.exists) diff.ddtags.item else null,
            source = if (diff.source.exists) diff.source.item?.toUpdateEvent() else null,
            usr = if (diff.usr.exists) diff.usr.item?.toUpdateEvent() else null,
            account = if (diff.account.exists) diff.account.item?.toUpdateEvent() else null,
            connectivity = if (diff.connectivity.exists) diff.connectivity.item?.toUpdateEvent() else null,
            synthetics = if (diff.synthetics.exists) diff.synthetics.item?.let {
                RumViewUpdateEvent.Synthetics(
                    testId = it.testId,
                    resultId = it.resultId,
                    injected = it.injected
                )
            } else null,
            ciTest = if (diff.ciTest.exists) diff.ciTest.item?.let {
                RumViewUpdateEvent.CiTest(testExecutionId = it.testExecutionId)
            } else null,
            os = if (diff.os.exists) diff.os.item?.toUpdateEvent() else null,
            device = if (diff.device.exists) diff.device.item?.toUpdateEvent() else null,
            dd = diff.dd.item!!.toUpdateEventDd(),
            context = if (diff.context.exists) diff.context.item?.let {
                RumViewUpdateEvent.FeatureFlags(it.additionalProperties)
            } else null
        )
    }

    // region Private - top-level nested types

    private fun RumViewState.Application.toUpdateEvent() = RumViewUpdateEvent.Application(
        id = id,
        currentLocale = currentLocale
    )

    private fun RumViewState.ViewEventSession.toUpdateEvent() = RumViewUpdateEvent.RumViewUpdateEventSession(
        isActive = isActive,
        sampledForReplay = sampledForReplay,
        id = id,
        type = type.toUpdateEvent(),
        hasReplay = hasReplay
    )

    @Suppress("LongMethod")
    private fun RumViewStateDiff.ViewEventViewDiff.toUpdateEvent() = RumViewUpdateEvent.RumViewUpdateEventView(
        loadingTime = if (loadingTime.exists) loadingTime.item else null,
        networkSettledTime = if (networkSettledTime.exists) networkSettledTime.item else null,
        interactionToNextViewTime = if (interactionToNextViewTime.exists) interactionToNextViewTime.item else null,
        loadingType = if (loadingType.exists) loadingType.item?.toUpdateEvent() else null,
        timeSpent = if (timeSpent.exists) timeSpent.item else null,
        firstContentfulPaint = if (firstContentfulPaint.exists) firstContentfulPaint.item else null,
        largestContentfulPaint = if (largestContentfulPaint.exists) largestContentfulPaint.item else null,
        largestContentfulPaintTargetSelector = if (largestContentfulPaintTargetSelector.exists) {
            largestContentfulPaintTargetSelector.item
        } else null,
        firstInputDelay = if (firstInputDelay.exists) firstInputDelay.item else null,
        firstInputTime = if (firstInputTime.exists) firstInputTime.item else null,
        firstInputTargetSelector = if (firstInputTargetSelector.exists) firstInputTargetSelector.item else null,
        interactionToNextPaint = if (interactionToNextPaint.exists) interactionToNextPaint.item else null,
        interactionToNextPaintTime = if (interactionToNextPaintTime.exists) interactionToNextPaintTime.item else null,
        interactionToNextPaintTargetSelector = if (interactionToNextPaintTargetSelector.exists) {
            interactionToNextPaintTargetSelector.item
        } else null,
        cumulativeLayoutShift = if (cumulativeLayoutShift.exists) cumulativeLayoutShift.item else null,
        cumulativeLayoutShiftTime = if (cumulativeLayoutShiftTime.exists) cumulativeLayoutShiftTime.item else null,
        cumulativeLayoutShiftTargetSelector = if (cumulativeLayoutShiftTargetSelector.exists) {
            cumulativeLayoutShiftTargetSelector.item
        } else null,
        domComplete = if (domComplete.exists) domComplete.item else null,
        domContentLoaded = if (domContentLoaded.exists) domContentLoaded.item else null,
        domInteractive = if (domInteractive.exists) domInteractive.item else null,
        loadEvent = if (loadEvent.exists) loadEvent.item else null,
        firstByte = if (firstByte.exists) firstByte.item else null,
        customTimings = if (customTimings.exists) customTimings.item?.let {
            RumViewUpdateEvent.CustomTimings(it.additionalProperties)
        } else null,
        isActive = if (isActive.exists) isActive.item else null,
        isSlowRendered = if (isSlowRendered.exists) isSlowRendered.item else null,
        action = if (action.exists) action.item?.let { RumViewUpdateEvent.Action(it.count) } else null,
        error = if (error.exists) error.item?.let { RumViewUpdateEvent.Error(it.count) } else null,
        crash = if (crash.exists) crash.item?.let { RumViewUpdateEvent.Crash(it.count) } else null,
        longTask = if (longTask.exists) longTask.item?.let { RumViewUpdateEvent.LongTask(it.count) } else null,
        frozenFrame = if (frozenFrame.exists) frozenFrame.item?.let { RumViewUpdateEvent.FrozenFrame(it.count) } else null,
        slowFrames = if (slowFrames.exists) slowFrames.item?.map {
            RumViewUpdateEvent.SlowFrame(start = it.start, duration = it.duration)
        } else null,
        resource = if (resource.exists) resource.item?.let { RumViewUpdateEvent.Resource(it.count) } else null,
        frustration = if (frustration.exists) frustration.item?.let { RumViewUpdateEvent.Frustration(it.count) } else null,
        inForegroundPeriods = if (inForegroundPeriods.exists) inForegroundPeriods.item?.map {
            RumViewUpdateEvent.InForegroundPeriod(start = it.start, duration = it.duration)
        } else null,
        memoryAverage = if (memoryAverage.exists) memoryAverage.item else null,
        memoryMax = if (memoryMax.exists) memoryMax.item else null,
        cpuTicksCount = if (cpuTicksCount.exists) cpuTicksCount.item else null,
        cpuTicksPerSecond = if (cpuTicksPerSecond.exists) cpuTicksPerSecond.item else null,
        refreshRateAverage = if (refreshRateAverage.exists) refreshRateAverage.item else null,
        refreshRateMin = if (refreshRateMin.exists) refreshRateMin.item else null,
        slowFramesRate = if (slowFramesRate.exists) slowFramesRate.item else null,
        freezeRate = if (freezeRate.exists) freezeRate.item else null,
        flutterBuildTime = if (flutterBuildTime.exists) flutterBuildTime.item?.toUpdateEvent() else null,
        flutterRasterTime = if (flutterRasterTime.exists) flutterRasterTime.item?.toUpdateEvent() else null,
        jsRefreshRate = if (jsRefreshRate.exists) jsRefreshRate.item?.toUpdateEvent() else null,
        performance = if (performance.exists) performance.item?.toUpdateEvent() else null,
        accessibility = if (accessibility.exists) accessibility.item?.toUpdateEvent() else null,
        id = id,
        referrer = if (referrer.exists) referrer.item else null,
        url = url,
        name = if (name.exists) name.item else null
    )

    private fun RumViewState.Usr.toUpdateEvent() = RumViewUpdateEvent.Usr(
        id = id,
        name = name,
        email = email,
        anonymousId = anonymousId,
        additionalProperties = additionalProperties
    )

    private fun RumViewState.Account.toUpdateEvent() = RumViewUpdateEvent.Account(
        id = id,
        name = name,
        additionalProperties = additionalProperties
    )

    private fun RumViewState.Connectivity.toUpdateEvent() = RumViewUpdateEvent.Connectivity(
        status = status.toUpdateEvent(),
        interfaces = interfaces?.map { it.toUpdateEvent() },
        effectiveType = effectiveType?.toUpdateEvent(),
        cellular = cellular?.let { RumViewUpdateEvent.Cellular(it.technology, it.carrierName) }
    )

    private fun RumViewState.Display.toUpdateEventDisplay() = RumViewUpdateEvent.Display(
        scroll = scroll?.let {
            RumViewUpdateEvent.Scroll(it.maxDepth, it.maxDepthScrollTop, it.maxScrollHeight, it.maxScrollHeightTime)
        },
        viewport = viewport?.let { RumViewUpdateEvent.Viewport(it.width, it.height) }
    )

    private fun RumViewState.Os.toUpdateEvent() = RumViewUpdateEvent.Os(
        name = name,
        version = version,
        build = build,
        versionMajor = versionMajor
    )

    private fun RumViewState.Device.toUpdateEvent() = RumViewUpdateEvent.Device(
        type = type?.toUpdateEvent(),
        name = name,
        model = model,
        brand = brand,
        architecture = architecture,
        locale = locale,
        locales = locales,
        timeZone = timeZone,
        batteryLevel = batteryLevel,
        powerSavingMode = powerSavingMode,
        brightnessLevel = brightnessLevel,
        logicalCpuCount = logicalCpuCount,
        totalRam = totalRam,
        isLowRam = isLowRam
    )

    private fun RumViewState.Dd.toUpdateEventDd() = RumViewUpdateEvent.Dd(
        session = session?.let {
            RumViewUpdateEvent.DdSession(
                plan = it.plan?.toUpdateEvent(),
                sessionPrecondition = it.sessionPrecondition?.toUpdateEvent()
            )
        },
        configuration = configuration?.let {
            RumViewUpdateEvent.Configuration(
                sessionSampleRate = it.sessionSampleRate,
                sessionReplaySampleRate = it.sessionReplaySampleRate,
                profilingSampleRate = it.profilingSampleRate,
                traceSampleRate = it.traceSampleRate
            )
        },
        browserSdkVersion = browserSdkVersion,
        sdkName = sdkName,
        documentVersion = documentVersion
    )

    private fun RumViewState.Container.toUpdateEvent() = RumViewUpdateEvent.Container(
        view = RumViewUpdateEvent.ContainerView(view.id),
        source = source.toUpdateEvent()
    )

    // endregion

    // region Private - deeper nested types

    private fun RumViewState.FlutterBuildTime.toUpdateEvent() = RumViewUpdateEvent.FlutterBuildTime(
        min = min,
        max = max,
        average = average,
        metricMax = metricMax
    )

    private fun RumViewStateDiff.ViewEventViewDiff.PerformanceDiff.toUpdateEvent() =
        RumViewUpdateEvent.Performance(
            cls = if (cls.exists) cls.item?.let {
                RumViewUpdateEvent.Cls(
                    score = it.score,
                    timestamp = it.timestamp,
                    targetSelector = it.targetSelector,
                    previousRect = it.previousRect?.let { r ->
                        RumViewUpdateEvent.PreviousRect(r.x, r.y, r.width, r.height)
                    },
                    currentRect = it.currentRect?.let { r ->
                        RumViewUpdateEvent.PreviousRect(r.x, r.y, r.width, r.height)
                    }
                )
            } else null,
            fcp = if (fcp.exists) fcp.item?.let { RumViewUpdateEvent.Fcp(it.timestamp) } else null,
            fid = if (fid.exists) fid.item?.let {
                RumViewUpdateEvent.Fid(it.duration, it.timestamp, it.targetSelector)
            } else null,
            inp = if (inp.exists) inp.item?.let {
                RumViewUpdateEvent.Inp(
                    duration = it.duration,
                    timestamp = it.timestamp,
                    targetSelector = it.targetSelector,
                    subParts = it.subParts?.let { s ->
                        RumViewUpdateEvent.InpSubParts(s.inputDelay, s.processingTime, s.presentationDelay)
                    }
                )
            } else null,
            lcp = if (lcp.exists) lcp.item?.let {
                RumViewUpdateEvent.Lcp(
                    timestamp = it.timestamp,
                    targetSelector = it.targetSelector,
                    resourceUrl = it.resourceUrl,
                    subParts = it.subParts?.let { s ->
                        RumViewUpdateEvent.LcpSubParts(s.loadDelay, s.loadTime, s.renderDelay)
                    }
                )
            } else null,
            fbc = if (fbc.exists) fbc.item?.let { RumViewUpdateEvent.Fbc(it.timestamp) } else null
        )

    private fun RumViewStateDiff.ViewEventViewDiff.AccessibilityDiff.toUpdateEvent() =
        RumViewUpdateEvent.Accessibility(
            textSize = if (textSize.exists) textSize.item else null,
            screenReaderEnabled = if (screenReaderEnabled.exists) screenReaderEnabled.item else null,
            boldTextEnabled = if (boldTextEnabled.exists) boldTextEnabled.item else null,
            reduceTransparencyEnabled = if (reduceTransparencyEnabled.exists) reduceTransparencyEnabled.item else null,
            reduceMotionEnabled = if (reduceMotionEnabled.exists) reduceMotionEnabled.item else null,
            buttonShapesEnabled = if (buttonShapesEnabled.exists) buttonShapesEnabled.item else null,
            invertColorsEnabled = if (invertColorsEnabled.exists) invertColorsEnabled.item else null,
            increaseContrastEnabled = if (increaseContrastEnabled.exists) increaseContrastEnabled.item else null,
            assistiveSwitchEnabled = if (assistiveSwitchEnabled.exists) assistiveSwitchEnabled.item else null,
            assistiveTouchEnabled = if (assistiveTouchEnabled.exists) assistiveTouchEnabled.item else null,
            videoAutoplayEnabled = if (videoAutoplayEnabled.exists) videoAutoplayEnabled.item else null,
            closedCaptioningEnabled = if (closedCaptioningEnabled.exists) closedCaptioningEnabled.item else null,
            monoAudioEnabled = if (monoAudioEnabled.exists) monoAudioEnabled.item else null,
            shakeToUndoEnabled = if (shakeToUndoEnabled.exists) shakeToUndoEnabled.item else null,
            reducedAnimationsEnabled = if (reducedAnimationsEnabled.exists) reducedAnimationsEnabled.item else null,
            shouldDifferentiateWithoutColor = if (shouldDifferentiateWithoutColor.exists) {
                shouldDifferentiateWithoutColor.item
            } else null,
            grayscaleEnabled = if (grayscaleEnabled.exists) grayscaleEnabled.item else null,
            singleAppModeEnabled = if (singleAppModeEnabled.exists) singleAppModeEnabled.item else null,
            onOffSwitchLabelsEnabled = if (onOffSwitchLabelsEnabled.exists) onOffSwitchLabelsEnabled.item else null,
            speakScreenEnabled = if (speakScreenEnabled.exists) speakScreenEnabled.item else null,
            speakSelectionEnabled = if (speakSelectionEnabled.exists) speakSelectionEnabled.item else null,
            rtlEnabled = if (rtlEnabled.exists) rtlEnabled.item else null
        )

    // endregion

    // region Private - enum mappings

    private fun RumViewState.ViewEventSessionType.toUpdateEvent() = when (this) {
        RumViewState.ViewEventSessionType.USER -> RumViewUpdateEvent.RumViewUpdateEventSessionType.USER
        RumViewState.ViewEventSessionType.SYNTHETICS -> RumViewUpdateEvent.RumViewUpdateEventSessionType.SYNTHETICS
        RumViewState.ViewEventSessionType.CI_TEST -> RumViewUpdateEvent.RumViewUpdateEventSessionType.CI_TEST
    }

    private fun RumViewState.ViewEventSource.toUpdateEvent() = when (this) {
        RumViewState.ViewEventSource.ANDROID -> RumViewUpdateEvent.RumViewUpdateEventSource.ANDROID
        RumViewState.ViewEventSource.IOS -> RumViewUpdateEvent.RumViewUpdateEventSource.IOS
        RumViewState.ViewEventSource.BROWSER -> RumViewUpdateEvent.RumViewUpdateEventSource.BROWSER
        RumViewState.ViewEventSource.FLUTTER -> RumViewUpdateEvent.RumViewUpdateEventSource.FLUTTER
        RumViewState.ViewEventSource.REACT_NATIVE -> RumViewUpdateEvent.RumViewUpdateEventSource.REACT_NATIVE
        RumViewState.ViewEventSource.ROKU -> RumViewUpdateEvent.RumViewUpdateEventSource.ROKU
        RumViewState.ViewEventSource.UNITY -> RumViewUpdateEvent.RumViewUpdateEventSource.UNITY
        RumViewState.ViewEventSource.KOTLIN_MULTIPLATFORM -> RumViewUpdateEvent.RumViewUpdateEventSource.KOTLIN_MULTIPLATFORM
        RumViewState.ViewEventSource.ELECTRON -> RumViewUpdateEvent.RumViewUpdateEventSource.ELECTRON
    }

    private fun RumViewState.LoadingType.toUpdateEvent() = when (this) {
        RumViewState.LoadingType.INITIAL_LOAD -> RumViewUpdateEvent.LoadingType.INITIAL_LOAD
        RumViewState.LoadingType.ROUTE_CHANGE -> RumViewUpdateEvent.LoadingType.ROUTE_CHANGE
        RumViewState.LoadingType.ACTIVITY_DISPLAY -> RumViewUpdateEvent.LoadingType.ACTIVITY_DISPLAY
        RumViewState.LoadingType.ACTIVITY_REDISPLAY -> RumViewUpdateEvent.LoadingType.ACTIVITY_REDISPLAY
        RumViewState.LoadingType.FRAGMENT_DISPLAY -> RumViewUpdateEvent.LoadingType.FRAGMENT_DISPLAY
        RumViewState.LoadingType.FRAGMENT_REDISPLAY -> RumViewUpdateEvent.LoadingType.FRAGMENT_REDISPLAY
        RumViewState.LoadingType.VIEW_CONTROLLER_DISPLAY -> RumViewUpdateEvent.LoadingType.VIEW_CONTROLLER_DISPLAY
        RumViewState.LoadingType.VIEW_CONTROLLER_REDISPLAY -> RumViewUpdateEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY
    }

    private fun RumViewState.ConnectivityStatus.toUpdateEvent() = when (this) {
        RumViewState.ConnectivityStatus.CONNECTED -> RumViewUpdateEvent.Status.CONNECTED
        RumViewState.ConnectivityStatus.NOT_CONNECTED -> RumViewUpdateEvent.Status.NOT_CONNECTED
        RumViewState.ConnectivityStatus.MAYBE -> RumViewUpdateEvent.Status.MAYBE
    }

    private fun RumViewState.Interface.toUpdateEvent() = when (this) {
        RumViewState.Interface.BLUETOOTH -> RumViewUpdateEvent.Interface.BLUETOOTH
        RumViewState.Interface.CELLULAR -> RumViewUpdateEvent.Interface.CELLULAR
        RumViewState.Interface.ETHERNET -> RumViewUpdateEvent.Interface.ETHERNET
        RumViewState.Interface.WIFI -> RumViewUpdateEvent.Interface.WIFI
        RumViewState.Interface.WIMAX -> RumViewUpdateEvent.Interface.WIMAX
        RumViewState.Interface.MIXED -> RumViewUpdateEvent.Interface.MIXED
        RumViewState.Interface.OTHER -> RumViewUpdateEvent.Interface.OTHER
        RumViewState.Interface.UNKNOWN -> RumViewUpdateEvent.Interface.UNKNOWN
        RumViewState.Interface.NONE -> RumViewUpdateEvent.Interface.NONE
    }

    private fun RumViewState.EffectiveType.toUpdateEvent() = when (this) {
        RumViewState.EffectiveType.SLOW_2G -> RumViewUpdateEvent.EffectiveType.SLOW_2G
        RumViewState.EffectiveType.`2G` -> RumViewUpdateEvent.EffectiveType.`2G`
        RumViewState.EffectiveType.`3G` -> RumViewUpdateEvent.EffectiveType.`3G`
        RumViewState.EffectiveType.`4G` -> RumViewUpdateEvent.EffectiveType.`4G`
    }

    private fun RumViewState.DeviceType.toUpdateEvent() = when (this) {
        RumViewState.DeviceType.MOBILE -> RumViewUpdateEvent.DeviceType.MOBILE
        RumViewState.DeviceType.DESKTOP -> RumViewUpdateEvent.DeviceType.DESKTOP
        RumViewState.DeviceType.TABLET -> RumViewUpdateEvent.DeviceType.TABLET
        RumViewState.DeviceType.TV -> RumViewUpdateEvent.DeviceType.TV
        RumViewState.DeviceType.GAMING_CONSOLE -> RumViewUpdateEvent.DeviceType.GAMING_CONSOLE
        RumViewState.DeviceType.BOT -> RumViewUpdateEvent.DeviceType.BOT
        RumViewState.DeviceType.OTHER -> RumViewUpdateEvent.DeviceType.OTHER
    }

    private fun RumViewState.ReplayLevel.toUpdateEvent() = when (this) {
        RumViewState.ReplayLevel.ALLOW -> RumViewUpdateEvent.ReplayLevel.ALLOW
        RumViewState.ReplayLevel.MASK -> RumViewUpdateEvent.ReplayLevel.MASK
        RumViewState.ReplayLevel.MASK_USER_INPUT -> RumViewUpdateEvent.ReplayLevel.MASK_USER_INPUT
    }

    private fun RumViewState.Plan.toUpdateEvent() = when (this) {
        RumViewState.Plan.PLAN_1 -> RumViewUpdateEvent.Plan.PLAN_1
        RumViewState.Plan.PLAN_2 -> RumViewUpdateEvent.Plan.PLAN_2
    }

    private fun RumViewState.SessionPrecondition.toUpdateEvent() = when (this) {
        RumViewState.SessionPrecondition.USER_APP_LAUNCH -> RumViewUpdateEvent.SessionPrecondition.USER_APP_LAUNCH
        RumViewState.SessionPrecondition.INACTIVITY_TIMEOUT -> RumViewUpdateEvent.SessionPrecondition.INACTIVITY_TIMEOUT
        RumViewState.SessionPrecondition.MAX_DURATION -> RumViewUpdateEvent.SessionPrecondition.MAX_DURATION
        RumViewState.SessionPrecondition.BACKGROUND_LAUNCH -> RumViewUpdateEvent.SessionPrecondition.BACKGROUND_LAUNCH
        RumViewState.SessionPrecondition.PREWARM -> RumViewUpdateEvent.SessionPrecondition.PREWARM
        RumViewState.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION -> RumViewUpdateEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION
        RumViewState.SessionPrecondition.EXPLICIT_STOP -> RumViewUpdateEvent.SessionPrecondition.EXPLICIT_STOP
    }

    // endregion
}
