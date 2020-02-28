package com.datadog.android.tracing.internal.domain

import com.datadog.android.BuildConfig
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.domain.Serializer
import com.datadog.android.core.internal.net.info.NetworkInfo
import com.datadog.android.core.internal.net.info.NetworkInfoProvider
import com.datadog.android.core.internal.time.TimeProvider
import com.datadog.android.log.internal.user.UserInfo
import com.datadog.android.log.internal.user.UserInfoProvider
import com.google.gson.JsonObject
import datadog.opentracing.DDSpan

internal class SpanSerializer(
    private val timeProvider: TimeProvider,
    private val networkInfoProvider: NetworkInfoProvider,
    private val userInfoProvider: UserInfoProvider
) : Serializer<DDSpan> {

    override fun serialize(model: DDSpan): String {
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
        return jsonObject.toString()
    }

    private fun addMeta(jsonObject: JsonObject, model: DDSpan) {
        val metaObject = JsonObject()
        model.meta.forEach {
            metaObject.addProperty(it.key, it.value)
        }

        metaObject.addProperty(TAG_DD_SOURCE, DD_SOURCE_MOBILE)
        metaObject.addProperty(TAG_VERSION_NAME, BuildConfig.VERSION_NAME)
        metaObject.addProperty(TAG_APP_VERSION_NAME, CoreFeature.packageVersion)
        metaObject.addProperty(TAG_APP_PACKAGE_NAME, CoreFeature.packageName)
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
            jsonLog.addProperty(TAG_NETWORK_CONNECTIVITY, networkInfo.connectivity.serialized)
            if (!networkInfo.carrierName.isNullOrBlank()) {
                jsonLog.addProperty(TAG_NETWORK_CARRIER_NAME, networkInfo.carrierName)
            }
            if (networkInfo.carrierId >= 0) {
                jsonLog.addProperty(TAG_NETWORK_CARRIER_ID, networkInfo.carrierId.toString())
            }
            if (networkInfo.upKbps >= 0) {
                jsonLog.addProperty(TAG_NETWORK_UP_KBPS, networkInfo.upKbps.toString())
            }
            if (networkInfo.downKbps >= 0) {
                jsonLog.addProperty(TAG_NETWORK_DOWN_KBPS, networkInfo.downKbps.toString())
            }
            if (networkInfo.strength > Int.MIN_VALUE) {
                jsonLog.addProperty(TAG_NETWORK_SIGNAL_STRENGTH, networkInfo.strength.toString())
            }
        }
    }

    private fun addLogUserInfo(userInfo: UserInfo, jsonLog: JsonObject) {
        if (!userInfo.id.isNullOrEmpty()) {
            jsonLog.addProperty(TAG_USER_ID, userInfo.id)
        }
        if (!userInfo.name.isNullOrEmpty()) {
            jsonLog.addProperty(TAG_USER_NAME, userInfo.name)
        }
        if (!userInfo.email.isNullOrEmpty()) {
            jsonLog.addProperty(TAG_USER_EMAIL, userInfo.email)
        }
    }

    companion object {

        internal const val TYPE_CUSTOM = "custom"
        internal const val DD_SOURCE_MOBILE = "mobile"

        // SPAN TAGS
        internal const val TAG_START_TIMESTAMP = "start"
        internal const val TAG_DURATION = "duration"
        internal const val TAG_SERVICE_NAME = "service"
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

        // GLOBAL TAGS
        internal const val TAG_VERSION_NAME = "logger.version"
        internal const val TAG_APP_VERSION_NAME = "application.version"
        internal const val TAG_APP_PACKAGE_NAME = "application.package"

        // NETWORK TAGS
        internal const val TAG_NETWORK_CONNECTIVITY = "network.client.connectivity"
        internal const val TAG_NETWORK_CARRIER_NAME = "network.client.sim_carrier.name"
        internal const val TAG_NETWORK_CARRIER_ID = "network.client.sim_carrier.id"
        internal const val TAG_NETWORK_UP_KBPS = "network.client.uplink_kbps"
        internal const val TAG_NETWORK_DOWN_KBPS = "network.client.downlink_kbps"
        internal const val TAG_NETWORK_SIGNAL_STRENGTH = "network.client.signal_strength"

        // USER TAGS
        internal const val TAG_USER_ID = "usr.id"
        internal const val TAG_USER_NAME = "usr.name"
        internal const val TAG_USER_EMAIL = "usr.email"
    }
}
