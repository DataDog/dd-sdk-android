/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.model.RumViewUpdateEvent
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.ViewEventDiff

@Suppress("LongMethod")
internal fun ViewEventDiff.toUpdateEvent(): RumViewUpdateEvent {
    return RumViewUpdateEvent(
        container = if (container.exists) container.item?.toUpdateEvent() else null,
        stream = if (stream.exists) stream.item?.let { RumViewUpdateEvent.Stream(id = it.id) } else null,
        view = view.toUpdateEvent(),
        session = session.toUpdateEvent(),
        featureFlags = if (featureFlags.exists) featureFlags.item?.let {
            if (it.additionalProperties.exists) it.additionalProperties.item?.let { map ->
                RumViewUpdateEvent.FeatureFlags(map.toMutableMap())
            } else null
        } else null,
        privacy = if (privacy.exists) privacy.item?.let {
            RumViewUpdateEvent.Privacy(replayLevel = it.replayLevel.toUpdateEvent())
        } else null,
        display = if (display.exists) display.item?.toUpdateEventDisplay() else null,
        date = date,
        application = application.toUpdateEvent(),
        service = if (service.exists) service.item else null,
        version = if (version.exists) version.item else null,
        buildVersion = if (buildVersion.exists) buildVersion.item else null,
        buildId = if (buildId.exists) buildId.item else null,
        ddtags = if (ddtags.exists) ddtags.item else null,
        source = if (source.exists) source.item?.toUpdateEvent() else null,
        usr = if (usr.exists) usr.item?.toUpdateEvent() else null,
        account = if (account.exists) account.item?.toUpdateEvent() else null,
        connectivity = if (connectivity.exists) connectivity.item?.toUpdateEvent() else null,
        synthetics = if (synthetics.exists) synthetics.item?.let {
            RumViewUpdateEvent.Synthetics(
                testId = it.testId,
                resultId = it.resultId,
                injected = it.injected
            )
        } else null,
        ciTest = if (ciTest.exists) ciTest.item?.let {
            RumViewUpdateEvent.CiTest(testExecutionId = it.testExecutionId)
        } else null,
        os = if (os.exists) os.item?.toUpdateEvent() else null,
        device = if (device.exists) device.item?.toUpdateEvent() else null,
        dd = dd.toUpdateEventDd(),
        context = if (context.exists) context.item?.let {
            RumViewUpdateEvent.FeatureFlags(it.additionalProperties)
        } else null
    )
}

// region Private - top-level nested types

private fun ViewEventDiff.ApplicationDiff.toUpdateEvent() = RumViewUpdateEvent.Application(
    id = id,
    currentLocale = if (currentLocale.exists) currentLocale.item else null
)

private fun ViewEventDiff.ViewEventSessionDiff.toUpdateEvent() = RumViewUpdateEvent.RumViewUpdateEventSession(
    isActive = if (isActive.exists) isActive.item else null,
    sampledForReplay = if (sampledForReplay.exists) sampledForReplay.item else null,
    id = id,
    type = type.toUpdateEvent(),
    hasReplay = if (hasReplay.exists) hasReplay.item else null
)

