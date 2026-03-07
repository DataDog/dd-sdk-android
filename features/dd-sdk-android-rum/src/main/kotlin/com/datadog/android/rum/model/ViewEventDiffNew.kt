/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.model

import com.datadog.android.internal.utils.computeDiffIfChanged
import com.datadog.android.internal.utils.computeDiffRequired

// TODO WAHAHA go through all fields again and correct merge semantics
@Suppress("LongMethod")
internal fun diffViewEvent(old: ViewEvent, new: ViewEvent): ViewUpdateEvent {
    return computeDiffRequired(old = old, new = new) {
        ViewUpdateEvent(
            date = diffRequired(ViewEvent::date),
            application = diffApplication(old.application, new.application),
            session = diffSession(old.session, new.session),
            view = diffView(old.view, new.view),
            dd = diffDd(old.dd, new.dd),
            featureFlags = diffMerge({ featureFlags ?: ViewEvent.Context() }, ::diffFeatureFlags),
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
                ViewUpdateEvent.Synthetics(testId = it.testId, resultId = it.resultId, injected = it.injected)
            },
            ciTest = diffEquals(ViewEvent::ciTest)?.let {
                ViewUpdateEvent.CiTest(testExecutionId = it.testExecutionId)
            },
            os = diffEquals(ViewEvent::os)?.toRum(),
            device = diffEquals(ViewEvent::device)?.toRum(),
            context = diffEquals(ViewEvent::context)?.let { ViewUpdateEvent.FeatureFlags(it.additionalProperties) },
            container = diffEquals(ViewEvent::container)?.toRum(),
            privacy = diffEquals(ViewEvent::privacy)?.let { ViewUpdateEvent.Privacy(replayLevel = it.replayLevel.toRum()) },
        )
    }
}

// region Diff helpers — merged required sub-objects (always produced via computeDiffRequired)

private fun diffApplication(
    old: ViewEvent.Application,
    new: ViewEvent.Application
): ViewUpdateEvent.Application {
    return computeDiffRequired(old = old, new = new) {
        ViewUpdateEvent.Application(
            id = diffRequired(ViewEvent.Application::id),
            currentLocale = diffEquals(ViewEvent.Application::currentLocale),
        )
    }
}

