/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.utils.assertj

import com.datadog.android.rum.model.ViewUpdateEvent
import com.datadog.tools.unit.assertj.withGsonIntEqualsForFields
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset

internal class DeserializedViewUpdateEventAssert(actual: ViewUpdateEvent) :
    AbstractAssert<DeserializedViewUpdateEventAssert, ViewUpdateEvent>(
        actual,
        DeserializedViewUpdateEventAssert::class.java
    ) {

    fun isEqualTo(expected: ViewUpdateEvent): DeserializedViewUpdateEventAssert {
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields(
                "context",
                "usr",
                "account",
                "view",
                "device",
                "dd.configuration",
                "dd.cls"
            )
            .isEqualTo(expected)
        assertDdClsEquals(actual.dd.cls, expected.dd.cls)
        assertConfigurationEquals(actual.dd.configuration, expected.dd.configuration)
        assertThat(actual.view)
            .usingRecursiveComparison()
            .ignoringFields(
                "memoryAverage",
                "memoryMax",
                "cpuTicksCount",
                "cpuTicksPerSecond",
                "refreshRateAverage",
                "refreshRateMin",
                "cumulativeLayoutShift",
                "slowFramesRate",
                "freezeRate"
            )
            .isEqualTo(expected.view)
        assertNumberFieldEquals(actual.view.memoryAverage, expected.view.memoryAverage)
        assertNumberFieldEquals(actual.view.memoryMax, expected.view.memoryMax)
        assertNumberFieldEquals(actual.view.cpuTicksCount, expected.view.cpuTicksCount)
        assertNumberFieldEquals(actual.view.cpuTicksPerSecond, expected.view.cpuTicksPerSecond)
        assertNumberFieldEquals(actual.view.refreshRateAverage, expected.view.refreshRateAverage)
        assertNumberFieldEquals(actual.view.refreshRateMin, expected.view.refreshRateMin)
        assertNumberFieldEquals(actual.view.cumulativeLayoutShift, expected.view.cumulativeLayoutShift)
        assertNumberFieldEquals(actual.view.slowFramesRate, expected.view.slowFramesRate)
        assertNumberFieldEquals(actual.view.freezeRate, expected.view.freezeRate)
        assertThat(actual.device)
            .usingRecursiveComparison()
            .withGsonIntEqualsForFields("totalRam", "logicalCpuCount")
            .ignoringFields("batteryLevel", "brightnessLevel")
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
        assertPropertiesEquals(
            actual.usr?.additionalProperties,
            expected.usr?.additionalProperties
        )
        assertPropertiesEquals(
            actual.account?.additionalProperties,
            expected.account?.additionalProperties
        )
        assertThat(actual.context)
            .usingRecursiveComparison()
            .ignoringFields("additionalProperties")
            .isEqualTo(expected.context)
        assertPropertiesEquals(
            actual.context?.additionalProperties,
            expected.context?.additionalProperties
        )
        return this
    }

    private fun assertDdClsEquals(actual: ViewUpdateEvent.DdCls?, expected: ViewUpdateEvent.DdCls?) {
        if (expected == null) {
            assertThat(actual).isNull()
            return
        }
        checkNotNull(actual)
        assertNumberFieldEquals(actual.devicePixelRatio, expected.devicePixelRatio)
    }

    private fun assertConfigurationEquals(
        actual: ViewUpdateEvent.Configuration?,
        expected: ViewUpdateEvent.Configuration?
    ) {
        if (expected == null) {
            assertThat(actual).isNull()
            return
        }
        checkNotNull(actual)
        assertNumberFieldEquals(actual.sessionSampleRate, expected.sessionSampleRate)
        assertNumberFieldEquals(actual.sessionReplaySampleRate, expected.sessionReplaySampleRate)
        assertNumberFieldEquals(actual.profilingSampleRate, expected.profilingSampleRate)
        assertNumberFieldEquals(actual.traceSampleRate, expected.traceSampleRate)
        assertThat(actual.startSessionReplayRecordingManually)
            .isEqualTo(expected.startSessionReplayRecordingManually)
    }

    private fun assertPropertiesEquals(actual: Map<String, Any?>?, expected: Map<String, Any?>?) {
        DeserializedMapAssert.assertThat(actual ?: emptyMap())
            .isEqualTo(expected ?: emptyMap())
    }

    private fun assertNumberFieldEquals(actual: Number?, expected: Number?) {
        if (expected == null) {
            assertThat(actual).isNull()
        } else {
            assertThat(actual).isNotNull()
            assertThat(actual!!.toDouble())
                .isCloseTo(expected.toDouble(), Offset.offset(0.00001))
        }
    }

    companion object {
        fun assertThat(actual: ViewUpdateEvent): DeserializedViewUpdateEventAssert {
            return DeserializedViewUpdateEventAssert(actual)
        }
    }
}
