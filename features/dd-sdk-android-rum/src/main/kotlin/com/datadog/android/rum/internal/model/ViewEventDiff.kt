/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.model

import com.datadog.android.internal.utils.computeDiffIfChanged
import com.datadog.android.internal.utils.computeDiffRequired
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.model.ViewUpdateEvent

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
            source = diffEquals(ViewEvent::source)?.toViewUpdate(),
            usr = diffEquals(ViewEvent::usr)?.toViewUpdate(),
            account = diffEquals(ViewEvent::account)?.toViewUpdate(),
            connectivity = diffEquals(ViewEvent::connectivity)?.toViewUpdate(),
            display = diffEquals(ViewEvent::display)?.toViewUpdate(),
            synthetics = diffEquals(ViewEvent::synthetics)?.let {
                ViewUpdateEvent.Synthetics(testId = it.testId, resultId = it.resultId, injected = it.injected)
            },
            ciTest = diffEquals(ViewEvent::ciTest)?.let {
                ViewUpdateEvent.CiTest(testExecutionId = it.testExecutionId)
            },
            os = diffEquals(ViewEvent::os)?.toViewUpdate(),
            device = diffEquals(ViewEvent::device)?.toViewUpdate(),
            // context = custom attributes (additionalProperties: true) → REPLACE semantics per spec.
            // The generator reuses ViewUpdateEvent.FeatureFlags for this field because both share
            // the same schema shape (additionalProperties: true map). The naming is misleading but correct.
            context = diffEquals(ViewEvent::context)?.let { ViewUpdateEvent.FeatureFlags(it.additionalProperties) },
            container = diffEquals(ViewEvent::container)?.toViewUpdate(),
            privacy = diffEquals(
                ViewEvent::privacy
            )?.let { ViewUpdateEvent.Privacy(replayLevel = it.replayLevel.toViewUpdate()) }
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
            currentLocale = diffEquals(ViewEvent.Application::currentLocale)
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
            type = diffRequired(ViewEvent.ViewEventSession::type).toViewUpdate(),
            hasReplay = diffEquals(ViewEvent.ViewEventSession::hasReplay),
            isActive = diffEquals(ViewEvent.ViewEventSession::isActive),
            sampledForReplay = diffEquals(ViewEvent.ViewEventSession::sampledForReplay)
        )
    }
}

