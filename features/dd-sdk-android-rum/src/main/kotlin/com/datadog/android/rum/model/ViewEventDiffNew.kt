/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.model

import com.datadog.android.rum.computeDiff
import com.datadog.android.rum.computeDiffRequired

@Suppress("LongMethod")
internal fun diffViewEvent(old: ViewEvent, new: ViewEvent): RumViewUpdateEvent? {
    return computeDiff(old = old, new = new) {
        RumViewUpdateEvent(
            date = diffRequired(ViewEvent::date),
            application = diffApplication(old.application, new.application),
            session = diffSession(old.session, new.session),
            view = diffView(old.view, new.view),
            dd = diffDd(old.dd, new.dd),
            featureFlags = diffMerge(ViewEvent::featureFlags, ::diffContext),
            service = diffEquals(ViewEvent::service),
            version = diffEquals(ViewEvent::version),
            buildVersion = diffEquals(ViewEvent::buildVersion),
            buildId = diffEquals(ViewEvent::buildId),
            ddtags = diffEquals(ViewEvent::ddtags),
            source = diffEquals(ViewEvent::source)?.toRumSource(),
            usr = diffEquals(ViewEvent::usr)?.toRum(),
            account = diffEquals(ViewEvent::account)?.toRum(),
            connectivity = diffEquals(ViewEvent::connectivity)?.toRum(),
            display = diffEquals(ViewEvent::display)?.toRum(),
            synthetics = diffEquals(ViewEvent::synthetics)?.let {
                RumViewUpdateEvent.Synthetics(testId = it.testId, resultId = it.resultId, injected = it.injected)
            },
            ciTest = diffEquals(ViewEvent::ciTest)?.let {
                RumViewUpdateEvent.CiTest(testExecutionId = it.testExecutionId)
            },
            os = diffEquals(ViewEvent::os)?.toRum(),
            device = diffEquals(ViewEvent::device)?.toRum(),
            context = diffEquals(ViewEvent::context)?.let { RumViewUpdateEvent.FeatureFlags(it.additionalProperties) },
            stream = diffEquals(ViewEvent::stream)?.let { RumViewUpdateEvent.Stream(id = it.id) },
            container = diffEquals(ViewEvent::container)?.toRum(),
            privacy = diffEquals(ViewEvent::privacy)?.let { RumViewUpdateEvent.Privacy(replayLevel = it.replayLevel.toRum()) },
        )
    }
}

// region Diff helpers — merged required sub-objects (always produced via computeDiffRequired)

private fun diffApplication(
    old: ViewEvent.Application,
    new: ViewEvent.Application
): RumViewUpdateEvent.Application {
    return computeDiffRequired(old = old, new = new) {
        RumViewUpdateEvent.Application(
            id = diffRequired(ViewEvent.Application::id),
            currentLocale = diffEquals(ViewEvent.Application::currentLocale),
        )
    }
}

private fun diffSession(
    old: ViewEvent.ViewEventSession,
    new: ViewEvent.ViewEventSession
): RumViewUpdateEvent.RumViewUpdateEventSession {
    return computeDiffRequired(old = old, new = new) {
        RumViewUpdateEvent.RumViewUpdateEventSession(
            id = diffRequired(ViewEvent.ViewEventSession::id),
            type = diffRequired(ViewEvent.ViewEventSession::type).toRum(),
            hasReplay = diffEquals(ViewEvent.ViewEventSession::hasReplay),
            isActive = diffEquals(ViewEvent.ViewEventSession::isActive),
            sampledForReplay = diffEquals(ViewEvent.ViewEventSession::sampledForReplay),
        )
    }
}