@Suppress("LongMethod")
private fun ViewEventDiff.ViewEventViewDiff.toUpdateEvent() = RumViewUpdateEvent.RumViewUpdateEventView(
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

private fun ViewEvent.Usr.toUpdateEvent() = RumViewUpdateEvent.Usr(
    id = id,
    name = name,
    email = email,
    anonymousId = anonymousId,
    additionalProperties = additionalProperties
)

private fun ViewEvent.Account.toUpdateEvent() = RumViewUpdateEvent.Account(
    id = id,
    name = name,
    additionalProperties = additionalProperties
)

private fun ViewEvent.Connectivity.toUpdateEvent() = RumViewUpdateEvent.Connectivity(
    status = status.toUpdateEvent(),
    interfaces = interfaces?.map { it.toUpdateEvent() },
    effectiveType = effectiveType?.toUpdateEvent(),
    cellular = cellular?.let { RumViewUpdateEvent.Cellular(it.technology, it.carrierName) }
)

private fun ViewEvent.Display.toUpdateEventDisplay() = RumViewUpdateEvent.Display(
    scroll = scroll?.let {
        RumViewUpdateEvent.Scroll(it.maxDepth, it.maxScrollHeight, it.maxScrollHeight, it.maxScrollHeightTime)
    },
    viewport = viewport?.let { RumViewUpdateEvent.Viewport(it.width, it.height) }
)

private fun ViewEvent.Os.toUpdateEvent() = RumViewUpdateEvent.Os(
    name = name,
    version = version,
    build = build,
    versionMajor = versionMajor
)

private fun ViewEvent.Device.toUpdateEvent() = RumViewUpdateEvent.Device(
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

private fun ViewEventDiff.DdDiff.toUpdateEventDd() = RumViewUpdateEvent.Dd(
    session = if (session.exists) session.item?.let {
        RumViewUpdateEvent.DdSession(
            plan = it.plan?.toUpdateEvent(),
            sessionPrecondition = it.sessionPrecondition?.toUpdateEvent()
        )
    } else null,
    configuration = if (configuration.exists) configuration.item?.let {
        RumViewUpdateEvent.Configuration(
            sessionSampleRate = it.sessionSampleRate,
            sessionReplaySampleRate = it.sessionReplaySampleRate,
            profilingSampleRate = it.profilingSampleRate,
            traceSampleRate = it.traceSampleRate
        )
    } else null,
    browserSdkVersion = if (browserSdkVersion.exists) browserSdkVersion.item else null,
    sdkName = if (sdkName.exists) sdkName.item else null,
    documentVersion = documentVersion
)

private fun ViewEvent.Container.toUpdateEvent() = RumViewUpdateEvent.Container(
    view = RumViewUpdateEvent.ContainerView(view.id),
    source = source.toUpdateEvent()
)

// endregion

// region Private - deeper nested types

private fun ViewEvent.FlutterBuildTime.toUpdateEvent() = RumViewUpdateEvent.FlutterBuildTime(
    min = min,
    max = max,
    average = average,
    metricMax = metricMax
)

private fun ViewEventDiff.ViewEventViewDiff.PerformanceDiff.toUpdateEvent() =
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

private fun ViewEventDiff.ViewEventViewDiff.AccessibilityDiff.toUpdateEvent() =
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

private fun ViewEvent.ViewEventSessionType.toUpdateEvent() = when (this) {
    ViewEvent.ViewEventSessionType.USER -> RumViewUpdateEvent.RumViewUpdateEventSessionType.USER
    ViewEvent.ViewEventSessionType.SYNTHETICS -> RumViewUpdateEvent.RumViewUpdateEventSessionType.SYNTHETICS
    ViewEvent.ViewEventSessionType.CI_TEST -> RumViewUpdateEvent.RumViewUpdateEventSessionType.CI_TEST
}

private fun ViewEvent.ViewEventSource.toUpdateEvent() = when (this) {
    ViewEvent.ViewEventSource.ANDROID -> RumViewUpdateEvent.RumViewUpdateEventSource.ANDROID
    ViewEvent.ViewEventSource.IOS -> RumViewUpdateEvent.RumViewUpdateEventSource.IOS
    ViewEvent.ViewEventSource.BROWSER -> RumViewUpdateEvent.RumViewUpdateEventSource.BROWSER
    ViewEvent.ViewEventSource.FLUTTER -> RumViewUpdateEvent.RumViewUpdateEventSource.FLUTTER
    ViewEvent.ViewEventSource.REACT_NATIVE -> RumViewUpdateEvent.RumViewUpdateEventSource.REACT_NATIVE
    ViewEvent.ViewEventSource.ROKU -> RumViewUpdateEvent.RumViewUpdateEventSource.ROKU
    ViewEvent.ViewEventSource.UNITY -> RumViewUpdateEvent.RumViewUpdateEventSource.UNITY
    ViewEvent.ViewEventSource.KOTLIN_MULTIPLATFORM -> RumViewUpdateEvent.RumViewUpdateEventSource.KOTLIN_MULTIPLATFORM
    ViewEvent.ViewEventSource.ELECTRON -> RumViewUpdateEvent.RumViewUpdateEventSource.ELECTRON
}

private fun ViewEvent.LoadingType.toUpdateEvent() = when (this) {
    ViewEvent.LoadingType.INITIAL_LOAD -> RumViewUpdateEvent.LoadingType.INITIAL_LOAD
    ViewEvent.LoadingType.ROUTE_CHANGE -> RumViewUpdateEvent.LoadingType.ROUTE_CHANGE
    ViewEvent.LoadingType.ACTIVITY_DISPLAY -> RumViewUpdateEvent.LoadingType.ACTIVITY_DISPLAY
    ViewEvent.LoadingType.ACTIVITY_REDISPLAY -> RumViewUpdateEvent.LoadingType.ACTIVITY_REDISPLAY
    ViewEvent.LoadingType.FRAGMENT_DISPLAY -> RumViewUpdateEvent.LoadingType.FRAGMENT_DISPLAY
    ViewEvent.LoadingType.FRAGMENT_REDISPLAY -> RumViewUpdateEvent.LoadingType.FRAGMENT_REDISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_DISPLAY -> RumViewUpdateEvent.LoadingType.VIEW_CONTROLLER_DISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY -> RumViewUpdateEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY
}

private fun ViewEvent.ConnectivityStatus.toUpdateEvent() = when (this) {
    ViewEvent.ConnectivityStatus.CONNECTED -> RumViewUpdateEvent.Status.CONNECTED
    ViewEvent.ConnectivityStatus.NOT_CONNECTED -> RumViewUpdateEvent.Status.NOT_CONNECTED
    ViewEvent.ConnectivityStatus.MAYBE -> RumViewUpdateEvent.Status.MAYBE
}

private fun ViewEvent.Interface.toUpdateEvent() = when (this) {
    ViewEvent.Interface.BLUETOOTH -> RumViewUpdateEvent.Interface.BLUETOOTH
    ViewEvent.Interface.CELLULAR -> RumViewUpdateEvent.Interface.CELLULAR
    ViewEvent.Interface.ETHERNET -> RumViewUpdateEvent.Interface.ETHERNET
    ViewEvent.Interface.WIFI -> RumViewUpdateEvent.Interface.WIFI
    ViewEvent.Interface.WIMAX -> RumViewUpdateEvent.Interface.WIMAX
    ViewEvent.Interface.MIXED -> RumViewUpdateEvent.Interface.MIXED
    ViewEvent.Interface.OTHER -> RumViewUpdateEvent.Interface.OTHER
    ViewEvent.Interface.UNKNOWN -> RumViewUpdateEvent.Interface.UNKNOWN
    ViewEvent.Interface.NONE -> RumViewUpdateEvent.Interface.NONE
}

private fun ViewEvent.EffectiveType.toUpdateEvent() = when (this) {
    ViewEvent.EffectiveType.SLOW_2G -> RumViewUpdateEvent.EffectiveType.SLOW_2G
    ViewEvent.EffectiveType.`2G` -> RumViewUpdateEvent.EffectiveType.`2G`
    ViewEvent.EffectiveType.`3G` -> RumViewUpdateEvent.EffectiveType.`3G`
    ViewEvent.EffectiveType.`4G` -> RumViewUpdateEvent.EffectiveType.`4G`
}

private fun ViewEvent.DeviceType.toUpdateEvent() = when (this) {
    ViewEvent.DeviceType.MOBILE -> RumViewUpdateEvent.DeviceType.MOBILE
    ViewEvent.DeviceType.DESKTOP -> RumViewUpdateEvent.DeviceType.DESKTOP
    ViewEvent.DeviceType.TABLET -> RumViewUpdateEvent.DeviceType.TABLET
    ViewEvent.DeviceType.TV -> RumViewUpdateEvent.DeviceType.TV
    ViewEvent.DeviceType.GAMING_CONSOLE -> RumViewUpdateEvent.DeviceType.GAMING_CONSOLE
    ViewEvent.DeviceType.BOT -> RumViewUpdateEvent.DeviceType.BOT
    ViewEvent.DeviceType.OTHER -> RumViewUpdateEvent.DeviceType.OTHER
}

private fun ViewEvent.ReplayLevel.toUpdateEvent() = when (this) {
    ViewEvent.ReplayLevel.ALLOW -> RumViewUpdateEvent.ReplayLevel.ALLOW
    ViewEvent.ReplayLevel.MASK -> RumViewUpdateEvent.ReplayLevel.MASK
    ViewEvent.ReplayLevel.MASK_USER_INPUT -> RumViewUpdateEvent.ReplayLevel.MASK_USER_INPUT
}

private fun ViewEvent.Plan.toUpdateEvent() = when (this) {
    ViewEvent.Plan.PLAN_1 -> RumViewUpdateEvent.Plan.PLAN_1
    ViewEvent.Plan.PLAN_2 -> RumViewUpdateEvent.Plan.PLAN_2
}

private fun ViewEvent.SessionPrecondition.toUpdateEvent() = when (this) {
    ViewEvent.SessionPrecondition.USER_APP_LAUNCH -> RumViewUpdateEvent.SessionPrecondition.USER_APP_LAUNCH
    ViewEvent.SessionPrecondition.INACTIVITY_TIMEOUT -> RumViewUpdateEvent.SessionPrecondition.INACTIVITY_TIMEOUT
    ViewEvent.SessionPrecondition.MAX_DURATION -> RumViewUpdateEvent.SessionPrecondition.MAX_DURATION
    ViewEvent.SessionPrecondition.BACKGROUND_LAUNCH -> RumViewUpdateEvent.SessionPrecondition.BACKGROUND_LAUNCH
    ViewEvent.SessionPrecondition.PREWARM -> RumViewUpdateEvent.SessionPrecondition.PREWARM
    ViewEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION -> RumViewUpdateEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION
    ViewEvent.SessionPrecondition.EXPLICIT_STOP -> RumViewUpdateEvent.SessionPrecondition.EXPLICIT_STOP
}

// endregion
