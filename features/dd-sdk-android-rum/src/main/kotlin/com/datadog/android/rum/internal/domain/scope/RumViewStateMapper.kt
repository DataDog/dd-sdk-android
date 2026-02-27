/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.model.ViewEvent

internal object RumViewStateMapper {

    fun mapToViewEvent(state: RumViewState): ViewEvent {
        return ViewEvent(
            date = state.date,
            application = state.application.toViewEvent(),
            service = state.service,
            version = state.version,
            buildVersion = state.buildVersion,
            buildId = state.buildId,
            ddtags = state.ddtags,
            session = state.session.toViewEvent(),
            source = state.source?.toViewEvent(),
            view = state.view.toViewEvent(),
            usr = state.usr?.toViewEvent(),
            account = state.account?.toViewEvent(),
            connectivity = state.connectivity?.toViewEvent(),
            display = state.display?.toViewEvent(),
            synthetics = state.synthetics?.toViewEvent(),
            ciTest = state.ciTest?.toViewEvent(),
            os = state.os?.toViewEvent(),
            device = state.device?.toViewEvent(),
            dd = state.dd.toViewEvent(),
            context = state.context?.toViewEvent(),
            container = state.container?.toViewEvent(),
            featureFlags = state.featureFlags?.toViewEvent(),
            privacy = state.privacy?.toViewEvent()
        )
    }

    // region Private - top-level nested types

    private fun RumViewState.Application.toViewEvent() = ViewEvent.Application(
        id = id,
        currentLocale = currentLocale
    )

    private fun RumViewState.ViewEventSession.toViewEvent() = ViewEvent.ViewEventSession(
        id = id,
        type = type.toViewEvent(),
        hasReplay = hasReplay,
        isActive = isActive,
        sampledForReplay = sampledForReplay
    )

    private fun RumViewState.ViewEventView.toViewEvent() = ViewEvent.ViewEventView(
        id = id,
        referrer = referrer,
        url = url,
        name = name,
        loadingTime = loadingTime,
        networkSettledTime = networkSettledTime,
        interactionToNextViewTime = interactionToNextViewTime,
        loadingType = loadingType?.toViewEvent(),
        timeSpent = timeSpent,
        firstContentfulPaint = firstContentfulPaint,
        largestContentfulPaint = largestContentfulPaint,
        largestContentfulPaintTargetSelector = largestContentfulPaintTargetSelector,
        firstInputDelay = firstInputDelay,
        firstInputTime = firstInputTime,
        firstInputTargetSelector = firstInputTargetSelector,
        interactionToNextPaint = interactionToNextPaint,
        interactionToNextPaintTime = interactionToNextPaintTime,
        interactionToNextPaintTargetSelector = interactionToNextPaintTargetSelector,
        cumulativeLayoutShift = cumulativeLayoutShift,
        cumulativeLayoutShiftTime = cumulativeLayoutShiftTime,
        cumulativeLayoutShiftTargetSelector = cumulativeLayoutShiftTargetSelector,
        domComplete = domComplete,
        domContentLoaded = domContentLoaded,
        domInteractive = domInteractive,
        loadEvent = loadEvent,
        firstByte = firstByte,
        customTimings = customTimings?.toViewEvent(),
        isActive = isActive,
        isSlowRendered = isSlowRendered,
        action = ViewEvent.Action(action.count),
        error = ViewEvent.Error(error.count),
        crash = crash?.let { ViewEvent.Crash(it.count) },
        longTask = longTask?.let { ViewEvent.LongTask(it.count) },
        frozenFrame = frozenFrame?.let { ViewEvent.FrozenFrame(it.count) },
        slowFrames = slowFrames?.map { it.toViewEvent() },
        resource = ViewEvent.Resource(resource.count),
        frustration = frustration?.let { ViewEvent.Frustration(it.count) },
        inForegroundPeriods = inForegroundPeriods?.map { it.toViewEvent() },
        memoryAverage = memoryAverage,
        memoryMax = memoryMax,
        cpuTicksCount = cpuTicksCount,
        cpuTicksPerSecond = cpuTicksPerSecond,
        refreshRateAverage = refreshRateAverage,
        refreshRateMin = refreshRateMin,
        slowFramesRate = slowFramesRate,
        freezeRate = freezeRate,
        flutterBuildTime = flutterBuildTime?.toViewEvent(),
        flutterRasterTime = flutterRasterTime?.toViewEvent(),
        jsRefreshRate = jsRefreshRate?.toViewEvent(),
        performance = performance?.toViewEvent(),
        accessibility = accessibility?.toViewEvent()
    )