@Suppress("LongMethod")
private fun diffView(
    old: ViewEvent.ViewEventView,
    new: ViewEvent.ViewEventView
): RumViewUpdateEvent.RumViewUpdateEventView {
    return computeDiffRequired(old = old, new = new) {
        val slowFrames = diffList<ViewEvent.SlowFrame> { slowFrames ?: emptyList() }
        val inForegroundPeriods = diffList<ViewEvent.InForegroundPeriod> { inForegroundPeriods ?: emptyList() }
        RumViewUpdateEvent.RumViewUpdateEventView(
            id = diffRequired(ViewEvent.ViewEventView::id),
            url = diffRequired(ViewEvent.ViewEventView::url),
            referrer = diffEquals(ViewEvent.ViewEventView::referrer),
            name = diffEquals(ViewEvent.ViewEventView::name),
            loadingTime = diffEquals(ViewEvent.ViewEventView::loadingTime),
            networkSettledTime = diffEquals(ViewEvent.ViewEventView::networkSettledTime),
            interactionToNextViewTime = diffEquals(ViewEvent.ViewEventView::interactionToNextViewTime),
            loadingType = diffEquals(ViewEvent.ViewEventView::loadingType)?.toRum(),
            timeSpent = diffEquals(ViewEvent.ViewEventView::timeSpent),
            firstContentfulPaint = diffEquals(ViewEvent.ViewEventView::firstContentfulPaint),
            largestContentfulPaint = diffEquals(ViewEvent.ViewEventView::largestContentfulPaint),
            largestContentfulPaintTargetSelector = diffEquals(ViewEvent.ViewEventView::largestContentfulPaintTargetSelector),
            firstInputDelay = diffEquals(ViewEvent.ViewEventView::firstInputDelay),
            firstInputTime = diffEquals(ViewEvent.ViewEventView::firstInputTime),
            firstInputTargetSelector = diffEquals(ViewEvent.ViewEventView::firstInputTargetSelector),
            interactionToNextPaint = diffEquals(ViewEvent.ViewEventView::interactionToNextPaint),
            interactionToNextPaintTime = diffEquals(ViewEvent.ViewEventView::interactionToNextPaintTime),
            interactionToNextPaintTargetSelector = diffEquals(ViewEvent.ViewEventView::interactionToNextPaintTargetSelector),
            cumulativeLayoutShift = diffEquals(ViewEvent.ViewEventView::cumulativeLayoutShift),
            cumulativeLayoutShiftTime = diffEquals(ViewEvent.ViewEventView::cumulativeLayoutShiftTime),
            cumulativeLayoutShiftTargetSelector = diffEquals(ViewEvent.ViewEventView::cumulativeLayoutShiftTargetSelector),
            domComplete = diffEquals(ViewEvent.ViewEventView::domComplete),
            domContentLoaded = diffEquals(ViewEvent.ViewEventView::domContentLoaded),
            domInteractive = diffEquals(ViewEvent.ViewEventView::domInteractive),
            loadEvent = diffEquals(ViewEvent.ViewEventView::loadEvent),
            firstByte = diffEquals(ViewEvent.ViewEventView::firstByte),
            customTimings = diffEquals(ViewEvent.ViewEventView::customTimings)?.let {
                RumViewUpdateEvent.CustomTimings(it.additionalProperties)
            },
            isActive = diffEquals(ViewEvent.ViewEventView::isActive),
            isSlowRendered = diffEquals(ViewEvent.ViewEventView::isSlowRendered),
            action = diffEquals(ViewEvent.ViewEventView::action)?.let { RumViewUpdateEvent.Action(it.count) },
            error = diffEquals(ViewEvent.ViewEventView::error)?.let { RumViewUpdateEvent.Error(it.count) },
            crash = diffEquals(ViewEvent.ViewEventView::crash)?.let { RumViewUpdateEvent.Crash(it.count) },
            longTask = diffEquals(ViewEvent.ViewEventView::longTask)?.let { RumViewUpdateEvent.LongTask(it.count) },
            frozenFrame = diffEquals(ViewEvent.ViewEventView::frozenFrame)?.let { RumViewUpdateEvent.FrozenFrame(it.count) },
            slowFrames = slowFrames.takeIf { it.isNotEmpty() }?.map {
                RumViewUpdateEvent.SlowFrame(start = it.start, duration = it.duration)
            },
            resource = diffEquals(ViewEvent.ViewEventView::resource)?.let { RumViewUpdateEvent.Resource(it.count) },
            frustration = diffEquals(ViewEvent.ViewEventView::frustration)?.let { RumViewUpdateEvent.Frustration(it.count) },
            inForegroundPeriods = inForegroundPeriods.takeIf { it.isNotEmpty() }?.map {
                RumViewUpdateEvent.InForegroundPeriod(start = it.start, duration = it.duration)
            },
            memoryAverage = diffEquals(ViewEvent.ViewEventView::memoryAverage),
            memoryMax = diffEquals(ViewEvent.ViewEventView::memoryMax),
            cpuTicksCount = diffEquals(ViewEvent.ViewEventView::cpuTicksCount),
            cpuTicksPerSecond = diffEquals(ViewEvent.ViewEventView::cpuTicksPerSecond),
            refreshRateAverage = diffEquals(ViewEvent.ViewEventView::refreshRateAverage),
            refreshRateMin = diffEquals(ViewEvent.ViewEventView::refreshRateMin),
            slowFramesRate = diffEquals(ViewEvent.ViewEventView::slowFramesRate),
            freezeRate = diffEquals(ViewEvent.ViewEventView::freezeRate),
            flutterBuildTime = diffEquals(ViewEvent.ViewEventView::flutterBuildTime)?.toRum(),
            flutterRasterTime = diffEquals(ViewEvent.ViewEventView::flutterRasterTime)?.toRum(),
            jsRefreshRate = diffEquals(ViewEvent.ViewEventView::jsRefreshRate)?.toRum(),
            performance = diffMerge(ViewEvent.ViewEventView::performance, ::diffPerformance),
            accessibility = diffMerge(ViewEvent.ViewEventView::accessibility, ::diffAccessibility),
        )
    }
}

