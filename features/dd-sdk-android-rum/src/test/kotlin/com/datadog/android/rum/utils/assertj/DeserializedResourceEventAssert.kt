/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.rum.model.ResourceEvent
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class DeserializedResourceEventAssert(actual: ResourceEvent) :
    AbstractAssert<DeserializedResourceEventAssert, ResourceEvent>(
        actual,
        DeserializedResourceEventAssert::class.java
    ) {

    fun isEqualTo(expected: ResourceEvent): DeserializedResourceEventAssert {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("context", "usr", "account", "device")
            .isEqualTo(expected)
        assertThat(actual.device)
            .usingRecursiveComparison()
            .ignoringFields("batteryLevel", "brightnessLevel", "totalRam", "logicalCpuCount")
            .isEqualTo(expected.device)
        assertNumberFieldEquals(actual.device?.batteryLevel, expected.device?.batteryLevel)
        assertNumberFieldEquals(actual.device?.brightnessLevel, expected.device?.brightnessLevel)
        assertThat(actual.usr)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected.usr)
        assertThat(actual.account)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected.account)
        assertProperties(
            actual.usr?.additionalProperties,
            expected.usr?.additionalProperties
        )
        assertProperties(
            actual.account?.additionalProperties,
            expected.account?.additionalProperties
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

    private fun assertNumberFieldEquals(actual: Number?, expected: Number?) {
        if (expected == null) {
            assertThat(actual).isNull()
        } else {
            assertThat(actual).isNotNull()
            assertThat(actual!!.toDouble())
                .isCloseTo(expected.toDouble(), Offset.offset(0.0000001))
        }
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
