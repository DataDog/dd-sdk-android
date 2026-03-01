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
        container = container.get()?.toUpdateEvent(),
        stream = stream.get()?.let { RumViewUpdateEvent.Stream(id = it.id) },
        view = view.toUpdateEvent(),
        session = session.toUpdateEvent(),
        featureFlags = featureFlags.get()?.let {
            it.additionalProperties.get()?.let { map ->
                RumViewUpdateEvent.FeatureFlags(map.toMutableMap())
            }
        },
        privacy = privacy.get()?.let {
            RumViewUpdateEvent.Privacy(replayLevel = it.replayLevel.toUpdateEvent())
        },
        display = display.get()?.toUpdateEventDisplay(),
        date = date,
        application = application.toUpdateEvent(),
        service = service.get(),
        version = version.get(),
        buildVersion = buildVersion.get(),
        buildId = buildId.get(),
        ddtags = ddtags.get(),
        source = source.get()?.toUpdateEvent(),
        usr = usr.get()?.toUpdateEvent(),
        account = account.get()?.toUpdateEvent(),
        connectivity = connectivity.get()?.toUpdateEvent(),
        synthetics = synthetics.get()?.let {
            RumViewUpdateEvent.Synthetics(
                testId = it.testId,
                resultId = it.resultId,
                injected = it.injected
            )
        },
        ciTest = ciTest.get()?.let {
            RumViewUpdateEvent.CiTest(testExecutionId = it.testExecutionId)
        },
        os = os.get()?.toUpdateEvent(),
        device = device.get()?.toUpdateEvent(),
        dd = dd.toUpdateEventDd(),
        context = context.get()?.let {
            RumViewUpdateEvent.FeatureFlags(it.additionalProperties)
        }
    )
}

// region Private - top-level nested types

private fun ViewEventDiff.ApplicationDiff.toUpdateEvent() = RumViewUpdateEvent.Application(
    id = id,
    currentLocale = currentLocale.get()
)

private fun ViewEventDiff.ViewEventSessionDiff.toUpdateEvent() = RumViewUpdateEvent.RumViewUpdateEventSession(
    isActive = isActive.get(),
    sampledForReplay = sampledForReplay.get(),
    id = id,
    type = type.toUpdateEvent(),
    hasReplay = hasReplay.get()
)

