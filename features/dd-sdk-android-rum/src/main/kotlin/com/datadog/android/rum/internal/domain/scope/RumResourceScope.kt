/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.scope

import androidx.annotation.WorkerThread
import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.EventWriteScope
import com.datadog.android.api.storage.DataWriter
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.net.FirstPartyHostHeaderTypeResolver
import com.datadog.android.internal.utils.loggableStackTrace
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumErrorSource
import com.datadog.android.rum.RumResourceKind
import com.datadog.android.rum.RumResourceMethod
import com.datadog.android.rum.RumSessionType
import com.datadog.android.rum.internal.FeaturesContextResolver
import com.datadog.android.rum.internal.domain.RumContext
import com.datadog.android.rum.internal.domain.Time
import com.datadog.android.rum.internal.domain.event.ResourceTiming
import com.datadog.android.rum.internal.metric.networksettled.InternalResourceContext
import com.datadog.android.rum.internal.metric.networksettled.NetworkSettledMetricResolver
import com.datadog.android.rum.internal.monitor.StorageEvent
import com.datadog.android.rum.internal.toError
import com.datadog.android.rum.internal.toResource
import com.datadog.android.rum.internal.utils.buildDDTagsString
import com.datadog.android.rum.internal.utils.hasUserData
import com.datadog.android.rum.internal.utils.newRumEventWriteOperation
import com.datadog.android.rum.model.ErrorEvent
import com.datadog.android.rum.model.ResourceEvent
import java.net.MalformedURLException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CoderMalfunctionError
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