    private fun RumViewState.Usr.toViewEvent() = ViewEvent.Usr(
        id = id,
        name = name,
        email = email,
        anonymousId = anonymousId,
        additionalProperties = additionalProperties
    )

    private fun RumViewState.Account.toViewEvent() = ViewEvent.Account(
        id = id,
        name = name,
        additionalProperties = additionalProperties
    )

    private fun RumViewState.Connectivity.toViewEvent() = ViewEvent.Connectivity(
        status = status.toViewEvent(),
        interfaces = interfaces?.map { it.toViewEvent() },
        effectiveType = effectiveType?.toViewEvent(),
        cellular = cellular?.toViewEvent()
    )

    private fun RumViewState.Display.toViewEvent() = ViewEvent.Display(
        viewport = viewport?.toViewEvent(),
        scroll = scroll?.toViewEvent()
    )

    private fun RumViewState.Synthetics.toViewEvent() = ViewEvent.Synthetics(
        testId = testId,
        resultId = resultId,
        injected = injected
    )

    private fun RumViewState.CiTest.toViewEvent() = ViewEvent.CiTest(
        testExecutionId = testExecutionId
    )

    private fun RumViewState.Os.toViewEvent() = ViewEvent.Os(
        name = name,
        version = version,
        build = build,
        versionMajor = versionMajor
    )

