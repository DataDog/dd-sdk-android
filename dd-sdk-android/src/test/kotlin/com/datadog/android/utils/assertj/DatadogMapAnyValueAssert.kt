/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.google.gson.JsonElement
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class DatadogMapAnyValueAssert(actual: Map<String, Any?>) :
    AbstractAssert<DatadogMapAnyValueAssert, Map<String, Any?>>(
        actual,
        DatadogMapAnyValueAssert::class.java
    ) {

    fun isEqualTo(expectedMap: Map<String, Any?>): DatadogMapAnyValueAssert {
        assertThat(actual.size).overridingErrorMessage(
            "We were expecting a map size: %d, actual it was: %d",
            expectedMap.size,
            actual.size
        ).isEqualTo(expectedMap.size)
        actual.forEach {
            val expectedValue = expectedMap[it.key]
            val currentValue = it.value
            if (currentValue is JsonElement) {
                DatadogJsonElementAssert.assertThat(currentValue).isEqualTo(expectedValue)
            } else {
                assertThat(currentValue).isEqualTo(expectedValue)
            }
        }

        return this
    }

    companion object {
        fun assertThat(actual: Map<String, Any?>): DatadogMapAnyValueAssert {
            return DatadogMapAnyValueAssert(actual)
        }
    }
}
