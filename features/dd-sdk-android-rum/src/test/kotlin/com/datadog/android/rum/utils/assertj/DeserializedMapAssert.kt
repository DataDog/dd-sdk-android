/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.rum.utils.assertj.JsonElementAssert.Companion.assertThat
import com.google.gson.JsonElement
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import java.util.Date

// TODO RUMM-2949 Share forgeries/test configurations between modules
internal class DeserializedMapAssert(actual: Map<String, Any?>) :
    AbstractAssert<DeserializedMapAssert, Map<String, Any?>>(
        actual,
        DeserializedMapAssert::class.java
    ) {

    fun isEqualTo(expectedMap: Map<String, Any?>): DeserializedMapAssert {
        assertThat(actual.size).overridingErrorMessage(
            "We were expecting a map size: %d, actual it was: %d",
            expectedMap.size,
            actual.size
        ).isEqualTo(expectedMap.size)
        actual.forEach {
            val expectedValue = expectedMap[it.key]
            val currentValue = it.value
            if (currentValue is JsonElement) {
                assertThat(currentValue).isEqualTo(expectedValue)
            } else if (currentValue is Number) {
                when (expectedValue) {
                    is Long -> assertThat(currentValue.toLong()).isEqualTo(expectedValue)
                    is Double -> assertThat(currentValue.toDouble()).isEqualTo(expectedValue)
                    is Float -> assertThat(currentValue.toFloat()).isEqualTo(expectedValue)
                    is Byte -> assertThat(currentValue.toByte()).isEqualTo(expectedValue)
                    is Int -> assertThat(currentValue.toInt()).isEqualTo(expectedValue)
                    is Date -> assertThat(currentValue.toLong()).isEqualTo(expectedValue.time)
                    else -> fail("Unable to compare <$currentValue> with <$expectedValue>")
                }
            } else if (currentValue is String) {
                assertThat(currentValue).isEqualTo(expectedValue.toString())
            } else {
                assertThat(currentValue).isEqualTo(expectedValue)
            }
        }

        return this
    }

    companion object {
        fun assertThat(actual: Map<String, Any?>): DeserializedMapAssert {
            return DeserializedMapAssert(actual)
        }
    }
}
