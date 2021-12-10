/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.log.model.WebLogEvent
import org.assertj.core.api.AbstractAssert

internal class DeserializedWebLogEventAssert(actual: WebLogEvent) :
    AbstractAssert<DeserializedWebLogEventAssert, WebLogEvent>(
        actual,
        DeserializedWebLogEventAssert::class.java
    ) {

    fun isEqualTo(expected: WebLogEvent): DeserializedWebLogEventAssert {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected)
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
        fun assertThat(actual: WebLogEvent): DeserializedWebLogEventAssert {
            return DeserializedWebLogEventAssert(actual)
        }
    }
}