    private fun RumViewState.Device.toViewEvent() = ViewEvent.Device(
        type = type?.toViewEvent(),
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

    private fun RumViewState.Dd.toViewEvent() = ViewEvent.Dd(
        session = session?.toViewEvent(),
        configuration = configuration?.toViewEvent(),
        browserSdkVersion = browserSdkVersion,
        sdkName = sdkName,
        documentVersion = documentVersion,
        pageStates = pageStates?.map { it.toViewEvent() },
        replayStats = replayStats?.toViewEvent(),
        cls = cls?.toViewEvent(),
        profiling = profiling?.toViewEvent()
    )

    private fun RumViewState.Context.toViewEvent() = ViewEvent.Context(
        additionalProperties = additionalProperties
    )

    private fun RumViewState.Container.toViewEvent() = ViewEvent.Container(
        view = ViewEvent.ContainerView(view.id),
        source = source.toViewEvent()
    )

    private fun RumViewState.Privacy.toViewEvent() = ViewEvent.Privacy(
        replayLevel = replayLevel.toViewEvent()
    )

    // endregion

    // region Private - deeper nested types

    private fun RumViewState.CustomTimings.toViewEvent() = ViewEvent.CustomTimings(
        additionalProperties = additionalProperties
    )

    private fun RumViewState.SlowFrame.toViewEvent() = ViewEvent.SlowFrame(
        start = start,
        duration = duration
    )

    private fun RumViewState.InForegroundPeriod.toViewEvent() = ViewEvent.InForegroundPeriod(
        start = start,
        duration = duration
    )

    private fun RumViewState.FlutterBuildTime.toViewEvent() = ViewEvent.FlutterBuildTime(
        min = min,
        max = max,
        average = average,
        metricMax = metricMax
    )

    private fun RumViewState.Performance.toViewEvent() = ViewEvent.Performance(
        cls = cls?.toViewEvent(),
        fcp = fcp?.let { ViewEvent.Fcp(it.timestamp) },
        fid = fid?.toViewEvent(),
        inp = inp?.toViewEvent(),
        lcp = lcp?.toViewEvent(),
        fbc = fbc?.let { ViewEvent.Fbc(it.timestamp) }
    )

    private fun RumViewState.PerformanceCls.toViewEvent() = ViewEvent.PerformanceCls(
        score = score,
        timestamp = timestamp,
        targetSelector = targetSelector,
        previousRect = previousRect?.toViewEvent(),
        currentRect = currentRect?.toViewEvent()
    )

    private fun RumViewState.PreviousRect.toViewEvent() = ViewEvent.PreviousRect(
        x = x,
        y = y,
        width = width,
        height = height
    )

    private fun RumViewState.Fid.toViewEvent() = ViewEvent.Fid(
        duration = duration,
        timestamp = timestamp,
        targetSelector = targetSelector
    )

    private fun RumViewState.Inp.toViewEvent() = ViewEvent.Inp(
        duration = duration,
        timestamp = timestamp,
        targetSelector = targetSelector,
        subParts = subParts?.let {
            ViewEvent.InpSubParts(
                inputDelay = it.inputDelay,
                processingTime = it.processingTime,
                presentationDelay = it.presentationDelay
            )
        }
    )

    private fun RumViewState.Lcp.toViewEvent() = ViewEvent.Lcp(
        timestamp = timestamp,
        targetSelector = targetSelector,
        resourceUrl = resourceUrl,
        subParts = subParts?.let {
            ViewEvent.LcpSubParts(
                loadDelay = it.loadDelay,
                loadTime = it.loadTime,
                renderDelay = it.renderDelay
            )
        }
    )

    private fun RumViewState.Accessibility.toViewEvent() = ViewEvent.Accessibility(
        textSize = textSize,
        screenReaderEnabled = screenReaderEnabled,
        boldTextEnabled = boldTextEnabled,
        reduceTransparencyEnabled = reduceTransparencyEnabled,
        reduceMotionEnabled = reduceMotionEnabled,
        buttonShapesEnabled = buttonShapesEnabled,
        invertColorsEnabled = invertColorsEnabled,
        increaseContrastEnabled = increaseContrastEnabled,
        assistiveSwitchEnabled = assistiveSwitchEnabled,
        assistiveTouchEnabled = assistiveTouchEnabled,
        videoAutoplayEnabled = videoAutoplayEnabled,
        closedCaptioningEnabled = closedCaptioningEnabled,
        monoAudioEnabled = monoAudioEnabled,
        shakeToUndoEnabled = shakeToUndoEnabled,
        reducedAnimationsEnabled = reducedAnimationsEnabled,
        shouldDifferentiateWithoutColor = shouldDifferentiateWithoutColor,
        grayscaleEnabled = grayscaleEnabled,
        singleAppModeEnabled = singleAppModeEnabled,
        onOffSwitchLabelsEnabled = onOffSwitchLabelsEnabled,
        speakScreenEnabled = speakScreenEnabled,
        speakSelectionEnabled = speakSelectionEnabled,
        rtlEnabled = rtlEnabled
    )

    private fun RumViewState.Cellular.toViewEvent() = ViewEvent.Cellular(
        technology = technology,
        carrierName = carrierName
    )

    private fun RumViewState.Viewport.toViewEvent() = ViewEvent.Viewport(
        width = width,
        height = height
    )

    private fun RumViewState.Scroll.toViewEvent() = ViewEvent.Scroll(
        maxDepth = maxDepth,
        maxDepthScrollTop = maxDepthScrollTop,
        maxScrollHeight = maxScrollHeight,
        maxScrollHeightTime = maxScrollHeightTime
    )

    private fun RumViewState.DdSession.toViewEvent() = ViewEvent.DdSession(
        plan = plan?.toViewEvent(),
        sessionPrecondition = sessionPrecondition?.toViewEvent()
    )

    private fun RumViewState.Configuration.toViewEvent() = ViewEvent.Configuration(
        sessionSampleRate = sessionSampleRate,
        sessionReplaySampleRate = sessionReplaySampleRate,
        profilingSampleRate = profilingSampleRate,
        traceSampleRate = traceSampleRate,
        startSessionReplayRecordingManually = startSessionReplayRecordingManually
    )

    private fun RumViewState.PageState.toViewEvent() = ViewEvent.PageState(
        state = state.toViewEvent(),
        start = start
    )

    private fun RumViewState.ReplayStats.toViewEvent() = ViewEvent.ReplayStats(
        recordsCount = recordsCount,
        segmentsCount = segmentsCount,
        segmentsTotalRawSize = segmentsTotalRawSize
    )

    private fun RumViewState.DdCls.toViewEvent() = ViewEvent.DdCls(
        devicePixelRatio = devicePixelRatio
    )

    private fun RumViewState.Profiling.toViewEvent() = ViewEvent.Profiling(
        status = status?.toViewEvent(),
        errorReason = errorReason?.toViewEvent()
    )

    // endregion

    // region Private - enum mappings

    private fun RumViewState.ViewEventSessionType.toViewEvent() = when (this) {
        RumViewState.ViewEventSessionType.USER -> ViewEvent.ViewEventSessionType.USER
        RumViewState.ViewEventSessionType.SYNTHETICS -> ViewEvent.ViewEventSessionType.SYNTHETICS
        RumViewState.ViewEventSessionType.CI_TEST -> ViewEvent.ViewEventSessionType.CI_TEST
    }

    private fun RumViewState.ViewEventSource.toViewEvent() = when (this) {
        RumViewState.ViewEventSource.ANDROID -> ViewEvent.ViewEventSource.ANDROID
        RumViewState.ViewEventSource.IOS -> ViewEvent.ViewEventSource.IOS
        RumViewState.ViewEventSource.BROWSER -> ViewEvent.ViewEventSource.BROWSER
        RumViewState.ViewEventSource.FLUTTER -> ViewEvent.ViewEventSource.FLUTTER
        RumViewState.ViewEventSource.REACT_NATIVE -> ViewEvent.ViewEventSource.REACT_NATIVE
        RumViewState.ViewEventSource.ROKU -> ViewEvent.ViewEventSource.ROKU
        RumViewState.ViewEventSource.UNITY -> ViewEvent.ViewEventSource.UNITY
        RumViewState.ViewEventSource.KOTLIN_MULTIPLATFORM -> ViewEvent.ViewEventSource.KOTLIN_MULTIPLATFORM
        RumViewState.ViewEventSource.ELECTRON -> ViewEvent.ViewEventSource.ELECTRON
    }

    private fun RumViewState.LoadingType.toViewEvent() = when (this) {
        RumViewState.LoadingType.INITIAL_LOAD -> ViewEvent.LoadingType.INITIAL_LOAD
        RumViewState.LoadingType.ROUTE_CHANGE -> ViewEvent.LoadingType.ROUTE_CHANGE
        RumViewState.LoadingType.ACTIVITY_DISPLAY -> ViewEvent.LoadingType.ACTIVITY_DISPLAY
        RumViewState.LoadingType.ACTIVITY_REDISPLAY -> ViewEvent.LoadingType.ACTIVITY_REDISPLAY
        RumViewState.LoadingType.FRAGMENT_DISPLAY -> ViewEvent.LoadingType.FRAGMENT_DISPLAY
        RumViewState.LoadingType.FRAGMENT_REDISPLAY -> ViewEvent.LoadingType.FRAGMENT_REDISPLAY
        RumViewState.LoadingType.VIEW_CONTROLLER_DISPLAY -> ViewEvent.LoadingType.VIEW_CONTROLLER_DISPLAY
        RumViewState.LoadingType.VIEW_CONTROLLER_REDISPLAY -> ViewEvent.LoadingType.VIEW_CONTROLLER_REDISPLAY
    }

    private fun RumViewState.ConnectivityStatus.toViewEvent() = when (this) {
        RumViewState.ConnectivityStatus.CONNECTED -> ViewEvent.ConnectivityStatus.CONNECTED
        RumViewState.ConnectivityStatus.NOT_CONNECTED -> ViewEvent.ConnectivityStatus.NOT_CONNECTED
        RumViewState.ConnectivityStatus.MAYBE -> ViewEvent.ConnectivityStatus.MAYBE
    }

    private fun RumViewState.Interface.toViewEvent() = when (this) {
        RumViewState.Interface.BLUETOOTH -> ViewEvent.Interface.BLUETOOTH
        RumViewState.Interface.CELLULAR -> ViewEvent.Interface.CELLULAR
        RumViewState.Interface.ETHERNET -> ViewEvent.Interface.ETHERNET
        RumViewState.Interface.WIFI -> ViewEvent.Interface.WIFI
        RumViewState.Interface.WIMAX -> ViewEvent.Interface.WIMAX
        RumViewState.Interface.MIXED -> ViewEvent.Interface.MIXED
        RumViewState.Interface.OTHER -> ViewEvent.Interface.OTHER
        RumViewState.Interface.UNKNOWN -> ViewEvent.Interface.UNKNOWN
        RumViewState.Interface.NONE -> ViewEvent.Interface.NONE
    }

    private fun RumViewState.EffectiveType.toViewEvent() = when (this) {
        RumViewState.EffectiveType.SLOW_2G -> ViewEvent.EffectiveType.SLOW_2G
        RumViewState.EffectiveType.`2G` -> ViewEvent.EffectiveType.`2G`
        RumViewState.EffectiveType.`3G` -> ViewEvent.EffectiveType.`3G`
        RumViewState.EffectiveType.`4G` -> ViewEvent.EffectiveType.`4G`
    }

    private fun RumViewState.DeviceType.toViewEvent() = when (this) {
        RumViewState.DeviceType.MOBILE -> ViewEvent.DeviceType.MOBILE
        RumViewState.DeviceType.DESKTOP -> ViewEvent.DeviceType.DESKTOP
        RumViewState.DeviceType.TABLET -> ViewEvent.DeviceType.TABLET
        RumViewState.DeviceType.TV -> ViewEvent.DeviceType.TV
        RumViewState.DeviceType.GAMING_CONSOLE -> ViewEvent.DeviceType.GAMING_CONSOLE
        RumViewState.DeviceType.BOT -> ViewEvent.DeviceType.BOT
        RumViewState.DeviceType.OTHER -> ViewEvent.DeviceType.OTHER
    }

    private fun RumViewState.ReplayLevel.toViewEvent() = when (this) {
        RumViewState.ReplayLevel.ALLOW -> ViewEvent.ReplayLevel.ALLOW
        RumViewState.ReplayLevel.MASK -> ViewEvent.ReplayLevel.MASK
        RumViewState.ReplayLevel.MASK_USER_INPUT -> ViewEvent.ReplayLevel.MASK_USER_INPUT
    }

    private fun RumViewState.Plan.toViewEvent() = when (this) {
        RumViewState.Plan.PLAN_1 -> ViewEvent.Plan.PLAN_1
        RumViewState.Plan.PLAN_2 -> ViewEvent.Plan.PLAN_2
    }

    private fun RumViewState.SessionPrecondition.toViewEvent() = when (this) {
        RumViewState.SessionPrecondition.USER_APP_LAUNCH -> ViewEvent.SessionPrecondition.USER_APP_LAUNCH
        RumViewState.SessionPrecondition.INACTIVITY_TIMEOUT -> ViewEvent.SessionPrecondition.INACTIVITY_TIMEOUT
        RumViewState.SessionPrecondition.MAX_DURATION -> ViewEvent.SessionPrecondition.MAX_DURATION
        RumViewState.SessionPrecondition.BACKGROUND_LAUNCH -> ViewEvent.SessionPrecondition.BACKGROUND_LAUNCH
        RumViewState.SessionPrecondition.PREWARM -> ViewEvent.SessionPrecondition.PREWARM
        RumViewState.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION -> ViewEvent.SessionPrecondition.FROM_NON_INTERACTIVE_SESSION
        RumViewState.SessionPrecondition.EXPLICIT_STOP -> ViewEvent.SessionPrecondition.EXPLICIT_STOP
    }

    private fun RumViewState.State.toViewEvent() = when (this) {
        RumViewState.State.ACTIVE -> ViewEvent.State.ACTIVE
        RumViewState.State.PASSIVE -> ViewEvent.State.PASSIVE
        RumViewState.State.HIDDEN -> ViewEvent.State.HIDDEN
        RumViewState.State.FROZEN -> ViewEvent.State.FROZEN
        RumViewState.State.TERMINATED -> ViewEvent.State.TERMINATED
    }

    private fun RumViewState.ProfilingStatus.toViewEvent() = when (this) {
        RumViewState.ProfilingStatus.STARTING -> ViewEvent.ProfilingStatus.STARTING
        RumViewState.ProfilingStatus.RUNNING -> ViewEvent.ProfilingStatus.RUNNING
        RumViewState.ProfilingStatus.STOPPED -> ViewEvent.ProfilingStatus.STOPPED
        RumViewState.ProfilingStatus.ERROR -> ViewEvent.ProfilingStatus.ERROR
    }

    private fun RumViewState.ErrorReason.toViewEvent() = when (this) {
        RumViewState.ErrorReason.NOT_SUPPORTED_BY_BROWSER -> ViewEvent.ErrorReason.NOT_SUPPORTED_BY_BROWSER
        RumViewState.ErrorReason.FAILED_TO_LAZY_LOAD -> ViewEvent.ErrorReason.FAILED_TO_LAZY_LOAD
        RumViewState.ErrorReason.MISSING_DOCUMENT_POLICY_HEADER -> ViewEvent.ErrorReason.MISSING_DOCUMENT_POLICY_HEADER
        RumViewState.ErrorReason.UNEXPECTED_EXCEPTION -> ViewEvent.ErrorReason.UNEXPECTED_EXCEPTION
    }

    // endregion
}