private fun diffDd(old: ViewEvent.Dd, new: ViewEvent.Dd): RumViewUpdateEvent.Dd {
    return computeDiffRequired(old = old, new = new) {
        RumViewUpdateEvent.Dd(
            documentVersion = diffRequired(ViewEvent.Dd::documentVersion),
            session = diffEquals(ViewEvent.Dd::session)?.let {
                RumViewUpdateEvent.DdSession(
                    plan = it.plan?.toRum(),
                    sessionPrecondition = it.sessionPrecondition?.toRum(),
                )
            },
            configuration = diffEquals(ViewEvent.Dd::configuration)?.let {
                RumViewUpdateEvent.Configuration(
                    sessionSampleRate = it.sessionSampleRate,
                    sessionReplaySampleRate = it.sessionReplaySampleRate,
                    profilingSampleRate = it.profilingSampleRate,
                    traceSampleRate = it.traceSampleRate,
                )
            },
            browserSdkVersion = diffEquals(ViewEvent.Dd::browserSdkVersion),
            sdkName = diffEquals(ViewEvent.Dd::sdkName),
        )
    }
}

// endregion

// region Diff helpers — nullable merged sub-objects

private fun diffContext(old: ViewEvent.Context?, new: ViewEvent.Context?): RumViewUpdateEvent.FeatureFlags? {
    if (new == null) return null
    return computeDiff(old = old ?: ViewEvent.Context(), new = new) {
        RumViewUpdateEvent.FeatureFlags(diffMap(ViewEvent.Context::additionalProperties).toMutableMap())
    }
}