private fun diffSession(
    old: ViewEvent.ViewEventSession,
    new: ViewEvent.ViewEventSession
): ViewUpdateEvent.ViewUpdateEventSession {
    return computeDiffRequired(old = old, new = new) {
        ViewUpdateEvent.ViewUpdateEventSession(
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
): ViewUpdateEvent.ViewUpdateEventView {
    return computeDiffRequired(old = old, new = new) {
        val slowFrames = diffList(ViewEvent.ViewEventView::slowFrames)
        val inForegroundPeriods = diffList(ViewEvent.ViewEventView::inForegroundPeriods)

        ViewUpdateEvent.ViewUpdateEventView(
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
            customTimings = diffEquals(ViewEvent.ViewEventView::customTimings)?.let { ViewUpdateEvent.CustomTimings(it.additionalProperties) },
            isActive = diffEquals(ViewEvent.ViewEventView::isActive),
            isSlowRendered = diffEquals(ViewEvent.ViewEventView::isSlowRendered),
            action = diffEquals(ViewEvent.ViewEventView::action)?.let { ViewUpdateEvent.Action(it.count) },
            error = diffEquals(ViewEvent.ViewEventView::error)?.let { ViewUpdateEvent.Error(it.count) },
            crash = diffEquals(ViewEvent.ViewEventView::crash)?.let { ViewUpdateEvent.Crash(it.count) },
            longTask = diffEquals(ViewEvent.ViewEventView::longTask)?.let { ViewUpdateEvent.LongTask(it.count) },
            frozenFrame = diffEquals(ViewEvent.ViewEventView::frozenFrame)?.let { ViewUpdateEvent.FrozenFrame(it.count) },
            slowFrames = slowFrames?.map { ViewUpdateEvent.SlowFrame(start = it.start, duration = it.duration) },
            resource = diffEquals(ViewEvent.ViewEventView::resource)?.let { ViewUpdateEvent.Resource(it.count) },
            frustration = diffEquals(ViewEvent.ViewEventView::frustration)?.let { ViewUpdateEvent.Frustration(it.count) },
            inForegroundPeriods = inForegroundPeriods?.map { ViewUpdateEvent.InForegroundPeriod(start = it.start, duration = it.duration) },
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
            performance = diffMerge({ performance ?: ViewEvent.Performance() }, ::diffPerformance),
            accessibility = diffMerge({ accessibility ?: ViewEvent.Accessibility() }, ::diffAccessibility),
        )
    }
}

private fun diffDd(old: ViewEvent.Dd, new: ViewEvent.Dd): ViewUpdateEvent.Dd {
    return computeDiffRequired(old = old, new = new) {
        ViewUpdateEvent.Dd(
            documentVersion = diffRequired(ViewEvent.Dd::documentVersion),
            session = diffEquals(ViewEvent.Dd::session)?.let {
                ViewUpdateEvent.DdSession(
                    plan = it.plan?.toRum(),
                    sessionPrecondition = it.sessionPrecondition?.toRum(),
                )
            },
            configuration = diffEquals(ViewEvent.Dd::configuration)?.let {
                ViewUpdateEvent.Configuration(
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

private fun diffFeatureFlags(old: ViewEvent.Context, new: ViewEvent.Context): ViewUpdateEvent.FeatureFlags? {
    return computeDiffIfChanged(old = old, new = new) {
        ViewUpdateEvent.FeatureFlags(diffMap(ViewEvent.Context::additionalProperties).toMutableMap()) // TODO WAHAHA
    }
}

private fun diffPerformance(old: ViewEvent.Performance, new: ViewEvent.Performance): ViewUpdateEvent.Performance? {
    return computeDiffIfChanged(old = old, new = new) {
        ViewUpdateEvent.Performance(
            cls = diffEquals(ViewEvent.Performance::cls)?.toRum(),
            fcp = diffEquals(ViewEvent.Performance::fcp)?.let { ViewUpdateEvent.Fcp(timestamp = it.timestamp) },
            fid = diffEquals(ViewEvent.Performance::fid)?.let {
                ViewUpdateEvent.Fid(duration = it.duration, timestamp = it.timestamp, targetSelector = it.targetSelector)
            },
            inp = diffEquals(ViewEvent.Performance::inp)?.toRum(),
            lcp = diffEquals(ViewEvent.Performance::lcp)?.toRum(),
            fbc = diffEquals(ViewEvent.Performance::fbc)?.let { ViewUpdateEvent.Fbc(timestamp = it.timestamp) },
        )
    }
}

@Suppress("LongMethod")
private fun diffAccessibility(old: ViewEvent.Accessibility, new: ViewEvent.Accessibility): ViewUpdateEvent.Accessibility? {
    return computeDiffIfChanged(old = old, new = new) {
        ViewUpdateEvent.Accessibility(
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

private fun ViewEvent.Usr.toRum() = ViewUpdateEvent.Usr(
    id = id,
    name = name,
    email = email,
    anonymousId = anonymousId,
    additionalProperties = additionalProperties,
)

private fun ViewEvent.Account.toRum() = ViewUpdateEvent.Account(
    id = id,
    name = name,
    additionalProperties = additionalProperties,
)

private fun ViewEvent.Connectivity.toRum() = ViewUpdateEvent.Connectivity(
    status = status.toRum(),
    interfaces = interfaces?.map { it.toRum() },
    effectiveType = effectiveType?.toRum(),
    cellular = cellular?.let { ViewUpdateEvent.Cellular(it.technology, it.carrierName) },
)

private fun ViewEvent.Display.toRum() = ViewUpdateEvent.Display(
    scroll = scroll?.let {
        ViewUpdateEvent.Scroll(it.maxDepth, it.maxScrollHeight, it.maxScrollHeight, it.maxScrollHeightTime)
    },
    viewport = viewport?.let { ViewUpdateEvent.Viewport(it.width, it.height) },
)

private fun ViewEvent.Os.toRum() = ViewUpdateEvent.Os(
    name = name,
    version = version,
    build = build,
    versionMajor = versionMajor,
)

private fun ViewEvent.Device.toRum() = ViewUpdateEvent.Device(
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

private fun ViewEvent.Container.toRum() = ViewUpdateEvent.Container(
    view = ViewUpdateEvent.ContainerView(view.id),
    source = source.toRumSource(),
)

private fun ViewEvent.FlutterBuildTime.toRum() = ViewUpdateEvent.FlutterBuildTime(
    min = min,
    max = max,
    average = average,
    metricMax = metricMax,
)

private fun ViewEvent.PerformanceCls.toRum() = ViewUpdateEvent.Cls(
    score = score,
    timestamp = timestamp,
    targetSelector = targetSelector,
    previousRect = previousRect?.let { ViewUpdateEvent.PreviousRect(it.x, it.y, it.width, it.height) },
    currentRect = currentRect?.let { ViewUpdateEvent.PreviousRect(it.x, it.y, it.width, it.height) },
)

private fun ViewEvent.Inp.toRum() = ViewUpdateEvent.Inp(
    duration = duration,
    timestamp = timestamp,
    targetSelector = targetSelector,
    subParts = subParts?.let {
        ViewUpdateEvent.InpSubParts(it.inputDelay, it.processingTime, it.presentationDelay)
    },
)

private fun ViewEvent.Lcp.toRum() = ViewUpdateEvent.Lcp(
    timestamp = timestamp,
    targetSelector = targetSelector,
    resourceUrl = resourceUrl,
    subParts = subParts?.let {
        ViewUpdateEvent.LcpSubParts(it.loadDelay, it.loadTime, it.renderDelay)
    },
)

// endregion

// region Enum conversions

private fun ViewEvent.ViewEventSource.toRumSource() = when (this) {
    ViewEvent.ViewEventSource.ANDROID -> ViewUpdateEvent.ViewUpdateEventSource.ANDROID
    ViewEvent.ViewEventSource.IOS -> ViewUpdateEvent.ViewUpdateEventSource.IOS
    ViewEvent.ViewEventSource.BROWSER -> ViewUpdateEvent.ViewUpdateEventSource.BROWSER
    ViewEvent.ViewEventSource.FLUTTER -> ViewUpdateEvent.ViewUpdateEventSource.FLUTTER
    ViewEvent.ViewEventSource.REACT_NATIVE -> ViewUpdateEvent.ViewUpdateEventSource.REACT_NATIVE
    ViewEvent.ViewEventSource.ROKU -> ViewUpdateEvent.ViewUpdateEventSource.ROKU
    ViewEvent.ViewEventSource.UNITY -> ViewUpdateEvent.ViewUpdateEventSource.UNITY
    ViewEvent.ViewEventSource.KOTLIN_MULTIPLATFORM -> ViewUpdateEvent.ViewUpdateEventSource.KOTLIN_MULTIPLATFORM
    ViewEvent.ViewEventSource.ELECTRON -> ViewUpdateEvent.ViewUpdateEventSource.ELECTRON
}

private fun ViewEvent.ViewEventSessionType.toRum() = when (this) {
    ViewEvent.ViewEventSessionType.USER -> ViewUpdateEvent.ViewUpdateEventSessionType.USER
    ViewEvent.ViewEventSessionType.SYNTHETICS -> ViewUpdateEvent.ViewUpdateEventSessionType.SYNTHETICS
    ViewEvent.ViewEventSessionType.CI_TEST -> ViewUpdateEvent.ViewUpdateEventSessionType.CI_TEST
}

private fun ViewEvent.LoadingType.toRum() = when (this) {
    ViewEvent.LoadingType.INITIAL_LOAD -> ViewUpdateEvent.LoadingType.INITIAL_LOAD
    ViewEvent.LoadingType.ROUTE_CHANGE -> ViewUpdateEvent.LoadingType.ROUTE_CHANGE
    ViewEvent.LoadingType.ACTIVITY_DISPLAY -> ViewUpdateEvent.LoadingType.ACTIVITY_DISPLAY
    ViewEvent.LoadingType.ACTIVITY_REDISPLAY -> ViewUpdateEvent.LoadingType.ACTIVITY_REDISPLAY
    ViewEvent.LoadingType.FRAGMENT_DISPLAY -> ViewUpdateEvent.LoadingType.FRAGMENT_DISPLAY
    ViewEvent.LoadingType.FRAGMENT_REDISPLAY -> ViewUpdateEvent.LoadingType.FRAGMENT_REDISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_DISPLAY -> ViewUpdateEvent.LoadingType.VIEW_CONTROLLER_DISPLAY
    ViewEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY -> ViewUpdateEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY
}

private fun ViewEvent.ConnectivityStatus.toRum() = when (this) {
    ViewEvent.ConnectivityStatus.CONNECTED -> ViewUpdateEvent.Status.CONNECTED
    ViewEvent.ConnectivityStatus.NOT_CONNECTED -> ViewUpdateEvent.Status.NOT_CONNECTED
    ViewEvent.ConnectivityStatus.MAYBE -> ViewUpdateEvent.Status.MAYBE
}

private fun ViewEvent.Interface.toRum() = when (this) {
    ViewEvent.Interface.BLUETOOTH -> ViewUpdateEvent.Interface.BLUETOOTH
    ViewEvent.Interface.CELLULAR -> ViewUpdateEvent.Interface.CELLULAR
    ViewEvent.Interface.ETHERNET -> ViewUpdateEvent.Interface.ETHERNET
    ViewEvent.Interface.WIFI -> ViewUpdateEvent.Interface.WIFI
    ViewEvent.Interface.WIMAX -> ViewUpdateEvent.Interface.WIMAX
    ViewEvent.Interface.MIXED -> ViewUpdateEvent.Interface.MIXED
    ViewEvent.Interface.OTHER -> ViewUpdateEvent.Interface.OTHER
    ViewEvent.Interface.UNKNOWN -> ViewUpdateEvent.Interface.UNKNOWN
    ViewEvent.Interface.NONE -> ViewUpdateEvent.Interface.NONE
}

private fun ViewEvent.EffectiveType.toRum() = when (this) {
    ViewEvent.EffectiveType.SLOW_2G -> ViewUpdateEvent.EffectiveType.SLOW_2G
    ViewEvent.EffectiveType.`2G` -> ViewUpdateEvent.EffectiveType.`2G`
    ViewEvent.EffectiveType.`3G` -> ViewUpdateEvent.EffectiveType.`3G`
    ViewEvent.EffectiveType.`4G` -> ViewUpdateEvent.EffectiveType.`4G`
}

private fun ViewEvent.DeviceType.toRum() = when (this) {
    ViewEvent.DeviceType.MOBILE -> ViewUpdateEvent.DeviceType.MOBILE
    ViewEvent.DeviceType.DESKTOP -> ViewUpdateEvent.DeviceType.DESKTOP
    ViewEvent.DeviceType.TABLET -> ViewUpdateEvent.DeviceType.TABLET
    ViewEvent.DeviceType.TV -> ViewUpdateEvent.DeviceType.TV
    ViewEvent.DeviceType.GAMING_CONSOLE -> ViewUpdateEvent.DeviceType.GAMING_CONSOLE
    ViewEvent.DeviceType.BOT -> ViewUpdateEvent.DeviceType.BOT
    ViewEvent.DeviceType.OTHER -> ViewUpdateEvent.DeviceType.OTHER
}

private fun ViewEvent.ReplayLevel.toRum() = when (this) {
    ViewEvent.ReplayLevel.ALLOW -> ViewUpdateEvent.ReplayLevel.ALLOW
    ViewEvent.ReplayLevel.MASK -> ViewUpdateEvent.ReplayLevel.MASK
    ViewEvent.ReplayLevel.MASK_USER_INPUT -> ViewUpdateEvent.ReplayLevel.MASK_USER_INPUT
}

private fun ViewEvent.Plan.toRum() = when (this) {
    ViewEvent.Plan.PLAN_1 -> ViewUpdateEvent.Plan.PLAN_1
    ViewEvent.Plan.PLAN_2 -> ViewUpdateEvent.Plan.PLAN_2
}

private fun ViewEvent.SessionPrecondition.toRum() = when (this) {
    ViewEvent.SessionPrecondition.USER_APP_LAUNCH -> ViewUpdateEvent.SessionPrecondition.USER_APP_LAUNCH
    ViewEvent.SessionPrecondition.INACTIVITY_TIMEOUT -> ViewUpdateEvent.SessionPrecondition.INACTIVITY_TIMEOUT
    ViewEvent.SessionPrecondition.MAX_DURATION -> ViewUpdateEvent.SessionPrecondition.MAX_DURATION
    ViewEvent.SessionPrecondition.BACKGROUND_LAUNCH -> ViewUpdateEvent.SessionPrecondition.BACKGROUND_LAUNCH
    ViewEvent.SessionPrecondition.PREWARM -> ViewUpdateEvent.SessionPrecondition.PREWARM
    ViewEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION -> ViewUpdateEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION
    ViewEvent.SessionPrecondition.EXPLICIT_STOP -> ViewUpdateEvent.SessionPrecondition.EXPLICIT_STOP
}

// endregion
