/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.model.RumViewUpdateEvent
import com.datadog.android.rum.model.ViewEvent

internal class RumViewStateMapper {

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

    // region Private - top-level nested types (ViewEvent)

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

    // region Private - top-level nested types (RumViewUpdateEvent)

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

    // region Private - deeper nested types (RumViewUpdateEvent)

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

    // region Private - enum mappings (RumViewUpdateEvent)

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

    // region Private - enum mappings (ViewEvent)

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