@Suppress("LongMethod")
private fun diffView(
    old: ViewEvent.ViewEventView,
    new: ViewEvent.ViewEventView
): ViewUpdateEvent.ViewUpdateEventView {
    return computeDiffRequired(old = old, new = new) {
        val slowFrames = diffSlowFrames(old.slowFrames ?: emptyList(), new.slowFrames ?: emptyList())
        val inForegroundPeriods = diffList(ViewEvent.ViewEventView::inForegroundPeriods)

        ViewUpdateEvent.ViewUpdateEventView(
            id = diffRequired(ViewEvent.ViewEventView::id),
            url = diffRequired(ViewEvent.ViewEventView::url),
            referrer = diffEquals(ViewEvent.ViewEventView::referrer),
            name = diffEquals(ViewEvent.ViewEventView::name),
            loadingTime = diffEquals(ViewEvent.ViewEventView::loadingTime),
            networkSettledTime = diffEquals(ViewEvent.ViewEventView::networkSettledTime),
            interactionToNextViewTime = diffEquals(ViewEvent.ViewEventView::interactionToNextViewTime),
            loadingType = diffEquals(ViewEvent.ViewEventView::loadingType)?.toViewUpdate(),
            timeSpent = diffEquals(ViewEvent.ViewEventView::timeSpent),
            firstContentfulPaint = diffEquals(ViewEvent.ViewEventView::firstContentfulPaint),
            largestContentfulPaint = diffEquals(ViewEvent.ViewEventView::largestContentfulPaint),
            largestContentfulPaintTargetSelector = diffEquals(
                ViewEvent.ViewEventView::largestContentfulPaintTargetSelector
            ),
            firstInputDelay = diffEquals(ViewEvent.ViewEventView::firstInputDelay),
            firstInputTime = diffEquals(ViewEvent.ViewEventView::firstInputTime),
            firstInputTargetSelector = diffEquals(ViewEvent.ViewEventView::firstInputTargetSelector),
            interactionToNextPaint = diffEquals(ViewEvent.ViewEventView::interactionToNextPaint),
            interactionToNextPaintTime = diffEquals(ViewEvent.ViewEventView::interactionToNextPaintTime),
            interactionToNextPaintTargetSelector = diffEquals(
                ViewEvent.ViewEventView::interactionToNextPaintTargetSelector
            ),
            cumulativeLayoutShift = diffEquals(ViewEvent.ViewEventView::cumulativeLayoutShift),
            cumulativeLayoutShiftTime = diffEquals(ViewEvent.ViewEventView::cumulativeLayoutShiftTime),
            cumulativeLayoutShiftTargetSelector = diffEquals(
                ViewEvent.ViewEventView::cumulativeLayoutShiftTargetSelector
            ),
            domComplete = diffEquals(ViewEvent.ViewEventView::domComplete),
            domContentLoaded = diffEquals(ViewEvent.ViewEventView::domContentLoaded),
            domInteractive = diffEquals(ViewEvent.ViewEventView::domInteractive),
            loadEvent = diffEquals(ViewEvent.ViewEventView::loadEvent),
            firstByte = diffEquals(ViewEvent.ViewEventView::firstByte),
            customTimings = diffEquals(ViewEvent.ViewEventView::customTimings)?.let {
                ViewUpdateEvent.CustomTimings(it.additionalProperties)
            },
            // TODO RUM-14814: is_active must not be sent in VIEW_UPDATE until backend fix is complete
            // (backend cannot distinguish absent false from explicit false — risks corrupting view state)
            // isActive = diffEquals(ViewEvent.ViewEventView::isActive),
            isSlowRendered = diffEquals(ViewEvent.ViewEventView::isSlowRendered),
            action = diffEquals(ViewEvent.ViewEventView::action)?.let { ViewUpdateEvent.Action(it.count) },
            error = diffEquals(ViewEvent.ViewEventView::error)?.let { ViewUpdateEvent.Error(it.count) },
            crash = diffEquals(ViewEvent.ViewEventView::crash)?.let { ViewUpdateEvent.Crash(it.count) },
            longTask = diffEquals(ViewEvent.ViewEventView::longTask)?.let { ViewUpdateEvent.LongTask(it.count) },
            frozenFrame = diffEquals(
                ViewEvent.ViewEventView::frozenFrame
            )?.let { ViewUpdateEvent.FrozenFrame(it.count) },
            slowFrames = slowFrames,
            resource = diffEquals(ViewEvent.ViewEventView::resource)?.let { ViewUpdateEvent.Resource(it.count) },
            frustration = diffEquals(
                ViewEvent.ViewEventView::frustration
            )?.let { ViewUpdateEvent.Frustration(it.count) },
            inForegroundPeriods = inForegroundPeriods?.map {
                ViewUpdateEvent.InForegroundPeriod(start = it.start, duration = it.duration)
            },
            memoryAverage = diffEquals(ViewEvent.ViewEventView::memoryAverage),
            memoryMax = diffEquals(ViewEvent.ViewEventView::memoryMax),
            cpuTicksCount = diffEquals(ViewEvent.ViewEventView::cpuTicksCount),
            cpuTicksPerSecond = diffEquals(ViewEvent.ViewEventView::cpuTicksPerSecond),
            refreshRateAverage = diffEquals(ViewEvent.ViewEventView::refreshRateAverage),
            refreshRateMin = diffEquals(ViewEvent.ViewEventView::refreshRateMin),
            slowFramesRate = diffEquals(ViewEvent.ViewEventView::slowFramesRate),
            freezeRate = diffEquals(ViewEvent.ViewEventView::freezeRate),
            flutterBuildTime = diffEquals(ViewEvent.ViewEventView::flutterBuildTime)?.toViewUpdate(),
            flutterRasterTime = diffEquals(ViewEvent.ViewEventView::flutterRasterTime)?.toViewUpdate(),
            jsRefreshRate = diffEquals(ViewEvent.ViewEventView::jsRefreshRate)?.toViewUpdate(),
            performance = diffMerge({ performance ?: ViewEvent.Performance() }, ::diffPerformance),
            accessibility = diffMerge(ViewEvent.ViewEventView::accessibility) { oldAccessibility, newAccessibility ->
                when {
                    newAccessibility == null -> null
                    oldAccessibility == null -> diffAccessibility(ViewEvent.Accessibility(), newAccessibility)
                    else -> diffAccessibility(oldAccessibility, newAccessibility)
                }
            }
        )
    }
}

