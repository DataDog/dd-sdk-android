/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.rum.model.ErrorEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class DeserializedErrorEventAssert(actual: ErrorEvent) :
    AbstractAssert<DeserializedErrorEventAssert, ErrorEvent>(
        actual,
        DeserializedErrorEventAssert::class.java
    ) {

    fun isEqualTo(expected: ErrorEvent): DeserializedErrorEventAssert {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("context", "usr")
            .isEqualTo(expected)
        assertThat(actual.usr)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected.usr)
        assertProperties(
            actual.usr?.additionalProperties,
            expected.usr?.additionalProperties
        )
        assertThat(actual.context)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected.context)
        assertProperties(
            actual.context?.additionalProperties,
            expected.context?.additionalProperties
        )
        return this
    }

    private fun assertProperties(actual: Map<String, Any?>?, expected: Map<String, Any?>?) {
        DeserializedMapAssert.assertThat(actual ?: emptyMap())
            .isEqualTo(expected ?: emptyMap())
    }

    companion object {
        fun assertThat(actual: ErrorEvent): DeserializedErrorEventAssert {
            return DeserializedErrorEventAssert(actual)
        }
    }
}
