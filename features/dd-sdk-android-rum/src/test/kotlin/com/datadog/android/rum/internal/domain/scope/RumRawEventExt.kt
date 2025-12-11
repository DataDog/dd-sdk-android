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
import com.datadog.android.rum.featureoperations.FailureReason
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.model.ActionEvent
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

internal fun Forge.startViewEvent(eventTime: Time = Time()): RumRawEvent.StartView {
    return RumRawEvent.StartView(
        key = getForgery(),
        attributes = exhaustiveAttributes(),
        eventTime = eventTime
    )
}

internal fun Forge.stopViewEvent(): RumRawEvent.StopView {
    return RumRawEvent.StopView(
        key = getForgery(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.startActionEvent(continuous: Boolean? = null, eventTime: Time = Time()): RumRawEvent.StartAction {
    return RumRawEvent.StartAction(
        type = aValueFrom(RumActionType::class.java),
        name = anAlphabeticalString(),
        waitForStop = continuous ?: aBool(),
        attributes = exhaustiveAttributes(),
        eventTime = eventTime
    )
}

internal fun Forge.stopActionEvent(): RumRawEvent.StopAction {
    return RumRawEvent.StopAction(
        type = aValueFrom(RumActionType::class.java),
        name = anAlphabeticalString(),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.startResourceEvent(): RumRawEvent.StartResource {
    return RumRawEvent.StartResource(
        key = anAlphabeticalString(),
        url = getForgery<URL>().toString(),
        method = aValueFrom(RumResourceMethod::class.java),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopResourceEvent(): RumRawEvent.StopResource {
    return RumRawEvent.StopResource(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        size = aNullable { aPositiveLong() },
        kind = aValueFrom(RumResourceKind::class.java),
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.startFeatureOperationEvent(): RumRawEvent.StartFeatureOperation {
    return RumRawEvent.StartFeatureOperation(
        name = anAlphabeticalString(),
        operationKey = aNullable { anAlphabeticalString() },
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopFeatureOperationEvent(): RumRawEvent.StopFeatureOperation {
    return RumRawEvent.StopFeatureOperation(
        name = anAlphabeticalString(),
        operationKey = aNullable { anAlphabeticalString() },
        failureReason = aNullable { aValueFrom(FailureReason::class.java) },
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.stopResourceWithErrorEvent(): RumRawEvent.StopResourceWithError {
    return RumRawEvent.StopResourceWithError(
        key = anAlphabeticalString(),
        statusCode = aNullable { aLong(100, 600) },
        source = aValueFrom(RumErrorSource::class.java),
        message = anAlphabeticalString(),
        throwable = aThrowable(),
        attributes = exhaustiveAttributes()
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
        attributes = exhaustiveAttributes()
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
        attributes = exhaustiveAttributes()
    )
}

internal fun Forge.addViewLoadingTimeEvent(): RumRawEvent.AddViewLoadingTime {
    return RumRawEvent.AddViewLoadingTime(overwrite = aBool())
}

internal fun Forge.addLongTaskEvent(): RumRawEvent.AddLongTask {
    return RumRawEvent.AddLongTask(
        durationNs = aLong(min = 1),
        target = anAlphabeticalString()
    )
}

internal fun Forge.applicationStartedEvent(): RumRawEvent.ApplicationStarted {
    val time = Time()
    return RumRawEvent.ApplicationStarted(
        eventTime = time,
        applicationStartupNanos = aLong(min = 0L, max = time.nanoTime)
    )
}

internal fun Forge.sdkInitEvent(): RumRawEvent.SdkInit {
    val time = Time()
    return RumRawEvent.SdkInit(
        isAppInForeground = aBool(),
        eventTime = time
    )
}

internal fun Forge.updatePerformanceMetricEvent(): RumRawEvent.UpdatePerformanceMetric {
    val time = Time()
    return RumRawEvent.UpdatePerformanceMetric(
        metric = getForgery(),
        value = aDouble(),
        eventTime = time
    )
}

internal fun Forge.updateExternalRefreshRateEvent(): RumRawEvent.UpdateExternalRefreshRate {
    val time = Time()
    return RumRawEvent.UpdateExternalRefreshRate(
        frameTimeSeconds = aDouble(),
        eventTime = time
    )
}

internal fun Forge.addFeatureFlagEvaluationEvent(): RumRawEvent.AddFeatureFlagEvaluation {
    val time = Time()
    return RumRawEvent.AddFeatureFlagEvaluation(
        name = anAlphabeticalString(),
        value = anElementFrom(aString(), anInt(), Any()),
        eventTime = time
    )
}

internal fun Forge.addCustomTimingEvent(): RumRawEvent.AddCustomTiming {
    val time = Time()
    return RumRawEvent.AddCustomTiming(
        name = anAlphabeticalString(),
        eventTime = time
    )
}

internal fun Forge.validBackgroundEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            { startActionEvent() },
            { addErrorEvent() },
            { startResourceEvent() },
            { startFeatureOperationEvent() },
            { stopFeatureOperationEvent() }
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
    val allEventsFactories = mapOf<KClass<out RumRawEvent>, () -> RumRawEvent>(
        strictSameTypePair(RumRawEvent.StartView::class, { startViewEvent() }),
        strictSameTypePair(RumRawEvent.StopView::class, { stopViewEvent() }),
        strictSameTypePair(RumRawEvent.StartAction::class, { startActionEvent() }),
        strictSameTypePair(RumRawEvent.StopAction::class, { stopActionEvent() }),
        strictSameTypePair(RumRawEvent.StartResource::class, { startResourceEvent() }),
        strictSameTypePair(RumRawEvent.StopResource::class, { stopResourceEvent() }),
        strictSameTypePair(RumRawEvent.StopResourceWithError::class, { stopResourceWithErrorEvent() }),
        strictSameTypePair(RumRawEvent.StopResourceWithStackTrace::class, { stopResourceWithStacktraceEvent() }),
        strictSameTypePair(RumRawEvent.AddError::class, { addErrorEvent() }),
        strictSameTypePair(RumRawEvent.AddLongTask::class, { addLongTaskEvent() }),
        strictSameTypePair(RumRawEvent.AddFeatureFlagEvaluation::class, { addFeatureFlagEvaluationEvent() }),
        strictSameTypePair(RumRawEvent.AddCustomTiming::class, { addCustomTimingEvent() }),
        strictSameTypePair(RumRawEvent.UpdatePerformanceMetric::class, { updatePerformanceMetricEvent() }),
        strictSameTypePair(RumRawEvent.UpdateExternalRefreshRate::class, { updateExternalRefreshRateEvent() }),
        strictSameTypePair(RumRawEvent.AddViewLoadingTime::class, { addViewLoadingTimeEvent() })
    )
    return this.anElementFrom(
        allEventsFactories
            .filter { !excluding.contains(it.key) }
            .values
            .toList()
    ).invoke()
}

internal fun Forge.invalidAppLaunchEvent(): RumRawEvent {
    return this.anElementFrom(
        listOf(
            stopActionEvent(),
            stopResourceEvent(),
            stopResourceWithErrorEvent(),
            stopResourceWithStacktraceEvent()
        )
    )
}

internal fun Forge.silentOrphanEvent(): RumRawEvent {
    val fakeId = getForgery<UUID>().toString()

    return this.anElementFrom(
        listOf(
            RumRawEvent.ApplicationStarted(Time(), aLong()),
            RumRawEvent.ResetSession(),
            RumRawEvent.KeepAlive(),
            RumRawEvent.StopView(getForgery(), emptyMap()),
            RumRawEvent.ActionSent(
                fakeId,
                aPositiveInt(),
                aValueFrom(ActionEvent.ActionEventActionType::class.java),
                aPositiveLong()
            ),
            RumRawEvent.ErrorSent(fakeId),
            RumRawEvent.LongTaskSent(fakeId),
            RumRawEvent.ResourceSent(fakeId, getForgery<UUID>().toString(), aPositiveLong()),
            RumRawEvent.ActionDropped(fakeId),
            RumRawEvent.ErrorDropped(fakeId),
            RumRawEvent.LongTaskDropped(fakeId),
            RumRawEvent.ResourceDropped(fakeId, getForgery<UUID>().toString())
        )
    )
}

internal fun Forge.eventSent(
    viewId: String,
    eventTime: Time = Time()
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
