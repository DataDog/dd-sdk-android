/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.heatmaps.NativeHeatmapActionData
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.operations.FailureReason
import com.datadog.tools.unit.forge.aThrowable
import com.datadog.tools.unit.forge.exhaustiveAttributes
import fr.xgouchet.elmyr.Forge
import java.net.URL
import java.util.UUID
import kotlin.reflect.KClass

internal fun Forge.interactiveRumRawEvent(): RumRawEvent {
    return anElementFrom(
        startViewEvent(),
        startActionEvent()
    )
}

internal fun Forge.startViewEvent(eventTime: Time = getForgery()): RumRawEvent.StartView {
    return RumRawEvent.StartView(
        key = getForgery(),
        attributes = exhaustiveAttributes(),
        eventTime = eventTime
    )
}

internal fun Forge.stopViewEvent(): RumRawEvent.StopView {
    return RumRawEvent.StopView(
        key = getForgery(),
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.startActionEvent(
    continuous: Boolean? = null,
    eventTime: Time = getForgery(),
    nativeHeatmapActionData: NativeHeatmapActionData? = null
): RumRawEvent.StartAction {
    return RumRawEvent.StartAction(
        type = aValueFrom(RumActionType::class.java),
        name = anAlphabeticalString(),
        waitForStop = continuous ?: aBool(),
        nativeHeatmapActionData = nativeHeatmapActionData,
        eventTime = eventTime,
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopActionEvent(): RumRawEvent.StopAction {
    return RumRawEvent.StopAction(
        type = aValueFrom(RumActionType::class.java),
        name = anAlphabeticalString(),
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.startResourceEvent(): RumRawEvent.StartResource {
    return RumRawEvent.StartResource(
        key = anAlphabeticalString(),
        url = getForgery<URL>().toString(),
        method = aValueFrom(RumResourceMethod::class.java),
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.stopResourceEvent(): RumRawEvent.StopResource {
    return RumRawEvent.StopResource(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        size = aNullable { aPositiveLong() },
        kind = aValueFrom(RumResourceKind::class.java),
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.startOperationEvent(): RumRawEvent.StartOperation {
    return RumRawEvent.StartOperation(
        name = anAlphabeticalString(),
        operationKey = aNullable { anAlphabeticalString() },
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.stopOperationEvent(): RumRawEvent.StopOperation {
    return RumRawEvent.StopOperation(
        name = anAlphabeticalString(),
        operationKey = aNullable { anAlphabeticalString() },
        attributes = exhaustiveAttributes(),
        eventTime = getForgery(),
        failureReason = aNullable { aValueFrom(FailureReason::class.java) }
    )
}

internal fun Forge.stopResourceWithErrorEvent(): RumRawEvent.StopResourceWithError {
    return RumRawEvent.StopResourceWithError(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        source = aValueFrom(RumErrorSource::class.java),
        message = anAlphabeticalString(),
        throwable = aThrowable(),
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.stopResourceWithStacktraceEvent(): RumRawEvent.StopResourceWithStackTrace {
    return RumRawEvent.StopResourceWithStackTrace(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        source = aValueFrom(RumErrorSource::class.java),
        message = anAlphabeticalString(),
        stackTrace = anAlphabeticalString(),
        errorType = aNullable { anAlphabeticalString() },
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.addErrorEvent(): RumRawEvent.AddError {
    val isFatal = aBool()
    return RumRawEvent.AddError(
        message = anAlphabeticalString(),
        source = aValueFrom(RumErrorSource::class.java),
        stacktrace = null,
        throwable = null,
        isFatal = isFatal,
        threads = if (isFatal) aList { getForgery() } else emptyList(),
        timeSinceAppStartNs = if (isFatal) aPositiveLong() else null,
        attributes = exhaustiveAttributes(),
        eventTime = getForgery()
    )
}

internal fun Forge.addViewLoadingTimeEvent(): RumRawEvent.AddViewLoadingTime {
    return RumRawEvent.AddViewLoadingTime(overwrite = aBool(), eventTime = getForgery())
}

internal fun Forge.addLongTaskEvent(): RumRawEvent.AddLongTask {
    return RumRawEvent.AddLongTask(
        durationNs = aLong(min = 1),
        target = anAlphabeticalString(),
        eventTime = getForgery()
    )
}

internal fun Forge.applicationStartedEvent(): RumRawEvent.ApplicationStarted {
    val time = getForgery<Time>()
    return RumRawEvent.ApplicationStarted(
        eventTime = time,
        applicationStartupNanos = aLong(min = 0L, max = time.nanoTime)
    )
}

internal fun Forge.sdkInitEvent(): RumRawEvent.SdkInit {
    return RumRawEvent.SdkInit(
        isAppInForeground = aBool(),
        eventTime = getForgery()
    )
}

internal fun Forge.updatePerformanceMetricEvent(): RumRawEvent.UpdatePerformanceMetric {
    return RumRawEvent.UpdatePerformanceMetric(
        metric = getForgery(),
        value = aDouble(),
        eventTime = getForgery()
    )
}

internal fun Forge.updateExternalRefreshRateEvent(): RumRawEvent.UpdateExternalRefreshRate {
    return RumRawEvent.UpdateExternalRefreshRate(
        frameTimeSeconds = aDouble(),
        eventTime = getForgery()
    )
}

internal fun Forge.addFeatureFlagEvaluationEvent(): RumRawEvent.AddFeatureFlagEvaluation {
    return RumRawEvent.AddFeatureFlagEvaluation(
        name = anAlphabeticalString(),
        value = anElementFrom(aString(), anInt(), Any()),
        eventTime = getForgery()
    )
}

internal fun Forge.addCustomTimingEvent(): RumRawEvent.AddCustomTiming {
    return RumRawEvent.AddCustomTiming(
        name = anAlphabeticalString(),
        eventTime = getForgery()
    )
}

internal fun Forge.validBackgroundEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            { startActionEvent() },
            { addErrorEvent() },
            { startResourceEvent() },
            { startOperationEvent() },
            { stopOperationEvent() }
        )
    ).invoke()
}

internal fun Forge.invalidBackgroundEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            { addLongTaskEvent() },
            { stopActionEvent() },
            { stopResourceEvent() },
            { stopResourceWithErrorEvent() },
            { stopResourceWithStacktraceEvent() },
            { addViewLoadingTimeEvent() }
        )
    ).invoke()
}

internal fun Forge.anyRumEvent(excluding: List<KClass<out RumRawEvent>> = listOf()): RumRawEvent {
    fun <T : RumRawEvent> strictSameTypePair(key: KClass<T>, value: () -> T) = key to value
    val allEventsFactories = mapOf(
        strictSameTypePair(RumRawEvent.StartView::class) { startViewEvent() },
        strictSameTypePair(RumRawEvent.StopView::class) { stopViewEvent() },
        strictSameTypePair(RumRawEvent.StartAction::class) { startActionEvent() },
        strictSameTypePair(RumRawEvent.StopAction::class) { stopActionEvent() },
        strictSameTypePair(RumRawEvent.StartResource::class) { startResourceEvent() },
        strictSameTypePair(RumRawEvent.StopResource::class) { stopResourceEvent() },
        strictSameTypePair(RumRawEvent.StopResourceWithError::class) { stopResourceWithErrorEvent() },
        strictSameTypePair(RumRawEvent.StopResourceWithStackTrace::class) { stopResourceWithStacktraceEvent() },
        strictSameTypePair(RumRawEvent.AddError::class) { addErrorEvent() },
        strictSameTypePair(RumRawEvent.AddLongTask::class) { addLongTaskEvent() },
        strictSameTypePair(RumRawEvent.AddFeatureFlagEvaluation::class) { addFeatureFlagEvaluationEvent() },
        strictSameTypePair(RumRawEvent.AddCustomTiming::class) { addCustomTimingEvent() },
        strictSameTypePair(RumRawEvent.UpdatePerformanceMetric::class) { updatePerformanceMetricEvent() },
        strictSameTypePair(RumRawEvent.UpdateExternalRefreshRate::class) { updateExternalRefreshRateEvent() },
        strictSameTypePair(RumRawEvent.AddViewLoadingTime::class) { addViewLoadingTimeEvent() }
    )
    return this.anElementFrom(
        allEventsFactories
            .filter { !excluding.contains(it.key) }
            .values
            .toList()
    ).invoke()
}

internal fun Forge.eventSent(
    viewId: String,
    eventTime: Time = getForgery()
): RumRawEvent {
    return this.anElementFrom(
        listOf(
            RumRawEvent.ActionSent(
                viewId = viewId,
                frustrationCount = aPositiveInt(),
                type = aValueFrom(ActionEvent.ActionEventActionType::class.java),
                eventEndTimestampInNanos = aPositiveLong(),
                eventTime = eventTime
            ),
            RumRawEvent.ErrorSent(
                viewId = viewId,
                resourceId = aNullable { getForgery<UUID>().toString() },
                resourceEndTimestampInNanos = aNullable { aLong() },
                eventTime = eventTime
            ),
            RumRawEvent.LongTaskSent(
                viewId = viewId,
                isFrozenFrame = aBool(),
                eventTime = eventTime
            ),
            RumRawEvent.ResourceSent(
                viewId = viewId,
                resourceId = getForgery<UUID>().toString(),
                resourceEndTimestampInNanos = aPositiveLong(),
                eventTime = eventTime
            )
        )
    )
}
