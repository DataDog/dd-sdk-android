/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.utils.assertj

import com.datadog.android.rum.model.ResourceEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat

internal class DeserializedResourceEventAssert(actual: ResourceEvent) :
    AbstractAssert<DeserializedResourceEventAssert, ResourceEvent>(
        actual,
        DeserializedResourceEventAssert::class.java
    ) {

    fun isEqualTo(expected: ResourceEvent): DeserializedResourceEventAssert {
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
        fun assertThat(actual: ResourceEvent): DeserializedResourceEventAssert {
            return DeserializedResourceEventAssert(actual)
        }
    }
}
