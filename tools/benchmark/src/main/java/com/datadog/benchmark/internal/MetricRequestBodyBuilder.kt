/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.benchmark.internal

import com.datadog.benchmark.internal.model.MetricContext
import com.datadog.benchmark.internal.model.MetricType
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.resources.Resource
import java.util.concurrent.TimeUnit

internal class MetricRequestBodyBuilder(private val metricContext: MetricContext) : RequestBodyBuilder<MetricData> {

    private val gson = GsonBuilder().create()

    override fun buildJsonElement(data: List<MetricData>): JsonElement {
        return gson.toJsonTree(resolveMetrics(data))
    }

    private fun resolveMetrics(metrics: List<MetricData>): Map<String, Any> {
        return mapOf(
            KEY_SERIES to metrics.map {
                resolveMetricData(it)
            }
        )
    }

    private fun resolveMetricData(metric: MetricData): Map<String, Any> {
        return mapOf(
            // only available for rate or count metric
            KEY_INTERVAL to metricContext.intervalInSeconds,
            KEY_METRIC to metric.name,
            KEY_POINTS to resolvePoints(metric),
            KEY_RESOURCES to resolveResources(metric.resource),
            KEY_TAGS to resolveTags(),
            KEY_TYPE to resolveMetricType(metric.type).value,
            KEY_UNIT to metric.unit
        )
    }

    private fun resolveMetricType(type: MetricDataType): MetricType {
        return when (type) {
            MetricDataType.LONG_GAUGE, MetricDataType.DOUBLE_GAUGE -> MetricType.GAUGE
            MetricDataType.LONG_SUM, MetricDataType.DOUBLE_SUM -> MetricType.COUNT
            else -> MetricType.UNSPECIFIED
        }
    }

    private fun resolveTags(): List<String> {
        return listOf(
            "$KEY_TAG_DEVICE_MODEL:${metricContext.deviceModel}",
            "$KEY_TAG_OS_VERSION:${metricContext.osVersion}",
            "$KEY_TAG_RUN:${metricContext.run}",
            "$KEY_TAG_APPLICATION_ID:${metricContext.applicationId}"
        )
    }

    private fun resolvePoints(metric: MetricData): List<Map<String, Any>> {
        return metric.data.points.map {
            val value = resolveValue(it)
            mapOf(
                KEY_TIMESTAMP to TimeUnit.NANOSECONDS.toSeconds(it.startEpochNanos),
                KEY_VALUE to value
            )
        }
    }

    private fun resolveValue(pointData: PointData): Double {
        return when (pointData) {
            is DoublePointData -> pointData.value
            is LongPointData -> pointData.value.toDouble()
            else -> 0.0
        }
    }

    private fun resolveResources(resource: Resource): List<Map<String, Any>> {
        val name = resource.attributes.get(AttributeKey.stringKey(RESOURCE_ATTRIBUTE_NAME_KEY))
        return listOfNotNull(
            name?.let {
                mapOf(KEY_RESOURCES_NAME to it, KEY_RESOURCES_TYPE to "")
            }

        )
    }

    companion object {

        private const val RESOURCE_ATTRIBUTE_NAME_KEY = "service.name"

        private const val KEY_SERIES = "series"
        private const val KEY_INTERVAL = "interval"
        private const val KEY_METRIC = "metric"
        private const val KEY_TAGS = "tags"
        private const val KEY_POINTS = "points"
        private const val KEY_RESOURCES = "resources"
        private const val KEY_TYPE = "type"
        private const val KEY_UNIT = "unit"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_VALUE = "value"
        private const val KEY_RESOURCES_NAME = "name"
        private const val KEY_RESOURCES_TYPE = "type"
        private const val KEY_TAG_DEVICE_MODEL = "device_model"
        private const val KEY_TAG_OS_VERSION = "os_version"
        private const val KEY_TAG_RUN = "run"
        private const val KEY_TAG_APPLICATION_ID = "application_id"
    }
}