private fun diffPerformance(old: ViewEvent.Performance?, new: ViewEvent.Performance?): RumViewUpdateEvent.Performance? {
    if (new == null) return null
    return computeDiff(old = old ?: ViewEvent.Performance(), new = new) {
        RumViewUpdateEvent.Performance(
            cls = diffEquals(ViewEvent.Performance::cls)?.toRum(),
            fcp = diffEquals(ViewEvent.Performance::fcp)?.let { RumViewUpdateEvent.Fcp(timestamp = it.timestamp) },
            fid = diffEquals(ViewEvent.Performance::fid)?.let {
                RumViewUpdateEvent.Fid(duration = it.duration, timestamp = it.timestamp, targetSelector = it.targetSelector)
            },
            inp = diffEquals(ViewEvent.Performance::inp)?.toRum(),
            lcp = diffEquals(ViewEvent.Performance::lcp)?.toRum(),
            fbc = diffEquals(ViewEvent.Performance::fbc)?.let { RumViewUpdateEvent.Fbc(timestamp = it.timestamp) },
        )
    }
}

@Suppress("LongMethod")
private fun diffAccessibility(old: ViewEvent.Accessibility?, new: ViewEvent.Accessibility?): RumViewUpdateEvent.Accessibility? {
    if (new == null) return null
    return computeDiff(old = old ?: ViewEvent.Accessibility(), new = new) {
        RumViewUpdateEvent.Accessibility(
            textSize = diffEquals(ViewEvent.Accessibility::textSize),
            screenReaderEnabled = diffEquals(ViewEvent.Accessibility::screenReaderEnabled),
            boldTextEnabled = diffEquals(ViewEvent.Accessibility::boldTextEnabled),
            reduceTransparencyEnabled = diffEquals(ViewEvent.Accessibility::reduceTransparencyEnabled),
            reduceMotionEnabled = diffEquals(ViewEvent.Accessibility::reduceMotionEnabled),
            buttonShapesEnabled = diffEquals(ViewEvent.Accessibility::buttonShapesEnabled),
            invertColorsEnabled = diffEquals(ViewEvent.Accessibility::invertColorsEnabled),
            increaseContrastEnabled = diffEquals(ViewEvent.Accessibility::increaseContrastEnabled),
            assistiveSwitchEnabled = diffEquals(ViewEvent.Accessibility::assistiveSwitchEnabled),
            assistiveTouchEnabled = diffEquals(ViewEvent.Accessibility::assistiveTouchEnabled),
            videoAutoplayEnabled = diffEquals(ViewEvent.Accessibility::videoAutoplayEnabled),
            closedCaptioningEnabled = diffEquals(ViewEvent.Accessibility::closedCaptioningEnabled),
            monoAudioEnabled = diffEquals(ViewEvent.Accessibility::monoAudioEnabled),
            shakeToUndoEnabled = diffEquals(ViewEvent.Accessibility::shakeToUndoEnabled),
            reducedAnimationsEnabled = diffEquals(ViewEvent.Accessibility::reducedAnimationsEnabled),
            shouldDifferentiateWithoutColor = diffEquals(ViewEvent.Accessibility::shouldDifferentiateWithoutColor),
            grayscaleEnabled = diffEquals(ViewEvent.Accessibility::grayscaleEnabled),
            singleAppModeEnabled = diffEquals(ViewEvent.Accessibility::singleAppModeEnabled),
            onOffSwitchLabelsEnabled = diffEquals(ViewEvent.Accessibility::onOffSwitchLabelsEnabled),
            speakScreenEnabled = diffEquals(ViewEvent.Accessibility::speakScreenEnabled),
            speakSelectionEnabled = diffEquals(ViewEvent.Accessibility::speakSelectionEnabled),
            rtlEnabled = diffEquals(ViewEvent.Accessibility::rtlEnabled),
        )
    }
}

// endregion

// region Type conversions

private fun ViewEvent.Usr.toRum() = RumViewUpdateEvent.Usr(
    id = id,
    name = name,
    email = email,
    anonymousId = anonymousId,
    additionalProperties = additionalProperties,
)

private fun ViewEvent.Account.toRum() = RumViewUpdateEvent.Account(
    id = id,
    name = name,
    additionalProperties = additionalProperties,
)

private fun ViewEvent.Connectivity.toRum() = RumViewUpdateEvent.Connectivity(
    status = status.toRum(),
    interfaces = interfaces?.map { it.toRum() },
    effectiveType = effectiveType?.toRum(),
    cellular = cellular?.let { RumViewUpdateEvent.Cellular(it.technology, it.carrierName) },
)