@Suppress("LongMethod")
private fun ViewEventDiff.ViewEventViewDiff.toUpdateEvent() = RumViewUpdateEvent.RumViewUpdateEventView(
    loadingTime = loadingTime.get(),
    networkSettledTime = networkSettledTime.get(),
    interactionToNextViewTime = interactionToNextViewTime.get(),
    loadingType = loadingType.get()?.toUpdateEvent(),
    timeSpent = timeSpent.get(),
    firstContentfulPaint = firstContentfulPaint.get(),
    largestContentfulPaint = largestContentfulPaint.get(),
    largestContentfulPaintTargetSelector = largestContentfulPaintTargetSelector.get(),
    firstInputDelay = firstInputDelay.get(),
    firstInputTime = firstInputTime.get(),
    firstInputTargetSelector = firstInputTargetSelector.get(),
    interactionToNextPaint = interactionToNextPaint.get(),
    interactionToNextPaintTime = interactionToNextPaintTime.get(),
    interactionToNextPaintTargetSelector = interactionToNextPaintTargetSelector.get(),
    cumulativeLayoutShift = cumulativeLayoutShift.get(),
    cumulativeLayoutShiftTime = cumulativeLayoutShiftTime.get(),
    cumulativeLayoutShiftTargetSelector = cumulativeLayoutShiftTargetSelector.get(),
    domComplete = domComplete.get(),
    domContentLoaded = domContentLoaded.get(),
    domInteractive = domInteractive.get(),
    loadEvent = loadEvent.get(),
    firstByte = firstByte.get(),
    customTimings = customTimings.get()?.let {
        RumViewUpdateEvent.CustomTimings(it.additionalProperties)
    },
    isActive = isActive.get(),
    isSlowRendered = isSlowRendered.get(),
    action = action.get()?.let { RumViewUpdateEvent.Action(it.count) },
    error = error.get()?.let { RumViewUpdateEvent.Error(it.count) },
    crash = crash.get()?.let { RumViewUpdateEvent.Crash(it.count) },
    longTask = longTask.get()?.let { RumViewUpdateEvent.LongTask(it.count) },
    frozenFrame = frozenFrame.get()?.let { RumViewUpdateEvent.FrozenFrame(it.count) },
    slowFrames = slowFrames.get()?.map {
        RumViewUpdateEvent.SlowFrame(start = it.start, duration = it.duration)
    },
    resource = resource.get()?.let { RumViewUpdateEvent.Resource(it.count) },
    frustration = frustration.get()?.let { RumViewUpdateEvent.Frustration(it.count) },
    inForegroundPeriods = inForegroundPeriods.get()?.map {
        RumViewUpdateEvent.InForegroundPeriod(start = it.start, duration = it.duration)
    },
    memoryAverage = memoryAverage.get(),
    memoryMax = memoryMax.get(),
    cpuTicksCount = cpuTicksCount.get(),
    cpuTicksPerSecond = cpuTicksPerSecond.get(),
    refreshRateAverage = refreshRateAverage.get(),
    refreshRateMin = refreshRateMin.get(),
    slowFramesRate = slowFramesRate.get(),
    freezeRate = freezeRate.get(),
    flutterBuildTime = flutterBuildTime.get()?.toUpdateEvent(),
    flutterRasterTime = flutterRasterTime.get()?.toUpdateEvent(),
    jsRefreshRate = jsRefreshRate.get()?.toUpdateEvent(),
    performance = performance.get()?.toUpdateEvent(),
    accessibility = accessibility.get()?.toUpdateEvent(),
    id = id,
    referrer = referrer.get(),
    url = url,
    name = name.get()
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
    session = session.get()?.let {
        RumViewUpdateEvent.DdSession(
            plan = it.plan?.toUpdateEvent(),
            sessionPrecondition = it.sessionPrecondition?.toUpdateEvent()
        )
    },
    configuration = configuration.get()?.let {
        RumViewUpdateEvent.Configuration(
            sessionSampleRate = it.sessionSampleRate,
            sessionReplaySampleRate = it.sessionReplaySampleRate,
            profilingSampleRate = it.profilingSampleRate,
            traceSampleRate = it.traceSampleRate
        )
    },
    browserSdkVersion = browserSdkVersion.get(),
    sdkName = sdkName.get(),
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
        cls = cls.get()?.let {
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
        },
        fcp = fcp.get()?.let { RumViewUpdateEvent.Fcp(it.timestamp) },
        fid = fid.get()?.let {
            RumViewUpdateEvent.Fid(it.duration, it.timestamp, it.targetSelector)
        },
        inp = inp.get()?.let {
            RumViewUpdateEvent.Inp(
                duration = it.duration,
                timestamp = it.timestamp,
                targetSelector = it.targetSelector,
                subParts = it.subParts?.let { s ->
                    RumViewUpdateEvent.InpSubParts(s.inputDelay, s.processingTime, s.presentationDelay)
                }
            )
        },
        lcp = lcp.get()?.let {
            RumViewUpdateEvent.Lcp(
                timestamp = it.timestamp,
                targetSelector = it.targetSelector,
                resourceUrl = it.resourceUrl,
                subParts = it.subParts?.let { s ->
                    RumViewUpdateEvent.LcpSubParts(s.loadDelay, s.loadTime, s.renderDelay)
                }
            )
        },
        fbc = fbc.get()?.let { RumViewUpdateEvent.Fbc(it.timestamp) }
    )

private fun ViewEventDiff.ViewEventViewDiff.AccessibilityDiff.toUpdateEvent() =
    RumViewUpdateEvent.Accessibility(
        textSize = textSize.get(),
        screenReaderEnabled = screenReaderEnabled.get(),
        boldTextEnabled = boldTextEnabled.get(),
        reduceTransparencyEnabled = reduceTransparencyEnabled.get(),
        reduceMotionEnabled = reduceMotionEnabled.get(),
        buttonShapesEnabled = buttonShapesEnabled.get(),
        invertColorsEnabled = invertColorsEnabled.get(),
        increaseContrastEnabled = increaseContrastEnabled.get(),
        assistiveSwitchEnabled = assistiveSwitchEnabled.get(),
        assistiveTouchEnabled = assistiveTouchEnabled.get(),
        videoAutoplayEnabled = videoAutoplayEnabled.get(),
        closedCaptioningEnabled = closedCaptioningEnabled.get(),
        monoAudioEnabled = monoAudioEnabled.get(),
        shakeToUndoEnabled = shakeToUndoEnabled.get(),
        reducedAnimationsEnabled = reducedAnimationsEnabled.get(),
        shouldDifferentiateWithoutColor = shouldDifferentiateWithoutColor.get(),
        grayscaleEnabled = grayscaleEnabled.get(),
        singleAppModeEnabled = singleAppModeEnabled.get(),
        onOffSwitchLabelsEnabled = onOffSwitchLabelsEnabled.get(),
        speakScreenEnabled = speakScreenEnabled.get(),
        speakSelectionEnabled = speakSelectionEnabled.get(),
        rtlEnabled = rtlEnabled.get()
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
