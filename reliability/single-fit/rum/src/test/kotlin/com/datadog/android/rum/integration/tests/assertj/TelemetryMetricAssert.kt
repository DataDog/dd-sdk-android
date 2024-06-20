/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.integration.tests.assertj

import com.datadog.tools.unit.assertj.JsonObjectAssert
import com.google.gson.Gson
import com.google.gson.JsonObject

class TelemetryMetricAssert(actual: JsonObject) : JsonObjectAssert(actual, true) {

    fun hasWasStopped(wasStopped: Boolean): TelemetryMetricAssert {
        hasField("additionalProperties.rse.was_stopped", wasStopped)
        return this
    }

    fun hasViewCount(viewCount: Int): TelemetryMetricAssert {
        hasField("additionalProperties.rse.views_count.total", viewCount)
        return this
    }

    companion object {
        fun assertThat(actual: Map<*, *>?): TelemetryMetricAssert {
            return TelemetryMetricAssert(Gson().toJsonTree(actual).asJsonObject)
        }
    }
}
