/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import android.util.Log
import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.api.storage.EventType
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.attributes.LocalAttribute
import com.datadog.android.internal.attributes.ViewScopeInstrumentationType
import com.datadog.android.internal.telemetry.InternalTelemetryEvent
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumActionType
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumPerformanceMetric
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.anr.ANRException
import com.datadog.android.rum.internal.domain.InfoProvider
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.accessibility.AccessibilitySnapshotManager
import com.datadog.android.rum.internal.domain.battery.BatteryInfo
import com.datadog.android.rum.internal.domain.display.DisplayInfo
import com.datadog.android.rum.internal.metric.NoValueReason
import com.datadog.android.rum.internal.metric.SessionMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewEndedMetricDispatcher
import com.datadog.android.rum.internal.metric.ViewMetricDispatcher
import com.datadog.android.rum.internal.metric.interactiontonextview.InteractionToNextViewMetricResolver
import com.datadog.android.rum.internal.metric.interactiontonextview.InternalInteractionContext
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.metric.slowframes.SlowFramesListener
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.toError
import com.datadog.android.rum.internal.toLongTask
import com.datadog.android.rum.internal.toView
import com.datadog.android.rum.internal.toVital
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.internal.utils.hasUserData
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.internal.vitals.VitalInfo
import com.datadog.android.rum.internal.vitals.VitalListener
import com.datadog.android.rum.internal.vitals.VitalMonitor
import com.datadog.android.rum.metric.networksettled.InitialResourceIdentifier
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.LongTaskEvent
import com.datadog.android.rum.model.RumVitalOperationStepEvent
import com.datadog.android.rum.model.ViewEvent
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