private fun diffDd(old: ViewEvent.Dd, new: ViewEvent.Dd): ViewUpdateEvent.Dd {
    return computeDiffRequired(old = old, new = new) {
        ViewUpdateEvent.Dd(
            documentVersion = diffRequired(ViewEvent.Dd::documentVersion),
            session = diffEquals(ViewEvent.Dd::session)?.let {
                ViewUpdateEvent.DdSession(
                    plan = it.plan?.toViewUpdate(),
                    sessionPrecondition = it.sessionPrecondition?.toViewUpdate()
                )
            },
            configuration = diffEquals(ViewEvent.Dd::configuration)?.let {
                ViewUpdateEvent.Configuration(
                    sessionSampleRate = it.sessionSampleRate,
                    sessionReplaySampleRate = it.sessionReplaySampleRate,
                    profilingSampleRate = it.profilingSampleRate,
                    traceSampleRate = it.traceSampleRate
                )
            },
            browserSdkVersion = diffEquals(ViewEvent.Dd::browserSdkVersion),
            sdkName = diffEquals(ViewEvent.Dd::sdkName)
        )
    }
}

// endregion

// region Diff helpers — nullable merged sub-objects

private fun diffSlowFrames(
    old: List<ViewEvent.SlowFrame>,
    new: List<ViewEvent.SlowFrame>
): List<ViewUpdateEvent.SlowFrame>? {
    // diffList (drop-by-size) breaks when the backing EvictingQueue evicts the head at capacity:
    // both lists have the same size but the content has shifted. Using the last known start
    // timestamp as an anchor ensures only genuinely new frames are returned regardless of eviction.
    val lastKnownStart = old.lastOrNull()?.start
    val newFrames = if (lastKnownStart == null) new else new.filter { it.start > lastKnownStart }
    return newFrames.ifEmpty { null }
        ?.map { ViewUpdateEvent.SlowFrame(start = it.start, duration = it.duration) }
}

private fun diffFeatureFlags(old: ViewEvent.Context, new: ViewEvent.Context): ViewUpdateEvent.FeatureFlags? {
    return computeDiffIfChanged(old = old, new = new) {
        ViewUpdateEvent.FeatureFlags(
            diffMap(ViewEvent.Context::additionalProperties).toMutableMap()
        )
    }
}

private fun diffPerformance(old: ViewEvent.Performance, new: ViewEvent.Performance): ViewUpdateEvent.Performance? {
    return computeDiffIfChanged(old = old, new = new) {
        ViewUpdateEvent.Performance(
            cls = diffEquals(ViewEvent.Performance::cls)?.toViewUpdate(),
            fcp = diffEquals(ViewEvent.Performance::fcp)?.let { ViewUpdateEvent.Fcp(timestamp = it.timestamp) },
            fid = diffEquals(ViewEvent.Performance::fid)?.let {
                ViewUpdateEvent.Fid(
                    duration = it.duration,
                    timestamp = it.timestamp,
                    targetSelector = it.targetSelector
                )
            },
            inp = diffEquals(ViewEvent.Performance::inp)?.toViewUpdate(),
            lcp = diffEquals(ViewEvent.Performance::lcp)?.toViewUpdate(),
            fbc = diffEquals(ViewEvent.Performance::fbc)?.let { ViewUpdateEvent.Fbc(timestamp = it.timestamp) }
        )
    }
}

@Suppress("LongMethod")
private fun diffAccessibility(
    old: ViewEvent.Accessibility,
    new: ViewEvent.Accessibility
): ViewUpdateEvent.Accessibility? {
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
            rtlEnabled = diffEquals(ViewEvent.Accessibility::rtlEnabled)
        )
    }
}

// endregion