private fun ViewEvent.Display.toRum() = RumViewUpdateEvent.Display(
    scroll = scroll?.let {
        RumViewUpdateEvent.Scroll(it.maxDepth, it.maxScrollHeight, it.maxScrollHeight, it.maxScrollHeightTime)
    },
    viewport = viewport?.let { RumViewUpdateEvent.Viewport(it.width, it.height) },
)

private fun ViewEvent.Os.toRum() = RumViewUpdateEvent.Os(
    name = name,
    version = version,
    build = build,
    versionMajor = versionMajor,
)

private fun ViewEvent.Device.toRum() = RumViewUpdateEvent.Device(
    type = type?.toRum(),
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
    isLowRam = isLowRam,
)

private fun ViewEvent.Container.toRum() = RumViewUpdateEvent.Container(
    view = RumViewUpdateEvent.ContainerView(view.id),
    source = source.toRumSource(),
)

private fun ViewEvent.FlutterBuildTime.toRum() = RumViewUpdateEvent.FlutterBuildTime(
    min = min,
    max = max,
    average = average,
    metricMax = metricMax,
)

private fun ViewEvent.PerformanceCls.toRum() = RumViewUpdateEvent.Cls(
    score = score,
    timestamp = timestamp,
    targetSelector = targetSelector,
    previousRect = previousRect?.let { RumViewUpdateEvent.PreviousRect(it.x, it.y, it.width, it.height) },
    currentRect = currentRect?.let { RumViewUpdateEvent.PreviousRect(it.x, it.y, it.width, it.height) },
)

private fun ViewEvent.Inp.toRum() = RumViewUpdateEvent.Inp(
    duration = duration,
    timestamp = timestamp,
    targetSelector = targetSelector,
    subParts = subParts?.let {
        RumViewUpdateEvent.InpSubParts(it.inputDelay, it.processingTime, it.presentationDelay)
    },
)

private fun ViewEvent.Lcp.toRum() = RumViewUpdateEvent.Lcp(
    timestamp = timestamp,
    targetSelector = targetSelector,
    resourceUrl = resourceUrl,
    subParts = subParts?.let {
        RumViewUpdateEvent.LcpSubParts(it.loadDelay, it.loadTime, it.renderDelay)
    },
)

// endregion

// region Enum conversions