@Suppress("TooManyFunctions", "LargeClass", "LongParameterList")
internal open class RumViewScope(
    override val parentScope: RumScope,
    private val sdkCore: InternalSdkCore,
    private val sessionEndedMetricDispatcher: SessionMetricDispatcher,
    internal val key: RumScopeKey,
    eventTime: Time,
    private val initialAttributes: Map<String, Any?>,
    private val viewChangedListener: RumViewChangedListener?,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    internal val cpuVitalMonitor: VitalMonitor,
    internal val memoryVitalMonitor: VitalMonitor,
    internal val frameRateVitalMonitor: VitalMonitor,
    private val featuresContextResolver: FeaturesContextResolver = FeaturesContextResolver(),
    internal val type: RumViewType = RumViewType.FOREGROUND,
    private val trackFrustrations: Boolean,
    internal val sampleRate: Float,
    private val interactionToNextViewMetricResolver: InteractionToNextViewMetricResolver,
    private val networkSettledMetricResolver: NetworkSettledMetricResolver,
    private val slowFramesListener: SlowFramesListener?,
    private val viewEndedMetricDispatcher: ViewMetricDispatcher,
    private val rumSessionTypeOverride: RumSessionType?,
    private val accessibilitySnapshotManager: AccessibilitySnapshotManager,
    private val batteryInfoProvider: InfoProvider<BatteryInfo>,
    private val displayInfoProvider: InfoProvider<DisplayInfo>
) : RumScope {

    internal val url = key.url.replace('.', '/')

    internal val viewAttributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()
    private val internalAttributes: MutableMap<String, Any?> = mutableMapOf()
    private var memoizedParentAttributes: Map<String, Any?> = emptyMap()

    private val sessionId: String = parentScope.getRumContext().sessionId
    internal val viewId: String = UUID.randomUUID().toString()

    private val startedNanos: Long = eventTime.nanoTime
    internal var stoppedNanos: Long = eventTime.nanoTime
    internal var viewLoadingTime: Long? = null

    private val serverTimeOffsetInMs = sdkCore.time.serverTimeOffsetMs
    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs

    internal var activeActionScope: RumScope? = null
    internal val activeResourceScopes = mutableMapOf<Any, RumScope>()

    private var resourceCount: Long = 0
    private var actionCount: Long = 0
    private var frustrationCount: Int = 0
    private var errorCount: Long = 0
    private var crashCount: Long = 0
    private var longTaskCount: Long = 0
    private var frozenFrameCount: Long = 0

    // TODO RUM-3792 We have now access to the event write result through the closure,
    // we probably can drop AdvancedRumMonitor#eventSent/eventDropped usage
    internal var pendingResourceCount: Long = 0
    internal var pendingActionCount: Long = 0
    internal var pendingErrorCount: Long = 0
    internal var pendingLongTaskCount: Long = 0
    internal var pendingFrozenFrameCount: Long = 0

    internal var version: Long = 1
    internal val customTimings: MutableMap<String, Long> = mutableMapOf()
    internal val featureFlags: MutableMap<String, Any?> = mutableMapOf()
    internal var hasReplay = false

    internal var stopped: Boolean = false

    // region Vitals Fields

    private var cpuTicks: Double? = null
    internal var cpuVitalListener: VitalListener = object : VitalListener {
        private var initialTickCount: Double = Double.NaN
        override fun onVitalUpdate(info: VitalInfo) {
            // The CPU Ticks will always grow, as it's the total ticks since the app started
            if (initialTickCount.isNaN()) {
                initialTickCount = info.maxValue
            } else {
                cpuTicks = info.maxValue - initialTickCount
            }
        }
    }

    private var lastMemoryInfo: VitalInfo? = null
    internal var memoryVitalListener: VitalListener = object : VitalListener {
        override fun onVitalUpdate(info: VitalInfo) {
            lastMemoryInfo = info
        }
    }

    private var lastFrameRateInfo: VitalInfo? = null
    internal var frameRateVitalListener: VitalListener = object : VitalListener {
        override fun onVitalUpdate(info: VitalInfo) {
            lastFrameRateInfo = info
        }
    }

    private val performanceMetrics: MutableMap<RumPerformanceMetric, VitalInfo> = mutableMapOf()

    private var externalRefreshRateInfo: VitalInfo? = null

    // endregion

    init {
        cpuVitalMonitor.register(cpuVitalListener)
        memoryVitalMonitor.register(memoryVitalListener)
        frameRateVitalMonitor.register(frameRateVitalListener)

        val rumContext = parentScope.getRumContext()
        if (rumContext.syntheticsTestId != null) {
            logSynthetics("_dd.application.id", rumContext.applicationId)
            logSynthetics("_dd.session.id", rumContext.sessionId)
            logSynthetics("_dd.view.id", viewId)
        }
        networkSettledMetricResolver.viewWasCreated(eventTime.nanoTime)
        interactionToNextViewMetricResolver.onViewCreated(viewId, eventTime.nanoTime)
        slowFramesListener?.onViewCreated(viewId, startedNanos)
    }

    // region RumScope

    @WorkerThread
    override fun handleEvent(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ): RumScope? {
        when (event) {
            is RumRawEvent.ResourceSent -> onResourceSent(event, datadogContext, writeScope, writer)
            is RumRawEvent.ActionSent -> onActionSent(event, datadogContext, writeScope, writer)
            is RumRawEvent.ErrorSent -> onErrorSent(event, datadogContext, writeScope, writer)
            is RumRawEvent.LongTaskSent -> onLongTaskSent(event, datadogContext, writeScope, writer)

            is RumRawEvent.ResourceDropped -> onResourceDropped(event)
            is RumRawEvent.ActionDropped -> onActionDropped(event)
            is RumRawEvent.ErrorDropped -> onErrorDropped(event)
            is RumRawEvent.LongTaskDropped -> onLongTaskDropped(event)

            is RumRawEvent.StartView -> onStartView(event, datadogContext, writeScope, writer)
            is RumRawEvent.StopView -> onStopView(event, datadogContext, writeScope, writer)
            is RumRawEvent.StartAction -> onStartAction(event, datadogContext, writeScope, writer)
            is RumRawEvent.StartResource -> onStartResource(event, datadogContext, writeScope, writer)
            is RumRawEvent.AddError -> onAddError(event, datadogContext, writeScope, writer)
            is RumRawEvent.AddLongTask -> onAddLongTask(event, datadogContext, writeScope, writer)
            is RumRawEvent.SetInternalViewAttribute -> onSetInternalViewAttribute(event)

            is RumRawEvent.AddFeatureFlagEvaluation -> onAddFeatureFlagEvaluation(
                event,
                datadogContext,
                writeScope,
                writer
            )

            is RumRawEvent.AddFeatureFlagEvaluations -> onAddFeatureFlagEvaluations(
                event,
                datadogContext,
                writeScope,
                writer
            )

            is RumRawEvent.AddCustomTiming -> onAddCustomTiming(event, datadogContext, writeScope, writer)
            is RumRawEvent.KeepAlive -> onKeepAlive(event, datadogContext, writeScope, writer)

            is RumRawEvent.StopSession -> onStopSession(event, datadogContext, writeScope, writer)

            is RumRawEvent.UpdatePerformanceMetric -> onUpdatePerformanceMetric(event)
            is RumRawEvent.UpdateExternalRefreshRate -> onUpdateExternalRefreshRate(event)
            is RumRawEvent.AddViewLoadingTime -> onAddViewLoadingTime(event, datadogContext, writeScope, writer)
            is RumRawEvent.AddViewAttributes -> onAddViewAttributes(event)
            is RumRawEvent.RemoveViewAttributes -> onRemoveViewAttributes(event)

            is RumRawEvent.StartFeatureOperation -> onStartFeatureOperation(event, datadogContext, writeScope, writer)
            is RumRawEvent.StopFeatureOperation -> onStopFeatureOperation(event, datadogContext, writeScope, writer)

            else -> delegateEventToChildren(event, datadogContext, writeScope, writer)
        }

        return if (isViewComplete()) {
            sdkCore.updateFeatureContext(Feature.SESSION_REPLAY_FEATURE_NAME) {
                it.remove(viewId)
            }
            null
        } else {
            this
        }
    }

    private fun onStartFeatureOperation(
        event: RumRawEvent.StartFeatureOperation,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            newVitalEvent(
                event,
                datadogContext,
                name = event.name,
                operationKey = event.operationKey,
                stepType = RumVitalOperationStepEvent.StepType.START,
                failureReason = null,
                eventAttributes = event.attributes
            )
        }.submit()
        sendViewUpdate(event, datadogContext, writeScope, writer)
    }

    private fun onStopFeatureOperation(
        event: RumRawEvent.StopFeatureOperation,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            newVitalEvent(
                event,
                datadogContext,
                name = event.name,
                operationKey = event.operationKey,
                stepType = RumVitalOperationStepEvent.StepType.END,
                failureReason = event.failureReason?.toSchemaFailureReason(),
                eventAttributes = event.attributes
            )
        }.submit()
        sendViewUpdate(event, datadogContext, writeScope, writer)
    }

    @Suppress("LongMethod")
    private fun newVitalEvent(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        name: String,
        operationKey: String?,
        stepType: RumVitalOperationStepEvent.StepType,
        failureReason: RumVitalOperationStepEvent.FailureReason?,
        eventAttributes: Map<String, Any?>
    ): RumVitalOperationStepEvent {
        val rumContext = getRumContext()
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            RumVitalOperationStepEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }
        val hasReplay = featuresContextResolver.resolveViewHasReplay(
            datadogContext,
            rumContext.viewId.orEmpty()
        )

        val sessionType = when {
            rumSessionTypeOverride != null -> rumSessionTypeOverride.toVital()
            syntheticsAttribute == null -> RumVitalOperationStepEvent.RumVitalOperationStepEventSessionType.USER
            else -> RumVitalOperationStepEvent.RumVitalOperationStepEventSessionType.SYNTHETICS
        }
        val batteryInfo = batteryInfoProvider.getState()
        val displayInfo = displayInfoProvider.getState()
        val user = datadogContext.userInfo

        return RumVitalOperationStepEvent(
            date = event.eventTime.timestamp + serverTimeOffsetInMs,
            context = RumVitalOperationStepEvent.Context(
                additionalProperties = getCustomAttributes().toMutableMap().also {
                    it.putAll(eventAttributes)
                }
            ),
            dd = RumVitalOperationStepEvent.Dd(
                session = RumVitalOperationStepEvent.DdSession(
                    sessionPrecondition = rumContext.sessionStartReason.toVitalOperationStepSessionPrecondition()
                ),
                configuration = RumVitalOperationStepEvent.Configuration(sessionSampleRate = sampleRate)
            ),
            application = RumVitalOperationStepEvent.Application(
                id = rumContext.applicationId,
                currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
            ),
            synthetics = syntheticsAttribute,
            session = RumVitalOperationStepEvent.RumVitalOperationStepEventSession(
                id = rumContext.sessionId,
                type = sessionType,
                hasReplay = hasReplay
            ),
            view = RumVitalOperationStepEvent.RumVitalOperationStepEventView(
                id = rumContext.viewId.orEmpty(),
                name = rumContext.viewName,
                url = rumContext.viewUrl.orEmpty()
            ),
            source = RumVitalOperationStepEvent.RumVitalOperationStepEventSource.tryFromSource(
                source = datadogContext.source,
                internalLogger = sdkCore.internalLogger
            ),
            account = datadogContext.accountInfo?.let {
                RumVitalOperationStepEvent.Account(
                    id = it.id,
                    name = it.name,
                    additionalProperties = it.extraInfo.toMutableMap()
                )
            },
            usr = if (user.hasUserData()) {
                RumVitalOperationStepEvent.Usr(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    anonymousId = user.anonymousId,
                    additionalProperties = user.additionalProperties.toMutableMap()
                )
            } else {
                null
            },
            device = RumVitalOperationStepEvent.Device(
                type = datadogContext.deviceInfo.deviceType.toVitalOperationStepSchemaType(),
                name = datadogContext.deviceInfo.deviceName,
                model = datadogContext.deviceInfo.deviceModel,
                brand = datadogContext.deviceInfo.deviceBrand,
                architecture = datadogContext.deviceInfo.architecture,
                locales = datadogContext.deviceInfo.localeInfo.locales,
                timeZone = datadogContext.deviceInfo.localeInfo.timeZone,
                batteryLevel = batteryInfo.batteryLevel,
                powerSavingMode = batteryInfo.lowPowerMode,
                brightnessLevel = displayInfo.screenBrightness
            ),
            os = RumVitalOperationStepEvent.Os(
                name = datadogContext.deviceInfo.osName,
                version = datadogContext.deviceInfo.osVersion,
                versionMajor = datadogContext.deviceInfo.osMajorVersion
            ),
            connectivity = datadogContext.networkInfo.toVitalOperationStepConnectivity(),
            version = datadogContext.version,
            buildVersion = datadogContext.versionCode.toString(),
            buildId = datadogContext.appBuildId,
            service = datadogContext.service,
            ddtags = buildDDTagsString(datadogContext),
            vital = RumVitalOperationStepEvent.Vital(
                id = UUID.randomUUID().toString(),
                name = name,
                operationKey = operationKey,
                stepType = stepType,
                failureReason = failureReason
            )
        )
    }

    override fun getRumContext(): RumContext {
        return parentScope.getRumContext().copy(
            viewId = viewId,
            viewName = key.name,
            viewUrl = url,
            actionId = (activeActionScope as? RumActionScope)?.actionId,
            viewType = type,
            viewTimestamp = eventTimestamp,
            viewTimestampOffset = serverTimeOffsetInMs,
            hasReplay = hasReplay
        )
    }

    override fun getCustomAttributes(): Map<String, Any?> {
        return if (!stopped) {
            parentScope.getCustomAttributes() + viewAttributes
        } else {
            memoizedParentAttributes + viewAttributes
        }
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    internal fun renew(newEventTime: Time): RumViewScope {
        return RumViewScope(
            parentScope = this,
            sdkCore = sdkCore,
            sessionEndedMetricDispatcher = sessionEndedMetricDispatcher,
            key = key,
            eventTime = newEventTime,
            initialAttributes = initialAttributes,
            viewChangedListener = viewChangedListener,
            firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
            cpuVitalMonitor = cpuVitalMonitor,
            memoryVitalMonitor = memoryVitalMonitor,
            frameRateVitalMonitor = frameRateVitalMonitor,
            featuresContextResolver = featuresContextResolver,
            type = type,
            trackFrustrations = trackFrustrations,
            sampleRate = sampleRate,
            interactionToNextViewMetricResolver = interactionToNextViewMetricResolver,
            networkSettledMetricResolver = networkSettledMetricResolver,
            viewEndedMetricDispatcher = viewEndedMetricDispatcher,
            slowFramesListener = slowFramesListener,
            rumSessionTypeOverride = rumSessionTypeOverride,
            accessibilitySnapshotManager = accessibilitySnapshotManager,
            batteryInfoProvider = batteryInfoProvider,
            displayInfoProvider = displayInfoProvider
        )
    }

    // endregion

    // region Internal

    @WorkerThread
    private fun onAddViewLoadingTime(
        event: RumRawEvent.AddViewLoadingTime,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val canUpdateViewLoadingTime = !stopped && (viewLoadingTime == null || event.overwrite)

        if (canUpdateViewLoadingTime) {
            updateViewLoadingTime(event, datadogContext, writeScope, writer)
        }
    }

    private fun updateViewLoadingTime(
        event: RumRawEvent.AddViewLoadingTime,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val internalLogger = sdkCore.internalLogger
        val viewName = key.name
        val previousViewLoadingTime = viewLoadingTime
        val newLoadingTime = event.eventTime.nanoTime - startedNanos
        if (previousViewLoadingTime == null) {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.USER,
                { ADDING_VIEW_LOADING_TIME_DEBUG_MESSAGE_FORMAT.format(Locale.US, newLoadingTime, viewName) }
            )
            internalLogger.logApiUsage {
                InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                    overwrite = false,
                    noView = false,
                    noActiveView = false
                )
            }
        } else if (event.overwrite) {
            internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                {
                    OVERWRITING_VIEW_LOADING_TIME_WARNING_MESSAGE_FORMAT.format(
                        Locale.US,
                        viewName,
                        previousViewLoadingTime,
                        newLoadingTime
                    )
                }
            )
            internalLogger.logApiUsage {
                InternalTelemetryEvent.ApiUsage.AddViewLoadingTime(
                    overwrite = true,
                    noView = false,
                    noActiveView = false
                )
            }
        }
        viewLoadingTime = newLoadingTime
        viewEndedMetricDispatcher.onViewLoadingTimeResolved(newLoadingTime)
        sendViewUpdate(event, datadogContext, writeScope, writer)
    }

    @WorkerThread
    private fun onAddViewAttributes(event: RumRawEvent.AddViewAttributes) {
        viewAttributes.putAll(event.attributes)
    }

    @WorkerThread
    private fun onRemoveViewAttributes(event: RumRawEvent.RemoveViewAttributes) {
        event.attributes.forEach {
            viewAttributes.remove(it)
        }
    }

    @WorkerThread
    private fun onStartView(
        event: RumRawEvent.StartView,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        stopScope(event, datadogContext, writeScope, writer)
    }

    @WorkerThread
    private fun onStopView(
        event: RumRawEvent.StopView,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, datadogContext, writeScope, writer)
        val shouldStop = (event.key.id == key.id)
        if (shouldStop && !stopped) {
            stopScope(event, datadogContext, writeScope, writer) {
                viewAttributes.putAll(event.attributes)
                memoizedParentAttributes = parentScope.getCustomAttributes().toMap()
            }
        }
    }

    @Suppress("ReturnCount")
    @WorkerThread
    private fun onStartAction(
        event: RumRawEvent.StartAction,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, datadogContext, writeScope, writer)

        if (stopped) return

        if (activeActionScope != null) {
            if (event.type == RumActionType.CUSTOM && !event.waitForStop) {
                // deliver it anyway, even if there is active action ongoing
                val customActionScope = RumActionScope.fromEvent(
                    parentScope = this,
                    sdkCore = sdkCore,
                    event = event,
                    timestampOffset = serverTimeOffsetInMs,
                    featuresContextResolver = featuresContextResolver,
                    trackFrustrations = trackFrustrations,
                    sampleRate = sampleRate,
                    rumSessionTypeOverride = rumSessionTypeOverride
                )
                pendingActionCount++
                customActionScope.handleEvent(RumRawEvent.SendCustomActionNow(), datadogContext, writeScope, writer)
                return
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    InternalLogger.Target.USER,
                    { ACTION_DROPPED_WARNING.format(Locale.US, event.type, event.name) }
                )
                return
            }
        }

        activeActionScope = RumActionScope.fromEvent(
            parentScope = this,
            sdkCore = sdkCore,
            event = event,
            timestampOffset = serverTimeOffsetInMs,
            featuresContextResolver = featuresContextResolver,
            trackFrustrations = trackFrustrations,
            sampleRate = sampleRate,
            rumSessionTypeOverride = rumSessionTypeOverride
        )
        pendingActionCount++
    }

    @WorkerThread
    private fun onStartResource(
        event: RumRawEvent.StartResource,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, datadogContext, writeScope, writer)
        if (stopped) return

        activeResourceScopes[event.key] = RumResourceScope.fromEvent(
            parentScope = this,
            sdkCore = sdkCore,
            event = event,
            firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
            timestampOffset = serverTimeOffsetInMs,
            featuresContextResolver = featuresContextResolver,
            sampleRate = sampleRate,
            networkSettledMetricResolver = networkSettledMetricResolver,
            rumSessionTypeOverride = rumSessionTypeOverride
        )
        pendingResourceCount++
    }

    @Suppress("ComplexMethod", "LongMethod")
    @WorkerThread
    private fun onAddError(
        event: RumRawEvent.AddError,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, datadogContext, writeScope, writer)
        if (stopped) return

        val rumContext = getRumContext()

        val errorCustomAttributes = getCustomAttributes().toMutableMap()
        errorCustomAttributes.putAll(event.attributes)
        val isFatal = errorCustomAttributes.remove(RumAttributes.INTERNAL_ERROR_IS_CRASH) as? Boolean == true ||
            event.isFatal
        val errorFingerprint = errorCustomAttributes.remove(RumAttributes.ERROR_FINGERPRINT) as? String
        // if a cross-platform crash was already reported, do not send its native version
        if (crashCount > 0 && isFatal) return

        val errorType = event.type ?: event.throwable?.javaClass?.canonicalName
        val throwableMessage = event.throwable?.message ?: ""
        val message = if (throwableMessage.isNotBlank() && event.message != throwableMessage) {
            "${event.message}: $throwableMessage"
        } else {
            event.message
        }
        // make a copy - by the time we iterate over it on another thread, it may already be changed
        val eventFeatureFlags = featureFlags.toMutableMap()
        val eventType = if (isFatal) EventType.CRASH else EventType.DEFAULT
        val batteryInfo = batteryInfoProvider.getState()
        val displayInfo = displayInfoProvider.getState()

        hasReplay = hasReplay || featuresContextResolver.resolveViewHasReplay(
            datadogContext,
            rumContext.viewId.orEmpty()
        )

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer, eventType) {
            val user = datadogContext.userInfo
            val syntheticsAttribute = if (
                rumContext.syntheticsTestId.isNullOrBlank() ||
                rumContext.syntheticsResultId.isNullOrBlank()
            ) {
                null
            } else {
                ErrorEvent.Synthetics(
                    testId = rumContext.syntheticsTestId,
                    resultId = rumContext.syntheticsResultId
                )
            }

            val sessionType = when {
                rumSessionTypeOverride != null -> rumSessionTypeOverride.toError()
                syntheticsAttribute == null -> ErrorEvent.ErrorEventSessionType.USER
                else -> ErrorEvent.ErrorEventSessionType.SYNTHETICS
            }

            ErrorEvent(
                buildId = datadogContext.appBuildId,
                date = event.eventTime.timestamp + serverTimeOffsetInMs,
                featureFlags = ErrorEvent.Context(eventFeatureFlags),
                error = ErrorEvent.Error(
                    id = UUID.randomUUID().toString(),
                    message = message,
                    source = event.source.toSchemaSource(),
                    stack = event.stacktrace ?: event.throwable?.loggableStackTrace(),
                    isCrash = isFatal,
                    fingerprint = errorFingerprint,
                    type = errorType,
                    sourceType = event.sourceType.toSchemaSourceType(),
                    category = ErrorEvent.Category.tryFrom(event),
                    threads = event.threads.map {
                        ErrorEvent.Thread(
                            name = it.name,
                            crashed = it.crashed,
                            stack = it.stack,
                            state = it.state
                        )
                    }.ifEmpty { null },
                    timeSinceAppStart = event.timeSinceAppStartNs?.let { TimeUnit.NANOSECONDS.toMillis(it) }
                ),
                action = rumContext.actionId?.let { ErrorEvent.Action(listOf(it)) },
                view = ErrorEvent.ErrorEventView(
                    id = rumContext.viewId.orEmpty(),
                    name = rumContext.viewName,
                    url = rumContext.viewUrl.orEmpty()
                ),
                usr = if (user.hasUserData()) {
                    ErrorEvent.Usr(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        anonymousId = user.anonymousId,
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                account = datadogContext.accountInfo?.let {
                    ErrorEvent.Account(
                        id = it.id,
                        name = it.name,
                        additionalProperties = it.extraInfo.toMutableMap()
                    )
                },
                connectivity = datadogContext.networkInfo.toErrorConnectivity(),
                application = ErrorEvent.Application(
                    id = rumContext.applicationId,
                    currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
                ),
                session = ErrorEvent.ErrorEventSession(
                    id = rumContext.sessionId,
                    type = sessionType,
                    hasReplay = hasReplay
                ),
                synthetics = syntheticsAttribute,
                source = ErrorEvent.ErrorEventSource.tryFromSource(
                    source = datadogContext.source,
                    internalLogger = sdkCore.internalLogger
                ),
                os = ErrorEvent.Os(
                    name = datadogContext.deviceInfo.osName,
                    version = datadogContext.deviceInfo.osVersion,
                    versionMajor = datadogContext.deviceInfo.osMajorVersion
                ),
                device = ErrorEvent.Device(
                    type = datadogContext.deviceInfo.deviceType.toErrorSchemaType(),
                    name = datadogContext.deviceInfo.deviceName,
                    model = datadogContext.deviceInfo.deviceModel,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    architecture = datadogContext.deviceInfo.architecture,
                    locales = datadogContext.deviceInfo.localeInfo.locales,
                    timeZone = datadogContext.deviceInfo.localeInfo.timeZone,
                    batteryLevel = batteryInfo.batteryLevel,
                    powerSavingMode = batteryInfo.lowPowerMode,
                    brightnessLevel = displayInfo.screenBrightness
                ),
                context = ErrorEvent.Context(additionalProperties = errorCustomAttributes),
                dd = ErrorEvent.Dd(
                    session = ErrorEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toErrorSessionPrecondition()
                    ),
                    configuration = ErrorEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                service = datadogContext.service,
                version = datadogContext.version,
                buildVersion = datadogContext.versionCode.toString(),
                ddtags = buildDDTagsString(datadogContext)
            )
        }
            .apply {
                if (!isFatal) {
                    // if fatal, then we don't have time for the notification, app is crashing
                    onError { it.eventDropped(rumContext.viewId.orEmpty(), StorageEvent.Error()) }
                    onSuccess { it.eventSent(rumContext.viewId.orEmpty(), StorageEvent.Error()) }
                }
            }
            .submit()

        if (isFatal) {
            errorCount++
            crashCount++
            sendViewUpdate(event, datadogContext, writeScope, writer, eventType)
        } else {
            pendingErrorCount++
        }
    }

    @WorkerThread
    private fun onAddCustomTiming(
        event: RumRawEvent.AddCustomTiming,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

        customTimings[event.name] = max(event.eventTime.nanoTime - startedNanos, 1L)
        sendViewUpdate(event, datadogContext, writeScope, writer)
    }

    private fun onUpdatePerformanceMetric(
        event: RumRawEvent.UpdatePerformanceMetric
    ) {
        if (stopped) return

        val value = event.value
        val vitalInfo = performanceMetrics[event.metric] ?: VitalInfo.EMPTY
        val newSampleCount = vitalInfo.sampleCount + 1

        // Assuming M(n) is the mean value of the first n samples
        // M(n) = ∑ sample(n) / n
        // n⨉M(n) = ∑ sample(n)
        // M(n+1) = ∑ sample(n+1) / (n+1)
        //        = [ sample(n+1) + ∑ sample(n) ] / (n+1)
        //        = (sample(n+1) + n⨉M(n)) / (n+1)
        val meanValue = (value + (vitalInfo.sampleCount * vitalInfo.meanValue)) / newSampleCount
        performanceMetrics[event.metric] = VitalInfo(
            newSampleCount,
            min(value, vitalInfo.minValue),
            max(value, vitalInfo.maxValue),
            meanValue
        )
    }

    private fun onUpdateExternalRefreshRate(
        event: RumRawEvent.UpdateExternalRefreshRate
    ) {
        if (stopped) return

        // Convert frame time (seconds) to refresh rate (Hz)
        val refreshRateHz = if (event.frameTimeSeconds > 0) {
            1.0 / event.frameTimeSeconds
        } else {
            return // Invalid frame time
        }

        val currentInfo = externalRefreshRateInfo ?: VitalInfo.EMPTY
        val newSampleCount = currentInfo.sampleCount + 1

        // Calculate incremental mean using the same algorithm as performance metrics
        val meanValue = (refreshRateHz + (currentInfo.sampleCount * currentInfo.meanValue)) / newSampleCount
        externalRefreshRateInfo = VitalInfo(
            newSampleCount,
            min(refreshRateHz, currentInfo.minValue),
            max(refreshRateHz, currentInfo.maxValue),
            meanValue
        )
    }

    @WorkerThread
    private fun onSetInternalViewAttribute(event: RumRawEvent.SetInternalViewAttribute) {
        if (stopped) return

        internalAttributes[event.key] = event.value
    }

    @WorkerThread
    private fun onStopSession(
        event: RumRawEvent.StopSession,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        stopScope(event, datadogContext, writeScope, writer)
    }

    @WorkerThread
    private fun onKeepAlive(
        event: RumRawEvent.KeepAlive,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, datadogContext, writeScope, writer)
        if (stopped) return

        sendViewUpdate(event, datadogContext, writeScope, writer)
    }

    @WorkerThread
    private fun delegateEventToChildren(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToResources(event, datadogContext, writeScope, writer)
        delegateEventToAction(event, datadogContext, writeScope, writer)
    }

    @WorkerThread
    private fun delegateEventToAction(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val currentAction = activeActionScope
        if (currentAction != null) {
            val updatedAction = currentAction.handleEvent(event, datadogContext, writeScope, writer)
            if (updatedAction == null) {
                activeActionScope = null
            }
        }
    }

    @WorkerThread
    private fun delegateEventToResources(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val iterator = activeResourceScopes.iterator()
        @Suppress("UnsafeThirdPartyFunctionCall") // next/remove can't fail: we checked hasNext
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val scope = entry.value.handleEvent(event, datadogContext, writeScope, writer)
            if (scope == null) {
                // if we finalized this scope and it was by error, we won't have resource
                // event written, but error event instead
                if (event is RumRawEvent.StopResourceWithError ||
                    event is RumRawEvent.StopResourceWithStackTrace
                ) {
                    pendingResourceCount--
                    pendingErrorCount++
                }
                iterator.remove()
            }
        }
    }

    @WorkerThread
    private fun onResourceSent(
        event: RumRawEvent.ResourceSent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingResourceCount--
            resourceCount++
            networkSettledMetricResolver.resourceWasStopped(
                InternalResourceContext(
                    event.resourceId,
                    event.resourceEndTimestampInNanos
                )
            )
            sendViewUpdate(event, datadogContext, writeScope, writer)
        }
    }

    @WorkerThread
    private fun onActionSent(
        event: RumRawEvent.ActionSent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingActionCount--
            actionCount++
            frustrationCount += event.frustrationCount
            interactionToNextViewMetricResolver.onActionSent(
                InternalInteractionContext(
                    event.viewId,
                    event.type,
                    event.eventEndTimestampInNanos
                )
            )
            sendViewUpdate(event, datadogContext, writeScope, writer)
        }
    }

    @WorkerThread
    private fun onLongTaskSent(
        event: RumRawEvent.LongTaskSent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingLongTaskCount--
            longTaskCount++
            if (event.isFrozenFrame) {
                pendingFrozenFrameCount--
                frozenFrameCount++
            }
            sendViewUpdate(event, datadogContext, writeScope, writer)
        }
    }

    @WorkerThread
    private fun onErrorSent(
        event: RumRawEvent.ErrorSent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (event.viewId == viewId) {
            pendingErrorCount--
            errorCount++
            if (event.resourceId != null && event.resourceEndTimestampInNanos != null) {
                networkSettledMetricResolver.resourceWasStopped(
                    InternalResourceContext(
                        event.resourceId,
                        event.resourceEndTimestampInNanos
                    )
                )
            }
            sendViewUpdate(event, datadogContext, writeScope, writer)
        }
    }

    private fun onResourceDropped(event: RumRawEvent.ResourceDropped) {
        if (event.viewId == viewId) {
            networkSettledMetricResolver.resourceWasDropped(event.resourceId)
            pendingResourceCount--
        }
    }

    private fun onActionDropped(event: RumRawEvent.ActionDropped) {
        if (event.viewId == viewId) {
            pendingActionCount--
        }
    }

    private fun onErrorDropped(event: RumRawEvent.ErrorDropped) {
        if (event.viewId == viewId) {
            pendingErrorCount--
            if (event.resourceId != null) {
                networkSettledMetricResolver.resourceWasDropped(event.resourceId)
            }
        }
    }

    private fun onLongTaskDropped(event: RumRawEvent.LongTaskDropped) {
        if (event.viewId == viewId) {
            pendingLongTaskCount--
            if (event.isFrozenFrame) {
                pendingFrozenFrameCount--
            }
        }
    }

    /**
     * Marks this scope as stopped, and clean up every thing that needs to.
     * This action and the side effect are only performed if the scope has not already been marked as stopped.
     * @param event the event triggering the stopping
     * @param datadogContext the datadog context
     * @param writeScope the scope for writing the event
     * @param writer the writer to send the view update
     * @param sideEffect additional side effect to be performed alongside regular cleanup.
     */
    @WorkerThread
    private fun stopScope(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        sideEffect: () -> Unit = {}
    ) {
        if (!stopped) {
            sideEffect()

            stopped = true
            resolveViewDuration(event)
            sendViewUpdate(event, datadogContext, writeScope, writer)
            delegateEventToChildren(event, datadogContext, writeScope, writer)
            sendViewChanged()

            cpuVitalMonitor.unregister(cpuVitalListener)
            memoryVitalMonitor.unregister(memoryVitalListener)
            frameRateVitalMonitor.unregister(frameRateVitalListener)
            networkSettledMetricResolver.viewWasStopped()
        }
    }

    @Suppress("LongMethod", "ComplexMethod")
    private fun sendViewUpdate(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        eventType: EventType = EventType.DEFAULT
    ) {
        val viewComplete = isViewComplete()
        val timeToSettled = networkSettledMetricResolver.resolveMetric()
        var interactionToNextViewTime = interactionToNextViewMetricResolver.resolveMetric(viewId)
        val invMetricResolverState = interactionToNextViewMetricResolver.getState(viewId)
        if (interactionToNextViewTime == null &&
            invMetricResolverState.noValueReason == NoValueReason.InteractionToNextView.DISABLED
        ) {
            interactionToNextViewTime = (internalAttributes[RumAttributes.CUSTOM_INV_VALUE] as? Long)
        }
        version++

        // make a local copy, so that closure captures the state as of now
        val eventVersion = version

        val eventActionCount = actionCount
        val eventErrorCount = errorCount
        val eventResourceCount = resourceCount
        val eventCrashCount = crashCount
        val eventLongTaskCount = longTaskCount
        val eventFrozenFramesCount = frozenFrameCount

        val eventCpuTicks = cpuTicks

        val eventFrustrationCount = frustrationCount

        val eventFlutterBuildTime = performanceMetrics[RumPerformanceMetric.FLUTTER_BUILD_TIME]
            ?.toPerformanceMetric()
        val eventFlutterRasterTime = performanceMetrics[RumPerformanceMetric.FLUTTER_RASTER_TIME]
            ?.toPerformanceMetric()
        val eventJsRefreshRate = performanceMetrics[RumPerformanceMetric.JS_FRAME_TIME]
            ?.toInversePerformanceMetric()

        if (!stopped) {
            resolveViewDuration(event)
        }
        val durationNs = stoppedNanos - startedNanos
        val rumContext = getRumContext()

        val timings = resolveCustomTimings()
        val memoryInfo = lastMemoryInfo
        // Use external refresh rate data if available, otherwise fall back to internal data
        val refreshRateInfo = externalRefreshRateInfo ?: lastFrameRateInfo
        val isSlowRendered = resolveRefreshRateInfo(refreshRateInfo) ?: false
        // make a copy - by the time we iterate over it on another thread, it may already be changed
        val eventFeatureFlags = featureFlags.toMutableMap()

        val viewCustomAttributes = getCustomAttributes().toMutableMap()

        val uiSlownessReport = slowFramesListener?.resolveReport(viewId, viewComplete, durationNs)
        val slowFrames = uiSlownessReport?.slowFramesRecords?.map {
            ViewEvent.SlowFrame(
                start = it.startTimestampNs - startedNanos,
                duration = it.durationNs
            )
        }

        // freezeRate and slowFramesRate should be sent with last view update for this view scope,
        // that will happen when isViewComplete == true
        val freezeRate = if (viewComplete) uiSlownessReport?.freezeFramesRate(stoppedNanos) else null
        val slowFramesRate = if (viewComplete) uiSlownessReport?.slowFramesRate(stoppedNanos) else null

        if (viewComplete && getRumContext().sessionState != RumSessionScope.State.NOT_TRACKED) {
            viewEndedMetricDispatcher.sendViewEnded(
                interactionToNextViewMetricResolver.getState(viewId),
                networkSettledMetricResolver.getState()
            )
        }

        val accessibility = accessibilitySnapshotManager.getIfChanged()?.let {
            ViewEvent.Accessibility(
                textSize = it.textSize,
                invertColorsEnabled = it.isColorInversionEnabled,
                singleAppModeEnabled = it.isScreenPinningEnabled,
                screenReaderEnabled = it.isScreenReaderEnabled,
                closedCaptioningEnabled = it.isClosedCaptioningEnabled,
                reducedAnimationsEnabled = it.isReducedAnimationsEnabled,
                rtlEnabled = it.isRtlEnabled
            )
        }

        val batteryInfo = batteryInfoProvider.getState()
        val displayInfo = displayInfoProvider.getState()

        val performance = (internalAttributes[RumAttributes.FLUTTER_FIRST_BUILD_COMPLETE] as? Number)?.let {
            ViewEvent.Performance(
                fbc = ViewEvent.Fbc(
                    timestamp = it.toLong()
                )
            )
        }

        val currentViewId = rumContext.viewId.orEmpty()
        hasReplay = hasReplay || featuresContextResolver.resolveViewHasReplay(
            datadogContext,
            currentViewId
        )
        val sessionReplayRecordsCount = featuresContextResolver.resolveViewRecordsCount(
            datadogContext,
            currentViewId
        )

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer, eventType) {
            val user = datadogContext.userInfo
            val replayStats = ViewEvent.ReplayStats(recordsCount = sessionReplayRecordsCount)
            val syntheticsAttribute = if (
                rumContext.syntheticsTestId.isNullOrBlank() ||
                rumContext.syntheticsResultId.isNullOrBlank()
            ) {
                null
            } else {
                ViewEvent.Synthetics(
                    testId = rumContext.syntheticsTestId,
                    resultId = rumContext.syntheticsResultId
                )
            }

            val sessionType = when {
                rumSessionTypeOverride != null -> rumSessionTypeOverride.toView()
                syntheticsAttribute == null -> ViewEvent.ViewEventSessionType.USER
                else -> ViewEvent.ViewEventSessionType.SYNTHETICS
            }
            ViewEvent(
                date = eventTimestamp,
                featureFlags = ViewEvent.Context(additionalProperties = eventFeatureFlags),
                view = ViewEvent.ViewEventView(
                    id = currentViewId,
                    name = rumContext.viewName,
                    url = rumContext.viewUrl.orEmpty(),
                    timeSpent = durationNs,
                    action = ViewEvent.Action(eventActionCount),
                    resource = ViewEvent.Resource(eventResourceCount),
                    error = ViewEvent.Error(eventErrorCount),
                    crash = ViewEvent.Crash(eventCrashCount),
                    longTask = ViewEvent.LongTask(eventLongTaskCount),
                    frozenFrame = ViewEvent.FrozenFrame(eventFrozenFramesCount),
                    customTimings = timings,
                    isActive = !viewComplete,
                    cpuTicksCount = eventCpuTicks,
                    cpuTicksPerSecond = if (durationNs >= ONE_SECOND_NS) {
                        eventCpuTicks?.let { (it * ONE_SECOND_NS) / durationNs }
                    } else {
                        null
                    },
                    memoryAverage = memoryInfo?.meanValue,
                    memoryMax = memoryInfo?.maxValue,
                    refreshRateAverage = refreshRateInfo?.meanValue,
                    refreshRateMin = refreshRateInfo?.minValue,
                    isSlowRendered = isSlowRendered,
                    frustration = ViewEvent.Frustration(eventFrustrationCount.toLong()),
                    flutterBuildTime = eventFlutterBuildTime,
                    flutterRasterTime = eventFlutterRasterTime,
                    jsRefreshRate = eventJsRefreshRate,
                    performance = performance,
                    accessibility = accessibility,
                    networkSettledTime = timeToSettled,
                    interactionToNextViewTime = interactionToNextViewTime,
                    loadingTime = viewLoadingTime,
                    slowFrames = slowFrames,
                    slowFramesRate = slowFramesRate,
                    freezeRate = freezeRate
                ),
                usr = if (user.hasUserData()) {
                    ViewEvent.Usr(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        anonymousId = user.anonymousId,
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                account = datadogContext.accountInfo?.let {
                    ViewEvent.Account(
                        id = it.id,
                        name = it.name,
                        additionalProperties = it.extraInfo.toMutableMap()
                    )
                },
                application = ViewEvent.Application(
                    id = rumContext.applicationId,
                    currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
                ),
                session = ViewEvent.ViewEventSession(
                    id = rumContext.sessionId,
                    type = sessionType,
                    hasReplay = hasReplay,
                    isActive = rumContext.isSessionActive
                ),
                synthetics = syntheticsAttribute,
                source = ViewEvent.ViewEventSource.tryFromSource(
                    datadogContext.source,
                    sdkCore.internalLogger
                ),
                os = ViewEvent.Os(
                    name = datadogContext.deviceInfo.osName,
                    version = datadogContext.deviceInfo.osVersion,
                    versionMajor = datadogContext.deviceInfo.osMajorVersion
                ),
                device = ViewEvent.Device(
                    type = datadogContext.deviceInfo.deviceType.toViewSchemaType(),
                    name = datadogContext.deviceInfo.deviceName,
                    model = datadogContext.deviceInfo.deviceModel,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    architecture = datadogContext.deviceInfo.architecture,
                    locales = datadogContext.deviceInfo.localeInfo.locales,
                    timeZone = datadogContext.deviceInfo.localeInfo.timeZone,
                    batteryLevel = batteryInfo.batteryLevel,
                    powerSavingMode = batteryInfo.lowPowerMode,
                    brightnessLevel = displayInfo.screenBrightness
                ),
                context = ViewEvent.Context(additionalProperties = viewCustomAttributes),
                dd = ViewEvent.Dd(
                    documentVersion = eventVersion,
                    session = ViewEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toViewSessionPrecondition()
                    ),
                    replayStats = replayStats,
                    configuration = ViewEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                connectivity = datadogContext.networkInfo.toViewConnectivity(),
                service = datadogContext.service,
                version = datadogContext.version,
                buildVersion = datadogContext.versionCode.toString(),
                buildId = datadogContext.appBuildId,
                ddtags = buildDDTagsString(datadogContext)
            ).apply {
                sessionEndedMetricDispatcher.onViewTracked(sessionId, this)
            }
        }.submit()
    }

    private fun resolveViewDuration(event: RumRawEvent) {
        stoppedNanos = event.eventTime.nanoTime
        val duration = stoppedNanos - startedNanos
        viewEndedMetricDispatcher.onDurationResolved(duration)
        if (duration == 0L) {
            if (type == RumViewType.BACKGROUND && event is RumRawEvent.AddError && event.isFatal) {
                // This is a legitimate empty duration, no-op
            } else {
                sdkCore.internalLogger.log(
                    InternalLogger.Level.WARN,
                    listOf(
                        InternalLogger.Target.USER,
                        InternalLogger.Target.TELEMETRY
                    ),
                    { ZERO_DURATION_WARNING_MESSAGE.format(Locale.US, key.name) },
                    null,
                    false,
                    mapOf("view.name" to key.name)
                )
            }
            stoppedNanos = startedNanos + 1
        } else if (duration < 0) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                listOf(
                    InternalLogger.Target.USER,
                    InternalLogger.Target.TELEMETRY
                ),
                { NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, key.name) },
                null,
                false,
                mapOf(
                    "view.start_ns" to startedNanos,
                    "view.end_ns" to event.eventTime.nanoTime,
                    "view.name" to key.name
                )
            )
            stoppedNanos = startedNanos + 1
        }
    }

    private fun resolveRefreshRateInfo(refreshRateInfo: VitalInfo?) =
        if (refreshRateInfo == null) {
            null
        } else {
            refreshRateInfo.meanValue < SLOW_RENDERED_THRESHOLD_FPS
        }

    private fun resolveCustomTimings() = if (customTimings.isNotEmpty()) {
        ViewEvent.CustomTimings(LinkedHashMap(customTimings))
    } else {
        null
    }

    @Suppress("LongMethod")
    @WorkerThread
    private fun onAddLongTask(
        event: RumRawEvent.AddLongTask,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        delegateEventToChildren(event, datadogContext, writeScope, writer)
        if (stopped) return

        val rumContext = getRumContext()
        val longTaskCustomAttributes = getCustomAttributes().toMutableMap().apply {
            put(RumAttributes.LONG_TASK_TARGET, event.target)
        }

        val timestamp = event.eventTime.timestamp + serverTimeOffsetInMs
        val isFrozenFrame = event.durationNs > FROZEN_FRAME_THRESHOLD_NS
        slowFramesListener?.onAddLongTask(event.durationNs)
        hasReplay = hasReplay || featuresContextResolver.resolveViewHasReplay(
            datadogContext,
            rumContext.viewId.orEmpty()
        )
        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
            val user = datadogContext.userInfo
            val syntheticsAttribute = if (
                rumContext.syntheticsTestId.isNullOrBlank() ||
                rumContext.syntheticsResultId.isNullOrBlank()
            ) {
                null
            } else {
                LongTaskEvent.Synthetics(
                    testId = rumContext.syntheticsTestId,
                    resultId = rumContext.syntheticsResultId
                )
            }

            val sessionType = when {
                rumSessionTypeOverride != null -> rumSessionTypeOverride.toLongTask()
                syntheticsAttribute == null -> LongTaskEvent.LongTaskEventSessionType.USER
                else -> LongTaskEvent.LongTaskEventSessionType.SYNTHETICS
            }

            LongTaskEvent(
                date = timestamp - TimeUnit.NANOSECONDS.toMillis(event.durationNs),
                longTask = LongTaskEvent.LongTask(
                    id = UUID.randomUUID().toString(),
                    duration = event.durationNs,
                    isFrozenFrame = isFrozenFrame
                ),
                action = rumContext.actionId?.let { LongTaskEvent.Action(listOf(it)) },
                view = LongTaskEvent.LongTaskEventView(
                    id = rumContext.viewId.orEmpty(),
                    name = rumContext.viewName,
                    url = rumContext.viewUrl.orEmpty()
                ),
                usr = if (user.hasUserData()) {
                    LongTaskEvent.Usr(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        anonymousId = user.anonymousId,
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                account = datadogContext.accountInfo?.let {
                    LongTaskEvent.Account(
                        id = it.id,
                        name = it.name,
                        additionalProperties = it.extraInfo.toMutableMap()
                    )
                },
                connectivity = datadogContext.networkInfo.toLongTaskConnectivity(),
                application = LongTaskEvent.Application(rumContext.applicationId),
                session = LongTaskEvent.LongTaskEventSession(
                    id = rumContext.sessionId,
                    type = sessionType,
                    hasReplay = hasReplay
                ),
                synthetics = syntheticsAttribute,
                source = LongTaskEvent.LongTaskEventSource.tryFromSource(
                    datadogContext.source,
                    sdkCore.internalLogger
                ),
                os = LongTaskEvent.Os(
                    name = datadogContext.deviceInfo.osName,
                    version = datadogContext.deviceInfo.osVersion,
                    versionMajor = datadogContext.deviceInfo.osMajorVersion
                ),
                device = LongTaskEvent.Device(
                    type = datadogContext.deviceInfo.deviceType.toLongTaskSchemaType(),
                    name = datadogContext.deviceInfo.deviceName,
                    model = datadogContext.deviceInfo.deviceModel,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    architecture = datadogContext.deviceInfo.architecture
                ),
                context = LongTaskEvent.Context(additionalProperties = longTaskCustomAttributes),
                dd = LongTaskEvent.Dd(
                    session = LongTaskEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toLongTaskSessionPrecondition()
                    ),
                    configuration = LongTaskEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                service = datadogContext.service,
                version = datadogContext.version,
                buildVersion = datadogContext.versionCode.toString(),
                buildId = datadogContext.appBuildId,
                ddtags = buildDDTagsString(datadogContext)
            )
        }
            .apply {
                val storageEvent =
                    if (isFrozenFrame) StorageEvent.FrozenFrame else StorageEvent.LongTask
                onError { it.eventDropped(rumContext.viewId.orEmpty(), storageEvent) }
                onSuccess { it.eventSent(rumContext.viewId.orEmpty(), storageEvent) }
            }
            .submit()

        pendingLongTaskCount++
        if (isFrozenFrame) pendingFrozenFrameCount++
    }

    private fun onAddFeatureFlagEvaluation(
        event: RumRawEvent.AddFeatureFlagEvaluation,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

        if (event.value != featureFlags[event.name]) {
            featureFlags[event.name] = event.value
            sendViewUpdate(event, datadogContext, writeScope, writer)
            sendViewChanged()
        }
    }

    private fun onAddFeatureFlagEvaluations(
        event: RumRawEvent.AddFeatureFlagEvaluations,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (stopped) return

        var modified = false
        event.featureFlags.forEach { (k, v) ->
            if (v != featureFlags[k]) {
                featureFlags[k] = v
                modified = true
            }
        }

        if (modified) {
            sendViewUpdate(event, datadogContext, writeScope, writer)
            sendViewChanged()
        }
    }

    private fun sendViewChanged() {
        viewChangedListener?.onViewChanged(
            RumViewInfo(
                key = key,
                attributes = viewAttributes,
                isActive = isActive()
            )
        )
    }

    private fun isViewComplete(): Boolean {
        val pending = pendingActionCount +
            pendingResourceCount +
            pendingErrorCount +
            pendingLongTaskCount
        // we use <= 0 for pending counter as a safety measure to make sure this ViewScope will
        // be closed.
        return stopped && activeResourceScopes.isEmpty() && (pending <= 0L)
    }

    private fun ErrorEvent.Category.Companion.tryFrom(
        event: RumRawEvent.AddError
    ): ErrorEvent.Category? {
        return if (event.throwable != null) {
            if (event.throwable is ANRException) ErrorEvent.Category.ANR else ErrorEvent.Category.EXCEPTION
        } else if (event.stacktrace != null) {
            ErrorEvent.Category.EXCEPTION
        } else {
            null
        }
    }

    private fun logSynthetics(key: String, value: String) {
        /**
         * We use [android.util.Log] here instead of [InternalLogger] because we want to log regardless of the
         * verbosity level set using [com.datadog.android.Datadog.setVerbosity].
         */
        Log.i("DatadogSynthetics", "$key=$value")
    }

    // endregion

    companion object {
        internal val ONE_SECOND_NS = TimeUnit.SECONDS.toNanos(1)

        internal const val ACTION_DROPPED_WARNING = "RUM Action (%s on %s) was dropped, because" +
            " another action is still active for the same view"

        internal val FROZEN_FRAME_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(700)
        internal const val SLOW_RENDERED_THRESHOLD_FPS = 55
        internal const val ZERO_DURATION_WARNING_MESSAGE = "The computed duration for the " +
            "view: %s was 0. In order to keep the view we forced it to 1ns."
        internal const val NEGATIVE_DURATION_WARNING_MESSAGE = "The computed duration for the " +
            "view: %s was negative. In order to keep the view we forced it to 1ns."
        internal const val ADDING_VIEW_LOADING_TIME_DEBUG_MESSAGE_FORMAT =
            "View loading time %dns added to the view %s"
        internal const val OVERWRITING_VIEW_LOADING_TIME_WARNING_MESSAGE_FORMAT =
            "View loading time already exists for the view %s. Replacing the existing %d ns " +
                "view loading time with the new %d ns loading time."

        internal fun fromEvent(
            parentScope: RumScope,
            sessionEndedMetricDispatcher: SessionMetricDispatcher,
            sdkCore: InternalSdkCore,
            event: RumRawEvent.StartView,
            viewChangedListener: RumViewChangedListener?,
            firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
            cpuVitalMonitor: VitalMonitor,
            memoryVitalMonitor: VitalMonitor,
            frameRateVitalMonitor: VitalMonitor,
            trackFrustrations: Boolean,
            sampleRate: Float,
            interactionToNextViewMetricResolver: InteractionToNextViewMetricResolver,
            networkSettledResourceIdentifier: InitialResourceIdentifier,
            slowFramesListener: SlowFramesListener?,
            rumSessionTypeOverride: RumSessionType?,
            accessibilitySnapshotManager: AccessibilitySnapshotManager,
            batteryInfoProvider: InfoProvider<BatteryInfo>,
            displayInfoProvider: InfoProvider<DisplayInfo>
        ): RumViewScope {
            val networkSettledMetricResolver = NetworkSettledMetricResolver(
                networkSettledResourceIdentifier,
                sdkCore.internalLogger
            )

            val viewType = RumViewType.FOREGROUND
            val viewEndedMetricDispatcher = ViewEndedMetricDispatcher(
                viewType = viewType,
                internalLogger = sdkCore.internalLogger,
                instrumentationType = event.tryResolveInstrumentationType()
            )

            return RumViewScope(
                parentScope,
                sdkCore,
                sessionEndedMetricDispatcher,
                event.key,
                event.eventTime,
                event.attributes,
                viewChangedListener,
                firstPartyHostHeaderTypeResolver,
                cpuVitalMonitor,
                memoryVitalMonitor,
                frameRateVitalMonitor,
                type = viewType,
                trackFrustrations = trackFrustrations,
                sampleRate = sampleRate,
                interactionToNextViewMetricResolver = interactionToNextViewMetricResolver,
                networkSettledMetricResolver = networkSettledMetricResolver,
                viewEndedMetricDispatcher = viewEndedMetricDispatcher,
                slowFramesListener = slowFramesListener,
                rumSessionTypeOverride = rumSessionTypeOverride,
                accessibilitySnapshotManager = accessibilitySnapshotManager,
                batteryInfoProvider = batteryInfoProvider,
                displayInfoProvider = displayInfoProvider
            )
        }

        private fun VitalInfo.toPerformanceMetric(): ViewEvent.FlutterBuildTime {
            return ViewEvent.FlutterBuildTime(
                min = minValue,
                max = maxValue,
                average = meanValue
            )
        }

        private fun RumRawEvent.StartView.tryResolveInstrumentationType() =
            attributes[LocalAttribute.Key.VIEW_SCOPE_INSTRUMENTATION_TYPE.toString()] as? ViewScopeInstrumentationType

        @Suppress("CommentOverPrivateFunction")
        /**
         * This function is used to inverse frame times metrics into frame rates.
         *
         * As we take the inverse, the min of the inverse is the inverse of the max and
         * vice-versa.
         * For instance, if the min frame time is 20ms (50 fps) and the max is 500ms (2 fps),
         * the max frame rate is 50 fps (1/minValue) and the min is 2 fps (1/maxValue).
         *
         * As the frame times are reported in nanoseconds, we need to add a multiplier.
         */
        private fun VitalInfo.toInversePerformanceMetric(): ViewEvent.FlutterBuildTime {
            return ViewEvent.FlutterBuildTime(
                min = invertValue(maxValue) * TimeUnit.SECONDS.toNanos(1),
                max = invertValue(minValue) * TimeUnit.SECONDS.toNanos(1),
                average = invertValue(meanValue) * TimeUnit.SECONDS.toNanos(1)
            )
        }

        private fun invertValue(value: Double): Double {
            return if (value == 0.0) 0.0 else 1.0 / value
        }
    }
}
