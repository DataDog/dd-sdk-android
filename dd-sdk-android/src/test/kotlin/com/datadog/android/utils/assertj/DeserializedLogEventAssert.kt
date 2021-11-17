/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.log.model.LogEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class DeserializedLogEventAssert(actual: LogEvent) :
    AbstractAssert<DeserializedLogEventAssert, LogEvent>(
        actual,
        DeserializedLogEventAssert::class.java
    ) {

    fun isEqualTo(expected: LogEvent): DeserializedLogEventAssert {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("usr", "additionalProperties")
            .isEqualTo(expected)
        assertThat(actual.usr)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected.usr)
        assertProperties(
            actual.usr?.additionalProperties,
            expected.usr?.additionalProperties
        )
        assertProperties(
            actual.additionalProperties,
            expected.additionalProperties
        )
        return this
    }

    private fun assertProperties(actual: Map<String, Any?>?, expected: Map<String, Any?>?) {
        DeserializedMapAssert.assertThat(actual ?: emptyMap())
            .isEqualTo(expected ?: emptyMap())
    }

    companion object {
        fun assertThat(actual: LogEvent): DeserializedLogEventAssert {
            return DeserializedLogEventAssert(actual)
        }
    }
}
