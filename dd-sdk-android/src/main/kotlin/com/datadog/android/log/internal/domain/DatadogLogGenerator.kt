/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain

import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.core.model.UserInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.utils.buildLogDateFormat
import com.datadog.android.log.model.LogEvent
import com.datadog.android.rum.internal.RumFeature
import com.datadog.android.v2.api.context.DatadogContext
import io.opentracing.util.GlobalTracer
import java.util.Date

@Suppress("TooManyFunctions")
internal class DatadogLogGenerator(
    /**
     * Custom service name. If not provided, value will be taken from [DatadogContext].
     */
    internal val serviceName: String? = null
) : LogGenerator {

    private val simpleDateFormat = buildLogDateFormat()

    @Suppress("LongParameterList")
    override fun generateLog(
        level: Int,
        message: String,
        throwable: Throwable?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        datadogContext: DatadogContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        networkInfo: NetworkInfo?
    ): LogEvent {
        val error = throwable?.let {
            val kind = it.javaClass.canonicalName ?: it.javaClass.simpleName
            LogEvent.Error(kind = kind, stack = it.stackTraceToString(), message = it.message)
        }
        return internalGenerateLog(
            level,
            message,
            error,
            attributes,
            tags,
            timestamp,
            threadName,
            datadogContext,
            attachNetworkInfo,
            loggerName,
            bundleWithTraces,
            bundleWithRum,
            userInfo,
            networkInfo
        )
    }

    override fun generateLog(
        level: Int,
        message: String,
        errorKind: String?,
        errorMessage: String?,
        errorStack: String?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        datadogContext: DatadogContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        networkInfo: NetworkInfo?
    ): LogEvent {
        val error = if (errorKind != null || errorMessage != null || errorStack != null) {
            LogEvent.Error(kind = errorKind, message = errorMessage, stack = errorStack)
        } else {
            null
        }
        return internalGenerateLog(
            level,
            message,
            error,
            attributes,
            tags,
            timestamp,
            threadName,
            datadogContext,
            attachNetworkInfo,
            loggerName,
            bundleWithTraces,
            bundleWithRum,
            userInfo,
            networkInfo
        )
    }

    // region Internal

    @Suppress("LongParameterList")
    private fun internalGenerateLog(
        level: Int,
        message: String,
        error: LogEvent.Error?,
        attributes: Map<String, Any?>,
        tags: Set<String>,
        timestamp: Long,
        threadName: String,
        datadogContext: DatadogContext,
        attachNetworkInfo: Boolean,
        loggerName: String,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean,
        userInfo: UserInfo?,
        networkInfo: NetworkInfo?
    ): LogEvent {
        val resolvedTimestamp = timestamp + datadogContext.time.serverTimeOffsetMs
        val combinedAttributes =
            resolveAttributes(datadogContext, attributes, bundleWithTraces, bundleWithRum)
        val formattedDate = synchronized(simpleDateFormat) {
            @Suppress("UnsafeThirdPartyFunctionCall") // NPE cannot happen here
            simpleDateFormat.format(Date(resolvedTimestamp))
        }
        val combinedTags = resolveTags(datadogContext, tags)
        val usr = resolveUserInfo(datadogContext, userInfo)
        val network = if (networkInfo != null || attachNetworkInfo) {
            resolveNetworkInfo(datadogContext, networkInfo)
        } else {
            null
        }
        val loggerInfo = LogEvent.Logger(
            name = loggerName,
            threadName = threadName,
            version = datadogContext.sdkVersion
        )
        return LogEvent(
            service = serviceName ?: datadogContext.service,
            status = resolveLogLevelStatus(level),
            message = message,
            date = formattedDate,
            error = error,
            logger = loggerInfo,
            dd = LogEvent.Dd(
                device = LogEvent.Device(
                    architecture = datadogContext.deviceInfo.architecture
                )
            ),
            usr = usr,
            network = network,
            ddtags = combinedTags.joinToString(separator = ","),
            additionalProperties = combinedAttributes
        )
    }

    private fun envTag(datadogContext: DatadogContext): String? {
        val envName = datadogContext.env
        return if (envName.isNotEmpty()) {
            "${LogAttributes.ENV}:$envName"
        } else {
            null
        }
    }

    private fun appVersionTag(datadogContext: DatadogContext): String? {
        val appVersion = datadogContext.version
        return if (appVersion.isNotEmpty()) {
            "${LogAttributes.APPLICATION_VERSION}:$appVersion"
        } else {
            null
        }
    }

    private fun variantTag(datadogContext: DatadogContext): String? {
        val variant = datadogContext.variant
        return if (variant.isNotEmpty()) {
            "${LogAttributes.VARIANT}:$variant"
        } else {
            null
        }
    }

    private fun resolveNetworkInfo(
        datadogContext: DatadogContext,
        networkInfo: NetworkInfo?
    ): LogEvent.Network {
        // TODO RUMM-0000 use V2 (RUM should write V2 version)
        return if (networkInfo != null) {
            LogEvent.Network(
                LogEvent.Client(
                    simCarrier = resolveSimCarrier(networkInfo),
                    signalStrength = networkInfo.strength?.toString(),
                    downlinkKbps = networkInfo.downKbps?.toString(),
                    uplinkKbps = networkInfo.upKbps?.toString(),
                    connectivity = networkInfo.connectivity.toString()
                )
            )
        } else {
            LogEvent.Network(
                LogEvent.Client(
                    simCarrier = resolveSimCarrier(datadogContext.networkInfo),
                    signalStrength = datadogContext.networkInfo.strength?.toString(),
                    downlinkKbps = datadogContext.networkInfo.downKbps?.toString(),
                    uplinkKbps = datadogContext.networkInfo.upKbps?.toString(),
                    connectivity = datadogContext.networkInfo.connectivity.toString()
                )
            )
        }
    }

    private fun resolveUserInfo(datadogContext: DatadogContext, userInfo: UserInfo?): LogEvent.Usr {
        // TODO RUMM-0000 use V2 (RUM should write V2 version)
        return if (userInfo != null) {
            LogEvent.Usr(
                name = userInfo.name,
                email = userInfo.email,
                id = userInfo.id,
                additionalProperties = userInfo.additionalProperties
            )
        } else {
            with(datadogContext.userInfo) {
                LogEvent.Usr(
                    name = name,
                    email = email,
                    id = id,
                    additionalProperties = additionalProperties.toMutableMap()
                )
            }
        }
    }

    private fun resolveTags(
        datadogContext: DatadogContext,
        tags: Set<String>
    ): MutableSet<String> {
        val combinedTags = mutableSetOf<String>().apply { addAll(tags) }
        envTag(datadogContext)?.let {
            combinedTags.add(it)
        }
        appVersionTag(datadogContext)?.let {
            combinedTags.add(it)
        }
        variantTag(datadogContext)?.let {
            combinedTags.add(it)
        }

        return combinedTags
    }

    private fun resolveAttributes(
        datadogContext: DatadogContext,
        attributes: Map<String, Any?>,
        bundleWithTraces: Boolean,
        bundleWithRum: Boolean
    ): MutableMap<String, Any?> {
        val combinedAttributes = mutableMapOf<String, Any?>().apply { putAll(attributes) }
        if (bundleWithTraces && GlobalTracer.isRegistered()) {
            val tracer = GlobalTracer.get()
            val activeContext = tracer.activeSpan()?.context()
            if (activeContext != null) {
                combinedAttributes[LogAttributes.DD_TRACE_ID] = activeContext.toTraceId()
                combinedAttributes[LogAttributes.DD_SPAN_ID] = activeContext.toSpanId()
            }
        }
        if (bundleWithRum) {
            datadogContext.featuresContext[RumFeature.RUM_FEATURE_NAME]?.let {
                combinedAttributes[LogAttributes.RUM_APPLICATION_ID] = it["application_id"]
                combinedAttributes[LogAttributes.RUM_SESSION_ID] = it["session_id"]
                combinedAttributes[LogAttributes.RUM_VIEW_ID] = it["view_id"]
                combinedAttributes[LogAttributes.RUM_ACTION_ID] = it["action_id"]
            }
        }
        return combinedAttributes
    }

    @Suppress("DEPRECATION")
    private fun resolveLogLevelStatus(level: Int): LogEvent.Status {
        return when (level) {
            android.util.Log.ASSERT -> LogEvent.Status.CRITICAL
            android.util.Log.ERROR -> LogEvent.Status.ERROR
            android.util.Log.WARN -> LogEvent.Status.WARN
            android.util.Log.INFO -> LogEvent.Status.INFO
            android.util.Log.DEBUG -> LogEvent.Status.DEBUG
            android.util.Log.VERBOSE -> LogEvent.Status.TRACE
            DatadogLogGenerator.CRASH -> LogEvent.Status.EMERGENCY
            else -> LogEvent.Status.DEBUG
        }
    }

    private fun resolveSimCarrier(networkInfo: NetworkInfo): LogEvent.SimCarrier? {
        return if (networkInfo.carrierId != null || networkInfo.carrierName != null) {
            LogEvent.SimCarrier(
                id = networkInfo.carrierId?.toString(),
                name = networkInfo.carrierName
            )
        } else {
            null
        }
    }

    private fun resolveSimCarrier(networkInfo: com.datadog.android.v2.api.context.NetworkInfo):
        LogEvent.SimCarrier? {
        return if (networkInfo.carrierId != null || networkInfo.carrierName != null) {
            LogEvent.SimCarrier(
                id = networkInfo.carrierId?.toString(),
                name = networkInfo.carrierName
            )
        } else {
            null
        }
    }

    // endregion

    companion object {
        internal const val ISO_8601 = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
        internal const val CRASH: Int = 9
    }
}