@Suppress("LongParameterList", "TooManyFunctions")
internal class RumResourceScope(
    override val parentScope: RumScope,
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
    networkSettledMetricResolver: NetworkSettledMetricResolver,
    private val rumSessionTypeOverride: RumSessionType?
) : RumScope {

    internal val resourceId: String = UUID.randomUUID().toString()
    internal val resourceAttributes: MutableMap<String, Any?> = initialAttributes.toMutableMap()

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
    override fun handleEvent(
        event: RumRawEvent,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ): RumScope? {
        when (event) {
            is RumRawEvent.WaitForResourceTiming -> if (key == event.key) waitForTiming = true
            is RumRawEvent.AddResourceTiming -> onAddResourceTiming(event, datadogContext, writeScope, writer)
            is RumRawEvent.StopResource -> onStopResource(event, datadogContext, writeScope, writer)
            is RumRawEvent.StopResourceWithError -> onStopResourceWithError(event, datadogContext, writeScope, writer)
            is RumRawEvent.StopResourceWithStackTrace -> onStopResourceWithStackTrace(
                event,
                datadogContext,
                writeScope,
                writer
            )

            else -> {
                // Other events are not relevant for RumResourceScope
            }
        }

        return if (sent) null else this
    }

    override fun getRumContext(): RumContext {
        return initialContext
    }

    override fun getCustomAttributes(): Map<String, Any?> {
        return parentScope.getCustomAttributes() + resourceAttributes
    }

    override fun isActive(): Boolean {
        return !stopped
    }

    // endregion

    // region  Internal

    @WorkerThread
    private fun onStopResource(
        event: RumRawEvent.StopResource,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        stopped = true
        resourceAttributes.putAll(event.attributes)
        kind = event.kind
        statusCode = event.statusCode
        size = event.size

        if (!(waitForTiming && timing == null)) {
            sendResource(kind, event.statusCode, event.size, event.eventTime, datadogContext, writeScope, writer)
        }
    }

    @WorkerThread
    private fun onAddResourceTiming(
        event: RumRawEvent.AddResourceTiming,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        timing = event.timing
        if (stopped && !sent) {
            sendResource(kind, statusCode, size, event.eventTime, datadogContext, writeScope, writer)
        }
    }

    @WorkerThread
    private fun onStopResourceWithError(
        event: RumRawEvent.StopResourceWithError,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        resourceAttributes.putAll(event.attributes)
        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.throwable.loggableStackTrace(),
            event.throwable.javaClass.canonicalName,
            ErrorEvent.Category.EXCEPTION,
            datadogContext,
            writeScope,
            writer,
            event.eventTime.nanoTime
        )
    }

    @WorkerThread
    private fun onStopResourceWithStackTrace(
        event: RumRawEvent.StopResourceWithStackTrace,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        if (key != event.key) return
        resourceAttributes.putAll(event.attributes)

        val errorCategory =
            if (event.stackTrace.isNotEmpty()) ErrorEvent.Category.EXCEPTION else null

        sendError(
            event.message,
            event.source,
            event.statusCode,
            event.stackTrace,
            event.errorType,
            errorCategory,
            datadogContext,
            writeScope,
            writer,
            event.eventTime.nanoTime
        )
    }

    @Suppress("LongMethod")
    private fun sendResource(
        kind: RumResourceKind,
        statusCode: Long?,
        size: Long?,
        eventTime: Time,
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>
    ) {
        val traceId = resourceAttributes.remove(RumAttributes.TRACE_ID)?.toString()
        val spanId = resourceAttributes.remove(RumAttributes.SPAN_ID)?.toString()
        val rulePsr = resourceAttributes.remove(RumAttributes.RULE_PSR) as? Number

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
        val sessionType = when {
            rumSessionTypeOverride != null -> rumSessionTypeOverride.toResource()
            syntheticsAttribute == null -> ResourceEvent.ResourceEventSessionType.USER
            else -> ResourceEvent.ResourceEventSessionType.SYNTHETICS
        }

        @Suppress("UNCHECKED_CAST")
        val finalTiming = timing ?: extractResourceTiming(
            resourceAttributes.remove(RumAttributes.RESOURCE_TIMINGS) as? Map<String, Any?>
        )

        val graphqlOperationName = resourceAttributes.remove(RumAttributes.GRAPHQL_OPERATION_NAME) as? String
        val graphqlOperationType = resourceAttributes.remove(RumAttributes.GRAPHQL_OPERATION_TYPE) as? String
        val graphqlVariables = resourceAttributes.remove(RumAttributes.GRAPHQL_VARIABLES) as? String

        // The decision whether to send payloads is determined by a DatadogApolloInterceptor parameter
        val rawPayload = resourceAttributes.remove(RumAttributes.GRAPHQL_PAYLOAD) as? String
        val graphqlPayload = rawPayload?.truncateToUtf8Bytes(MAX_GRAPHQL_PAYLOAD_SIZE_BYTES)

        val graphql = resolveGraphQLAttributes(
            operationType = graphqlOperationType,
            operationName = graphqlOperationName,
            variables = graphqlVariables,
            payload = graphqlPayload
        )

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
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
                        anonymousId = user.anonymousId,
                        additionalProperties = user.additionalProperties.toMutableMap()
                    )
                } else {
                    null
                },
                account = datadogContext.accountInfo?.let {
                    ResourceEvent.Account(
                        id = it.id,
                        name = it.name,
                        additionalProperties = it.extraInfo.toMutableMap()
                    )
                },
                connectivity = networkInfo.toResourceConnectivity(),
                application = ResourceEvent.Application(
                    id = rumContext.applicationId,
                    currentLocale = datadogContext.deviceInfo.localeInfo.currentLocale
                ),
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
                    architecture = datadogContext.deviceInfo.architecture,
                    locales = datadogContext.deviceInfo.localeInfo.locales,
                    timeZone = datadogContext.deviceInfo.localeInfo.timeZone
                ),
                context = ResourceEvent.Context(additionalProperties = getCustomAttributes().toMutableMap()),
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
                version = datadogContext.version,
                buildVersion = datadogContext.versionCode,
                buildId = datadogContext.appBuildId,
                ddtags = buildDDTagsString(datadogContext)
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
        datadogContext: DatadogContext,
        writeScope: EventWriteScope,
        writer: DataWriter<Any>,
        resourceStopTimestampInNanos: Long
    ) {
        val errorFingerprint = resourceAttributes.remove(RumAttributes.ERROR_FINGERPRINT) as? String
        val rumContext = getRumContext()

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

        sdkCore.newRumEventWriteOperation(datadogContext, writeScope, writer) {
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
                context = ErrorEvent.Context(additionalProperties = getCustomAttributes().toMutableMap()),
                dd = ErrorEvent.Dd(
                    session = ErrorEvent.DdSession(
                        sessionPrecondition = rumContext.sessionStartReason.toErrorSessionPrecondition()
                    ),
                    configuration = ErrorEvent.Configuration(sessionSampleRate = sampleRate)
                ),
                service = datadogContext.service,
                version = datadogContext.version,
                buildVersion = datadogContext.versionCode,
                ddtags = buildDDTagsString(datadogContext)
            )
        }
            .onError {
                it.eventDropped(
                    rumContext.viewId.orEmpty(),
                    StorageEvent.Error(resourceId, resourceStopTimestampInNanos)
                )
            }
            .onSuccess {
                it.eventSent(rumContext.viewId.orEmpty(), StorageEvent.Error(resourceId, resourceStopTimestampInNanos))
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
        } catch (_: MalformedURLException) {
            url
        }
    }

    private fun resolveGraphQLAttributes(
        operationType: String?,
        operationName: String?,
        variables: String?,
        payload: String?
    ): ResourceEvent.Graphql? {
        operationType?.toOperationType(sdkCore.internalLogger)?.let {
            return ResourceEvent.Graphql(
                operationType = it,
                operationName = operationName,
                variables = variables,
                payload = payload
            )
        }

        return null
    }

    @Suppress("ReturnCount", "SwallowedException")
    private fun String.truncateToUtf8Bytes(maxBytes: Int): String {
        val encoder =
            // will not throw UnsupportedOperationException
            @Suppress("UnsafeThirdPartyFunctionCall")
            StandardCharsets.UTF_8.newEncoder()

        // will not throw IllegalArgumentException
        @Suppress("UnsafeThirdPartyFunctionCall")
        val dst = ByteBuffer.allocate(maxBytes)

        // will not throw IndexOutOfBoundsException
        @Suppress("UnsafeThirdPartyFunctionCall")
        val src = CharBuffer.wrap(this)

        @Suppress("TooGenericExceptionCaught")
        try {
            // Encode as much as fits. The encoder will not consume a character
            // if doing so would overflow the byte buffer.
            encoder.encode(src, dst, true)
        } catch (e: IllegalStateException) {
            logPayloadTruncationFailure(e)
            return ""
        } catch (e: CoderMalfunctionError) {
            logPayloadTruncationFailure(e)
            return ""
        } catch (e: NullPointerException) {
            logPayloadTruncationFailure(e)
            return ""
        }

        // will not throw IndexOutOfBoundsException
        @Suppress("UnsafeThirdPartyFunctionCall")
        return substring(0, src.position())
    }

    private fun logPayloadTruncationFailure(e: Throwable) {
        val logger = sdkCore.internalLogger
        logger.log(
            level = InternalLogger.Level.ERROR,
            target = InternalLogger.Target.MAINTAINER,
            messageBuilder = { "Failed to truncate payload" },
            throwable = e
        )
    }

    // endregion

    companion object {
        internal const val MAX_GRAPHQL_PAYLOAD_SIZE_BYTES = 30 * 1024

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
            networkSettledMetricResolver: NetworkSettledMetricResolver,
            rumSessionTypeOverride: RumSessionType?
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
                networkSettledMetricResolver = networkSettledMetricResolver,
                rumSessionTypeOverride = rumSessionTypeOverride
            )
        }
    }
}
