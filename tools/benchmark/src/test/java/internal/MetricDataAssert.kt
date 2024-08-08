/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package internal

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions

class MetricDataAssert(actual: JsonObject) : JsonObjectAssert(actual, true) {

    fun hasMetric(metric: String): MetricDataAssert {
        hasField("metric", metric)
        return this
    }

    fun hasTags(tags: List<String>): MetricDataAssert {
        hasField("tags", tags)
        return this
    }

    fun hasMetricType(type: Int): MetricDataAssert {
        hasField("type", type)
        return this
    }

    fun hasPoints(size: Int, pointAssert: PointAssert.(Int) -> Unit) {
        Assertions.assertThat(actual.get("points").isJsonArray).isTrue()
        val jsonArray = actual.getAsJsonArray("points")
        Assertions.assertThat(jsonArray.size()).isEqualTo(size)
        for (index in 0 until size) {
            PointAssert(jsonArray[index].asJsonObject).pointAssert(index)
        }
    }

    fun hasUnit(unit: String): MetricDataAssert {
        hasField("unit", unit)
        return this
    }

    companion object {
        fun assertThat(actual: JsonObject): MetricDataAssert {
            return MetricDataAssert(actual)
        }
    }
}
