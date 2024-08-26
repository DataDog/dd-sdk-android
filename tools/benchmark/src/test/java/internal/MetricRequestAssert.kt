/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package internal

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.JsonObject
import org.assertj.core.api.Assertions

class MetricRequestAssert(actual: JsonObject) : JsonObjectAssert(actual, true) {

    fun hasMetricDataArray(fieldName: String, size: Int, assert: MetricDataAssert.(Int) -> Unit): MetricRequestAssert {
        val element = actual.get(fieldName)
        Assertions.assertThat(element.isJsonArray).isTrue()
        val jsonArray = element.asJsonArray
        Assertions.assertThat(jsonArray.size()).isEqualTo(size)
        for (index in 0 until size) {
            MetricDataAssert.assertThat(jsonArray.get(index).asJsonObject).assert(index)
        }
        return this
    }

    companion object {
        fun assertThat(actual: JsonObject): MetricRequestAssert {
            return MetricRequestAssert(actual)
        }
    }
}