private fun ViewEvent.ViewEventSource.toRumSource() = when (this) {
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

private fun ViewEvent.ViewEventSessionType.toRum() = when (this) {
    ViewEvent.ViewEventSessionType.USER -> RumViewUpdateEvent.RumViewUpdateEventSessionType.USER
    ViewEvent.ViewEventSessionType.SYNTHETICS -> RumViewUpdateEvent.RumViewUpdateEventSessionType.SYNTHETICS
    ViewEvent.ViewEventSessionType.CI_TEST -> RumViewUpdateEvent.RumViewUpdateEventSessionType.CI_TEST
}

private fun ViewEvent.LoadingType.toRum() = when (this) {
    ViewEvent.LoadingType.INITIAL_LOAD -> RumViewUpdateEvent.LoadingType.INITIAL_LOAD
    ViewEvent.LoadingType.ROUTE_CHANGE -> RumViewUpdateEvent.LoadingType.ROUTE_CHANGE
    ViewEvent.LoadingType.ACTIVITY_DISPLAY -> RumViewUpdateEvent.LoadingType.ACTIVITY_DISPLAY
    ViewEvent.LoadingType.ACTIVITY_REDISPLAY -> RumViewUpdateEvent.LoadingType.ACTIVITY_REDISPLAY
    ViewEvent.LoadingType.FRAGMENT_DISPLAY -> RumViewUpdateEvent.LoadingType.FRAGMENT_DISPLAY
    ViewEvent.LoadingType.FRAGMENT_REDISPLAY -> RumViewUpdateEvent.LoadingType.FRAGMENT_REDISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_DISPLAY -> RumViewUpdateEvent.LoadingType.VIEW_CONTROLLER_DISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY -> RumViewUpdateEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY
}

private fun ViewEvent.ConnectivityStatus.toRum() = when (this) {
    ViewEvent.ConnectivityStatus.CONNECTED -> RumViewUpdateEvent.Status.CONNECTED
    ViewEvent.ConnectivityStatus.NOT_CONNECTED -> RumViewUpdateEvent.Status.NOT_CONNECTED
    ViewEvent.ConnectivityStatus.MAYBE -> RumViewUpdateEvent.Status.MAYBE
}

private fun ViewEvent.Interface.toRum() = when (this) {
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

private fun ViewEvent.EffectiveType.toRum() = when (this) {
    ViewEvent.EffectiveType.SLOW_2G -> RumViewUpdateEvent.EffectiveType.SLOW_2G
    ViewEvent.EffectiveType.`2G` -> RumViewUpdateEvent.EffectiveType.`2G`
    ViewEvent.EffectiveType.`3G` -> RumViewUpdateEvent.EffectiveType.`3G`
    ViewEvent.EffectiveType.`4G` -> RumViewUpdateEvent.EffectiveType.`4G`
}

private fun ViewEvent.DeviceType.toRum() = when (this) {
    ViewEvent.DeviceType.MOBILE -> RumViewUpdateEvent.DeviceType.MOBILE
    ViewEvent.DeviceType.DESKTOP -> RumViewUpdateEvent.DeviceType.DESKTOP
    ViewEvent.DeviceType.TABLET -> RumViewUpdateEvent.DeviceType.TABLET
    ViewEvent.DeviceType.TV -> RumViewUpdateEvent.DeviceType.TV
    ViewEvent.DeviceType.GAMING_CONSOLE -> RumViewUpdateEvent.DeviceType.GAMING_CONSOLE
    ViewEvent.DeviceType.BOT -> RumViewUpdateEvent.DeviceType.BOT
    ViewEvent.DeviceType.OTHER -> RumViewUpdateEvent.DeviceType.OTHER
}

private fun ViewEvent.ReplayLevel.toRum() = when (this) {
    ViewEvent.ReplayLevel.ALLOW -> RumViewUpdateEvent.ReplayLevel.ALLOW
    ViewEvent.ReplayLevel.MASK -> RumViewUpdateEvent.ReplayLevel.MASK
    ViewEvent.ReplayLevel.MASK_USER_INPUT -> RumViewUpdateEvent.ReplayLevel.MASK_USER_INPUT
}

private fun ViewEvent.Plan.toRum() = when (this) {
    ViewEvent.Plan.PLAN_1 -> RumViewUpdateEvent.Plan.PLAN_1
    ViewEvent.Plan.PLAN_2 -> RumViewUpdateEvent.Plan.PLAN_2
}

private fun ViewEvent.SessionPrecondition.toRum() = when (this) {
    ViewEvent.SessionPrecondition.USER_APP_LAUNCH -> RumViewUpdateEvent.SessionPrecondition.USER_APP_LAUNCH
    ViewEvent.SessionPrecondition.INACTIVITY_TIMEOUT -> RumViewUpdateEvent.SessionPrecondition.INACTIVITY_TIMEOUT
    ViewEvent.SessionPrecondition.MAX_DURATION -> RumViewUpdateEvent.SessionPrecondition.MAX_DURATION
    ViewEvent.SessionPrecondition.BACKGROUND_LAUNCH -> RumViewUpdateEvent.SessionPrecondition.BACKGROUND_LAUNCH
    ViewEvent.SessionPrecondition.PREWARM -> RumViewUpdateEvent.SessionPrecondition.PREWARM
    ViewEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION -> RumViewUpdateEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION
    ViewEvent.SessionPrecondition.EXPLICIT_STOP -> RumViewUpdateEvent.SessionPrecondition.EXPLICIT_STOP
}

// endregion
