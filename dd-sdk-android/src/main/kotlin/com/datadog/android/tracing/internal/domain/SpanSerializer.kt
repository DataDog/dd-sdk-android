package com.datadog.android.tracing.internal.domain

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
        jsonObject.addProperty(TRACE_ID_KEY, model.traceId.toLong().toString(16))
        jsonObject.addProperty(SPAN_ID_KEY, model.spanId.toLong().toString(16))
        jsonObject.addProperty(PARENT_ID_KEY, model.parentId.toLong().toString(16))
        jsonObject.addProperty(RESOURCE_KEY, model.resourceName)
        jsonObject.addProperty(OPERATION_NAME_KEY, model.operationName)
        jsonObject.addProperty(SERVICE_NAME_KEY, model.serviceName)
        jsonObject.addProperty(DURATION_KEY, model.durationNano)
        jsonObject.addProperty(START_TIMESTAMP_KEY, model.startTime + serverOffset)
        jsonObject.addProperty(TYPE_KEY, "object") // do not know yet what should be here
        addMeta(jsonObject, model)
        addMetrics(jsonObject, model)
        return jsonObject.toString()
    }

    private fun addMeta(jsonObject: JsonObject, model: DDSpan) {
        val metaObject = JsonObject()
        model.meta.forEach {
            metaObject.addProperty(it.key, it.value)
        }

        addLogNetworkInfo(networkInfoProvider.getLatestNetworkInfo(), metaObject)
        addLogUserInfo(userInfoProvider.getUserInfo(), metaObject)

        jsonObject.add(META_KEY, metaObject)
    }

    private fun addMetrics(jsonObject: JsonObject, model: DDSpan) {
        val metricsObject = JsonObject()
        model.metrics.forEach {
            metricsObject.addProperty(it.key, it.value)
        }
        // For now disable the sampling on server
        metricsObject.addProperty(METRICS_KEY_SAMPLING, 1)

        if (model.parentId.toLong() == 0L) {
            // mark this span as top level
            metricsObject.addProperty(METRICS_KEY_TOP_LEVEL, 1)
        }
        jsonObject.add(METRICS_KEY, metricsObject)
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
        const val START_TIMESTAMP_KEY = "start"
        const val DURATION_KEY = "duration"
        const val SERVICE_NAME_KEY = "service"
        const val TRACE_ID_KEY = "trace_id"
        const val SPAN_ID_KEY = "span_id"
        const val PARENT_ID_KEY = "parent_id"
        const val RESOURCE_KEY = "resource"
        const val OPERATION_NAME_KEY = "name"
        const val TYPE_KEY = "type"
        const val META_KEY = "meta"
        const val METRICS_KEY = "metrics"
        const val METRICS_KEY_TOP_LEVEL = "_top_level"
        const val METRICS_KEY_SAMPLING = "_sampling_priority_v1"

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
