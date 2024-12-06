/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.utils.hasUserData
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale
import java.util.UUID

@Suppress("LongParameterList", "TooManyFunctions")
internal class RumResourceScope(
    internal val parentScope: RumScope,
    internal val sdkCore: InternalSdkCore,
    internal val url: String,
    internal val method: RumResourceMethod,
    internal val key: Any,
    eventTime: Time,
    initialAttributes: Map<String, Any?>,
    serverTimeOffsetInMs: Long,
    internal val firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
    private val featuresContextResolver: FeaturesContextResolver,
    internal val sampleRate: Float,
    internal val networkSettledMetricResolver: NetworkSettledMetricResolver
) : RumScope {

    internal val resourceId: String = UUID.randomUUID().toString()
    internal val attributes: MutableMap<String, Any?> = initialAttributes.toMutableMap().apply {
        putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
    }
    private var timing: ResourceTiming? = null
    private val initialContext = parentScope.getRumContext()

    internal val eventTimestamp = eventTime.timestamp + serverTimeOffsetInMs
    private val startedNanos: Long = eventTime.nanoTime
    private val networkInfo = sdkCore.networkInfo

    private var sent = false
    private var waitForTiming = false
    internal var stopped = false
    private var kind: RumResourceKind = RumResourceKind.UNKNOWN
    private var statusCode: Long? = null
    private var size: Long? = null

    init {
        networkSettledMetricResolver.resourceWasStarted(
            InternalResourceContext(resourceId, eventTime.nanoTime)
        )
    }

    // region RumScope

    @WorkerThread
    override fun handleEvent(event: RumRawEvent, writer: DataWriter<Any>): RumScope? {
        when (event) {
            is RumRawEvent.WaitForResourceTiming -> if (key == event.key) waitForTiming = true
            is RumRawEvent.AddResourceTiming -> onAddResourceTiming(event, writer)
            is RumRawEvent.StopResource -> onStopResource(event, writer)
            is RumRawEvent.StopResourceWithError -> onStopResourceWithError(event, writer)
            is RumRawEvent.StopResourceWithStackTrace -> onStopResourceWithStackTrace(event, writer)
            else -> {
                // Other events are not relevant for RumResourceScope
            }
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return initialContext
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region  Internal

    @WorkerThread
    private fun onStopResource(
        event: RumRawEvent.StopResource,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        stopped = true
        attributes.putAll(event.attributes)
        kind = event.kind
        statusCode = event.statusCode
        size = event.size

        if (!(waitForTiming && timing == null)) {
            sendResource(kind, event.statusCode, event.size, event.eventTime, writer)
        }
    }

    @WorkerThread
    private fun onAddResourceTiming(
        event: RumRawEvent.AddResourceTiming,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        timing = event.timing
        if (stopped && !sent) {
            sendResource(kind, statusCode, size, event.eventTime, writer)
        }
    }

    @WorkerThread
    private fun onStopResourceWithError(
        event: RumRawEvent.StopResourceWithError,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        attributes.putAll(event.attributes)
        reportResourceStoppedMetric(event.eventTime.nanoTime)
        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.throwable.loggableStackTrace(),
            event.throwable.javaClass.canonicalName,
            ErrorEvent.Category.EXCEPTION,
            writer
        )
    }

    @WorkerThread
    private fun onStopResourceWithStackTrace(
        event: RumRawEvent.StopResourceWithStackTrace,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        reportResourceStoppedMetric(event.eventTime.nanoTime)
        attributes.putAll(event.attributes)

        val errorCategory =
            if (event.stackTrace.isNotEmpty()) ErrorEvent.Category.EXCEPTION else null

        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.stackTrace,
            event.errorType,
            errorCategory,
            writer
        )
    }

    private fun reportResourceStoppedMetric(eventTimeStamp: Long) {
        networkSettledMetricResolver.resourceWasStopped(
            InternalResourceContext(resourceId, eventTimeStamp)
        )
    }

    @Suppress("LongMethod")
    private fun sendResource(
        kind: RumResourceKind,
        statusCode: Long?,
        size: Long?,
        eventTime: Time,
        writer: DataWriter<Any>
    ) {
        attributes.putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
        val traceId = attributes.remove(RumAttributes.TRACE_ID)?.toString()
        val spanId = attributes.remove(RumAttributes.SPAN_ID)?.toString()
        val rulePsr = attributes.remove(RumAttributes.RULE_PSR) as? Number

        val rumContext = getRumContext()
        val syntheticsAttribute = if (
            rumContext.syntheticsTestId.isNullOrBlank() ||
            rumContext.syntheticsResultId.isNullOrBlank()
        ) {
            null
        } else {
            ResourceEvent.Synthetics(
                testId = rumContext.syntheticsTestId,
                resultId = rumContext.syntheticsResultId
            )
        }
        val sessionType = if (syntheticsAttribute == null) {
            ResourceEvent.ResourceEventSessionType.USER
        } else {
            ResourceEvent.ResourceEventSessionType.SYNTHETICS
        }

        @Suppress("UNCHECKED_CAST")
        val finalTiming = timing ?: extractResourceTiming(
            attributes.remove(RumAttributes.RESOURCE_TIMINGS) as? Map<String, Any?>
        )
        val graphql = resolveGraphQLAttributes(
            attributes.remove(RumAttributes.GRAPHQL_OPERATION_TYPE) as? String?,
            attributes.remove(RumAttributes.GRAPHQL_OPERATION_NAME) as? String?,
            attributes.remove(RumAttributes.GRAPHQL_PAYLOAD) as? String?,
            attributes.remove(RumAttributes.GRAPHQL_VARIABLES) as? String?
        )
        val eventAttributes = attributes.toMutableMap()
        sdkCore.newRumEventWriteOperation(writer) { datadogContext ->
            val user = datadogContext.userInfo
            val hasReplay = featuresContextResolver.resolveViewHasReplay(
                datadogContext,
                rumContext.viewId.orEmpty()
            )
            val duration = resolveResourceDuration(eventTime)
            ResourceEvent(
                date = eventTimestamp,
                resource = ResourceEvent.Resource(
                    id = resourceId,
                    type = kind.toSchemaType(),
                    url = url,
                    duration = duration,
                    method = method.toResourceMethod(),
                    statusCode = statusCode,
                    size = size,
                    dns = finalTiming?.dns(),
                    connect = finalTiming?.connect(),
                    ssl = finalTiming?.ssl(),
                    firstByte = finalTiming?.firstByte(),
                    download = finalTiming?.download(),
                    provider = resolveResourceProvider(),
                    graphql = graphql
                ),
                action = rumContext.actionId?.let { ResourceEvent.Action(listOf(it)) },
                view = ResourceEvent.ResourceEventView(
                    id = rumContext.viewId.orEmpty(),
                    name = rumContext.viewName,
                    url = rumContext.viewUrl.orEmpty()
                ),
                usr = if (user.hasUserData()) {
                    ResourceEvent.Usr(
                        id = user.id,
                        name = user.name,
                        email = user.email,
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                connectivity = networkInfo.toResourceConnectivity(),
                application = ResourceEvent.Application(rumContext.applicationId),
                session = ResourceEvent.ResourceEventSession(
                    id = rumContext.sessionId,
                    type = sessionType,
                    hasReplay = hasReplay
                ),
                synthetics = syntheticsAttribute,
                source = ResourceEvent.ResourceEventSource.tryFromSource(
                    datadogContext.source,
                    sdkCore.internalLogger
                ),
                os = ResourceEvent.Os(
                    name = datadogContext.deviceInfo.osName,
                    version = datadogContext.deviceInfo.osVersion,
                    versionMajor = datadogContext.deviceInfo.osMajorVersion
                ),
                device = ResourceEvent.Device(
                    type = datadogContext.deviceInfo.deviceType.toResourceSchemaType(),
                    name = datadogContext.deviceInfo.deviceName,
                    model = datadogContext.deviceInfo.deviceModel,
                    brand = datadogContext.deviceInfo.deviceBrand,
                    architecture = datadogContext.deviceInfo.architecture
                ),
                context = ResourceEvent.Context(additionalProperties = eventAttributes),
                dd = ResourceEvent.Dd(
                    traceId = traceId,
                    spanId = spanId,
                    rulePsr = rulePsr,
                    session = ResourceEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toResourceSessionPrecondition()
                    ),
                    configuration = ResourceEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                service = datadogContext.service,
                version = datadogContext.version
            )
        }
            .onError {
                it.eventDropped(rumContext.viewId.orEmpty(), StorageEvent.Resource(resourceId, eventTime.nanoTime))
            }
            .onSuccess {
                it.eventSent(rumContext.viewId.orEmpty(), StorageEvent.Resource(resourceId, eventTime.nanoTime))
            }
            .submit()

        sent = true
    }

    private fun resolveResourceDuration(eventTime: Time): Long {
        val duration = eventTime.nanoTime - startedNanos
        return if (duration <= 0) {
            sdkCore.internalLogger.log(
                InternalLogger.Level.WARN,
                InternalLogger.Target.USER,
                { NEGATIVE_DURATION_WARNING_MESSAGE.format(Locale.US, url) }
            )
            1
        } else {
            duration
        }
    }

    private fun resolveResourceProvider(): ResourceEvent.Provider? {
        return if (firstPartyHostHeaderTypeResolver.isFirstPartyUrl(url)) {
            ResourceEvent.Provider(
                resolveDomain(url),
                type = ResourceEvent.ProviderType.FIRST_PARTY
            )
        } else {
            null
        }
    }

    @SuppressWarnings("LongMethod")
    private fun sendError(
        message: String,
        source: RumErrorSource,
        statusCode: Long?,
        stackTrace: String?,
        errorType: String?,
        errorCategory: ErrorEvent.Category?,
        writer: DataWriter<Any>
    ) {
        attributes.putAll(GlobalRumMonitor.get(sdkCore).getAttributes())
        val errorFingerprint = attributes.remove(RumAttributes.ERROR_FINGERPRINT) as? String

        val rumContext = getRumContext()

        val eventAttributes = attributes.toMutableMap()
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
        val sessionType = if (syntheticsAttribute == null) {
            ErrorEvent.ErrorEventSessionType.USER
        } else {
            ErrorEvent.ErrorEventSessionType.SYNTHETICS
        }
        sdkCore.newRumEventWriteOperation(writer) { datadogContext ->
            val user = datadogContext.userInfo
            val hasReplay = featuresContextResolver.resolveViewHasReplay(
                datadogContext,
                rumContext.viewId.orEmpty()
            )
            ErrorEvent(
                buildId = datadogContext.appBuildId,
                date = eventTimestamp,
                error = ErrorEvent.Error(
                    message = message,
                    source = source.toSchemaSource(),
                    stack = stackTrace,
                    isCrash = false,
                    fingerprint = errorFingerprint,
                    resource = ErrorEvent.Resource(
                        url = url,
                        method = method.toErrorMethod(),
                        statusCode = statusCode ?: 0,
                        provider = resolveErrorProvider()
                    ),
                    type = errorType,
                    category = errorCategory,
                    sourceType = ErrorEvent.SourceType.ANDROID
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
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                connectivity = networkInfo.toErrorConnectivity(),
                application = ErrorEvent.Application(rumContext.applicationId),
                session = ErrorEvent.ErrorEventSession(
                    id = rumContext.sessionId,
                    type = sessionType,
                    hasReplay = hasReplay
                ),
                synthetics = syntheticsAttribute,
                source = ErrorEvent.ErrorEventSource.tryFromSource(
                    datadogContext.source,
                    sdkCore.internalLogger
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
                    architecture = datadogContext.deviceInfo.architecture
                ),
                context = ErrorEvent.Context(additionalProperties = eventAttributes),
                dd = ErrorEvent.Dd(
                    session = ErrorEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toErrorSessionPrecondition()
                    ),
                    configuration = ErrorEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                service = datadogContext.service,
                version = datadogContext.version
            )
        }
            .onError {
                it.eventDropped(rumContext.viewId.orEmpty(), StorageEvent.Error)
            }
            .onSuccess {
                it.eventSent(rumContext.viewId.orEmpty(), StorageEvent.Error)
            }
            .submit()

        sent = true
    }

    private fun resolveErrorProvider(): ErrorEvent.Provider? {
        return if (firstPartyHostHeaderTypeResolver.isFirstPartyUrl(url)) {
            ErrorEvent.Provider(
                domain = resolveDomain(url),
                type = ErrorEvent.ProviderType.FIRST_PARTY
            )
        } else {
            null
        }
    }

    private fun resolveDomain(url: String): String {
        return try {
            URL(url).host
        } catch (e: MalformedURLException) {
            url
        }
    }

    private fun resolveGraphQLAttributes(
        operationType: String?,
        operationName: String?,
        payload: String?,
        variables: String?
    ): ResourceEvent.Graphql? {
        operationType?.toOperationType(sdkCore.internalLogger)?.let {
            return ResourceEvent.Graphql(
                it,
                operationName,
                payload,
                variables
            )
        }

        return null
    }

    // endregion

    companion object {

        internal const val NEGATIVE_DURATION_WARNING_MESSAGE = "The computed duration for your " +
            "resource: %s was 0 or negative. In order to keep the resource event" +
            " we forced it to 1ns."

        @Suppress("LongParameterList")
        fun fromEvent(
            parentScope: RumScope,
            sdkCore: InternalSdkCore,
            event: RumRawEvent.StartResource,
            firstPartyHostHeaderTypeResolver: FirstPartyHostHeaderTypeResolver,
            timestampOffset: Long,
            featuresContextResolver: FeaturesContextResolver,
            sampleRate: Float,
            networkSettledMetricResolver: NetworkSettledMetricResolver
        ): RumScope {
            return RumResourceScope(
                parentScope = parentScope,
                sdkCore = sdkCore,
                url = event.url,
                method = event.method,
                key = event.key,
                eventTime = event.eventTime,
                initialAttributes = event.attributes,
                serverTimeOffsetInMs = timestampOffset,
                firstPartyHostHeaderTypeResolver = firstPartyHostHeaderTypeResolver,
                featuresContextResolver = featuresContextResolver,
                sampleRate = sampleRate,
                networkSettledMetricResolver = networkSettledMetricResolver
            )
        }
    }
}
