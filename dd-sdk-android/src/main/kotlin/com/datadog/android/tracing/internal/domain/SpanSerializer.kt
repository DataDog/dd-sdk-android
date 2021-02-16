/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal.domain

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.constraints.DataConstraints
import com.datadog.android.core.internal.constraints.DatadogDataConstraints
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.core.model.NetworkInfo
import com.datadog.android.log.LogAttributes
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.datadog.opentracing.DDSpan
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import java.util.Date

internal class SpanSerializer(
    private val timeProvider: TimeProvider,
    private val networkInfoProvider: NetworkInfoProvider,
    private val userInfoProvider: UserInfoProvider,
    private val envName: String,
    private val dataConstraints: DataConstraints = DatadogDataConstraints()
) : Serializer<DDSpan> {

    // region Serializer

    override fun serialize(model: DDSpan): String {
        val span = serializeSpan(model)
        val spans = JsonArray(1)
        spans.add(span)

        val jsonObject = JsonObject()
        jsonObject.add(TAG_SPANS, spans)
        jsonObject.addProperty(TAG_ENV, envName)

        return jsonObject.toString()
    }

    // endregion

    // region Internal

    private fun serializeSpan(model: DDSpan): JsonObject {

        val serverOffset = timeProvider.getServerOffsetNanos()
        val jsonObject = JsonObject()
        // it is safe to convert BigInteger IDs to Long as they are parsed as Long on the backend
        jsonObject.addProperty(TAG_TRACE_ID, model.traceId.toLong().toString(16))
        jsonObject.addProperty(TAG_SPAN_ID, model.spanId.toLong().toString(16))
        jsonObject.addProperty(TAG_PARENT_ID, model.parentId.toLong().toString(16))
        jsonObject.addProperty(TAG_RESOURCE, model.resourceName)
        jsonObject.addProperty(TAG_OPERATION_NAME, model.operationName)
        jsonObject.addProperty(TAG_SERVICE_NAME, model.serviceName)
        jsonObject.addProperty(TAG_DURATION, model.durationNano)
        jsonObject.addProperty(TAG_START_TIMESTAMP, model.startTime + serverOffset)
        jsonObject.addProperty(TAG_ERROR, if (model.isError) 1 else 0)
        jsonObject.addProperty(TAG_TYPE, TYPE_CUSTOM)
        addMeta(jsonObject, model)
        addMetrics(jsonObject, model)
        return jsonObject
    }

    private fun addMeta(jsonObject: JsonObject, model: DDSpan) {
        val metaObject = JsonObject()
        model.meta.forEach {
            metaObject.addProperty(it.key, it.value)
        }

        metaObject.addProperty(TAG_DD_SOURCE, DD_SOURCE_ANDROID)
        metaObject.addProperty(TAG_SPAN_KIND, KIND_CLIENT)
        metaObject.addProperty(TAG_TRACER_VERSION, BuildConfig.VERSION_NAME)
        metaObject.addProperty(TAG_APPLICATION_VERSION, CoreFeature.packageVersion)

        addLogNetworkInfo(networkInfoProvider.getLatestNetworkInfo(), metaObject)
        addLogUserInfo(userInfoProvider.getUserInfo(), metaObject)

        jsonObject.add(TAG_META, metaObject)
    }

    private fun addMetrics(jsonObject: JsonObject, model: DDSpan) {
        val metricsObject = JsonObject()
        model.metrics.forEach {
            metricsObject.addProperty(it.key, it.value)
        }
        if (model.parentId.toLong() == 0L) {
            // mark this span as top level
            metricsObject.addProperty(TAG_METRICS_TOP_LEVEL, 1)
        }
        jsonObject.add(TAG_METRICS, metricsObject)
    }

    private fun addLogNetworkInfo(
        networkInfo: NetworkInfo?,
        jsonLog: JsonObject
    ) {
        if (networkInfo != null) {
            jsonLog.add(
                LogAttributes.NETWORK_CONNECTIVITY,
                networkInfo.connectivity.toJson()
            )
            if (!networkInfo.carrierName.isNullOrBlank()) {
                jsonLog.addProperty(
                    LogAttributes.NETWORK_CARRIER_NAME,
                    networkInfo.carrierName
                )
            }
            if (networkInfo.carrierId >= 0) {
                jsonLog.addProperty(
                    LogAttributes.NETWORK_CARRIER_ID,
                    networkInfo.carrierId.toString()
                )
            }
            if (networkInfo.upKbps >= 0) {
                jsonLog.addProperty(
                    LogAttributes.NETWORK_UP_KBPS,
                    networkInfo.upKbps.toString()
                )
            }
            if (networkInfo.downKbps >= 0) {
                jsonLog.addProperty(
                    LogAttributes.NETWORK_DOWN_KBPS,
                    networkInfo.downKbps.toString()
                )
            }
            if (networkInfo.strength > Int.MIN_VALUE) {
                jsonLog.addProperty(
                    LogAttributes.NETWORK_SIGNAL_STRENGTH,
                    networkInfo.strength.toString()
                )
            }
        }
    }

    private fun addLogUserInfo(userInfo: UserInfo, jsonLog: JsonObject) {
        if (!userInfo.id.isNullOrEmpty()) {
            jsonLog.addProperty(LogAttributes.USR_ID, userInfo.id)
        }
        if (!userInfo.name.isNullOrEmpty()) {
            jsonLog.addProperty(LogAttributes.USR_NAME, userInfo.name)
        }
        if (!userInfo.email.isNullOrEmpty()) {
            jsonLog.addProperty(LogAttributes.USR_EMAIL, userInfo.email)
        }
        // add extra attributes
        dataConstraints.validateAttributes(
            userInfo.extraInfo,
            keyPrefix = LogAttributes.USR_ATTRIBUTES_GROUP,
            attributesGroupName = USER_EXTRA_GROUP_VERBOSE_NAME
        ).forEach {
            val key = "${LogAttributes.USR_ATTRIBUTES_GROUP}.${it.key}"
            toMetaString(it.value)?.apply {
                jsonLog.addProperty(key, this)
            }
        }
    }

    private fun toMetaString(element: Any?): String? {
        return when (element) {
            NULL_MAP_VALUE -> null
            null -> null
            is Date -> element.time.toString()
            is JsonPrimitive -> element.asString
            else -> element.toString()
        }
    }

    // endregion

    companion object {

        internal const val TYPE_CUSTOM = "custom"
        internal const val DD_SOURCE_ANDROID = "android"
        internal const val KIND_CLIENT = "client"

        // PAYLOAD TAGS
        internal const val TAG_SPANS = "spans"
        internal const val TAG_ENV = "env"

        // SPAN TAGS
        internal const val TAG_START_TIMESTAMP = "start"
        internal const val TAG_DURATION = "duration"
        internal const val TAG_SERVICE_NAME = "service"
        internal const val TAG_APPLICATION_VERSION = "version"
        internal const val TAG_TRACE_ID = "trace_id"
        internal const val TAG_SPAN_ID = "span_id"
        internal const val TAG_PARENT_ID = "parent_id"
        internal const val TAG_RESOURCE = "resource"
        internal const val TAG_OPERATION_NAME = "name"
        internal const val TAG_ERROR = "error"
        internal const val TAG_TYPE = "type"
        internal const val TAG_META = "meta"
        internal const val TAG_METRICS = "metrics"
        internal const val TAG_METRICS_TOP_LEVEL = "_top_level"
        internal const val TAG_DD_SOURCE = "_dd.source"
        internal const val TAG_SPAN_KIND = "span.kind"
        internal const val TAG_TRACER_VERSION = "tracer.version"

        internal const val USER_EXTRA_GROUP_VERBOSE_NAME = "user extra information"
    }
}
