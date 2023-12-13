/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.model.ActionEvent
import com.datadog.android.rum.utils.hasUserData
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max

internal class RumActionScope(
    val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    val waitForStop: Boolean,
    eventTime: Time,
    initialType: RumActionType,
    initialName: String,
    initialAttributes: Map<String, Any?>,
    serverTimeOffsetInMs: Long,
    inactivityThresholdMs: Long = ACTION_INACTIVITY_MS,
    maxDurationMs: Long = ACTION_MAX_DURATION_MS,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver(),
    private val trackFrustrations: Boolean,
    internal val sampleRate: Float
) : RumScope {

    private val inactivityThresholdNs = TimeUnit.MILLISECONDS.toNanos(inactivityThresholdMs)
    private val maxDurationNs = TimeUnit.MILLISECONDS.toNanos(maxDurationMs)

    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs
    internal val actionId: String = UUID.randomUUID().toString()
    internal var type: RumActionType = initialType
    internal var name: String = initialName
    private val startedNanos: Long = eventTime.nanoTime
    private var lastInteractionNanos: Long = startedNanos
    private val networkInfo = sdkCore.networkInfo

    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap().apply {
        putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
    }

    private val ongoingResourceKeys = mutableListOf<WeakReference<Any>>()

    internal var resourceCount: Long = 0
    internal var errorCount: Long = 0
    internal var crashCount: Long = 0
    internal var longTaskCount: Long = 0

    private var sent = false
    internal var stopped = false

    // endregion

    @WorkerThread
    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope? {
        val now = event.eventTime.nanoTime
        val isInactive = now - lastInteractionNanos > inactivityThresholdNs
        val isLongDuration = now - startedNanos > maxDurationNs
        ongoingResourceKeys.removeAll { it.get() == null }
        val isOngoing = waitForStop && !stopped
        val shouldStop = isInactive && ongoingResourceKeys.isEmpty() && !isOngoing

        when {
            shouldStop -> sendAction(lastInteractionNanos, writer)
            isLongDuration -> sendAction(now, writer)
            event is RumRawEvent.SendCustomActionNow -> sendAction(lastInteractionNanos, writer)
            event is RumRawEvent.StartView -> onStartView(now, writer)
            event is RumRawEvent.StopView -> onStopView(now, writer)
            event is RumRawEvent.StopAction -> onStopAction(event, now)
            event is RumRawEvent.StartResource -> onStartResource(event, now)
            event is RumRawEvent.StopResource -> onStopResource(event, now)
            event is RumRawEvent.AddError -> onError(event, now, writer)
            event is RumRawEvent.StopResourceWithError -> onResourceError(event.key, now)
            event is RumRawEvent.StopResourceWithStackTrace -> onResourceError(event.key, now)
            event is RumRawEvent.AddLongTask -> onLongTask(now)
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext()
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun onStartView(
        now: Long,
        writer: DataWriter<Any>
    ) {
        // another view starts, complete this action
        ongoingResourceKeys.clear()
        sendAction(now, writer)
    }

    @WorkerThread
    private fun onStopView(
        now: Long,
        writer: DataWriter<Any>
    ) {
        ongoingResourceKeys.clear()
        sendAction(now, writer)
    }

    private fun onStopAction(
        event: RumRawEvent.StopAction,
        now: Long
    ) {
        event.type?.let { type = it }
        event.name?.let { name = it }
        attributes.putAll(event.attributes)
        stopped = true
        lastInteractionNanos = now
    }

    private fun onStartResource(
        event: RumRawEvent.StartResource,
        now: Long
    ) {
        lastInteractionNanos = now
        resourceCount++
        ongoingResourceKeys.add(WeakReference(event.key))
    }

    private fun onStopResource(
        event: RumRawEvent.StopResource,
        now: Long
    ) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == event.key }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
        }
    }

    @WorkerThread
    private fun onError(
        event: RumRawEvent.AddError,
        now: Long,
        writer: DataWriter<Any>
    ) {
        lastInteractionNanos = now
        errorCount++

        if (event.isFatal) {
            crashCount++
            sendAction(now, writer)
        }
    }

    private fun onResourceError(eventKey: String, now: Long) {
        val keyRef = ongoingResourceKeys.firstOrNull { it.get() == eventKey }
        if (keyRef != null) {
            ongoingResourceKeys.remove(keyRef)
            lastInteractionNanos = now
            resourceCount--
            errorCount++
        }
    }

    private fun onLongTask(now: Long) {
        lastInteractionNanos = now
        longTaskCount++
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun sendAction(
        endNanos: Long,
        writer: DataWriter<Any>
    ) {
        if (sent) return

        val actualType = type
        attributes.putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
        val rumContext = getRumContext()

        // make a copy so that closure captures at the state as of now
        // normally not needed, because it should be only single event for this scope, but
        // just in case
        val eventName = name
        val eventErrorCount = errorCount
        val eventCrashCount = crashCount
        val eventLongTaskCount = longTaskCount
        val eventResourceCount = resourceCount

        sdkCore.getFeature(Feature.RUM_FEATURE_NAME)
            ?.withWriteContext { datadogContext, eventBatchWriter ->
                val user = datadogContext.userInfo
                val hasReplay = featuresContextResolver.resolveViewHasReplay(
                    datadogContext,
                    rumContext.viewId.orEmpty()
                )
                val frustrations = mutableListOf<ActionEvent.Type>()
                if (trackFrustrations && eventErrorCount > 0 && actualType == RumActionType.TAP) {
                    frustrations.add(ActionEvent.Type.ERROR_TAP)
                }

                val actionEvent = ActionEvent(
                    date = eventTimestamp,
                    action = ActionEvent.ActionEventAction(
                        type = actualType.toSchemaType(),
                        id = actionId,
                        target = ActionEvent.ActionEventActionTarget(eventName),
                        error = ActionEvent.Error(eventErrorCount),
                        crash = ActionEvent.Crash(eventCrashCount),
                        longTask = ActionEvent.LongTask(eventLongTaskCount),
                        resource = ActionEvent.Resource(eventResourceCount),
                        loadingTime = max(endNanos - startedNanos, 1L),
                        frustration = if (frustrations.isNotEmpty()) {
                            ActionEvent.Frustration(frustrations)
                        } else {
                            null
                        }
                    ),
                    view = ActionEvent.ActionEventView(
                        id = rumContext.viewId.orEmpty(),
                        name = rumContext.viewName,
                        url = rumContext.viewUrl.orEmpty()
                    ),
                    application = ActionEvent.Application(rumContext.applicationId),
                    session = ActionEvent.ActionEventSession(
                        id = rumContext.sessionId,
                        type = ActionEvent.ActionEventSessionType.USER,
                        hasReplay = hasReplay
                    ),
                    source = ActionEvent.ActionEventSource.tryFromSource(
                        datadogContext.source,
                        sdkCore.internalLogger
                    ),
                    usr = if (user.hasUserData()) {
                        ActionEvent.Usr(
                            id = user.id,
                            name = user.name,
                            email = user.email,
                            additionalProperties = user.additionalProperties.toMutableMap()
                        )
                    } else {
                        null
                    },
                    os = ActionEvent.Os(
                        name = datadogContext.deviceInfo.osName,
                        version = datadogContext.deviceInfo.osVersion,
                        versionMajor = datadogContext.deviceInfo.osMajorVersion
                    ),
                    device = ActionEvent.Device(
                        type = datadogContext.deviceInfo.deviceType.toActionSchemaType(),
                        name = datadogContext.deviceInfo.deviceName,
                        model = datadogContext.deviceInfo.deviceModel,
                        brand = datadogContext.deviceInfo.deviceBrand,
                        architecture = datadogContext.deviceInfo.architecture
                    ),
                    context = ActionEvent.Context(additionalProperties = attributes),
                    dd = ActionEvent.Dd(
                        session = ActionEvent.DdSession(plan = ActionEvent.Plan.PLAN_1),
                        configuration = ActionEvent.Configuration(sessionSampleRate = sampleRate)
                    ),
                    connectivity = networkInfo.toActionConnectivity(),
                    service = datadogContext.service,
                    version = datadogContext.version
                )

                @Suppress("ThreadSafety") // called in a worker thread context
                writer.write(eventBatchWriter, actionEvent)
            }

        sent = true
    }

    // endregion

    companion object {
        internal const val ACTION_INACTIVITY_MS = 100L
        internal const val ACTION_MAX_DURATION_MS = 5000L

        @Suppress("LongParameterList")
        fun fromEvent(
            parentScope: RumScope,
            sdkCore: InternalSdkCore,
            event: RumRawEvent.StartAction,
            timestampOffset: Long,
            featuresContextResolver: FeaturesContextResolver,
            trackFrustrations: Boolean,
            sampleRate: Float
        ): RumScope {
            return RumActionScope(
                parentScope,
                sdkCore,
                event.waitForStop,
                event.eventTime,
                event.type,
                event.name,
                event.attributes,
                timestampOffset,
                featuresContextResolver = featuresContextResolver,
                trackFrustrations = trackFrustrations,
                sampleRate = sampleRate
            )
        }
    }
}
