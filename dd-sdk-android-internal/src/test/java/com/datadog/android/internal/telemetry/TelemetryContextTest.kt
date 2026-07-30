/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.telemetry

import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class TelemetryContextTest {

    @Test
    fun `M return map with both keys W asAttributesMap() { non-null dropped bytes }`(
        @StringForgery fakeFeatureName: String,
        @IntForgery fakeDroppedBytes: Int
    ) {
        val result = TelemetryContext(featureName = fakeFeatureName)
            .asAttributesMap(bytesLost = fakeDroppedBytes)

        assertThat(result).containsEntry(TelemetryContext.FEATURE_NAME, fakeFeatureName)
        assertThat(result).containsEntry(TelemetryContext.EVENT_DROPPED_BYTES, fakeDroppedBytes)
        assertThat(result).doesNotContainKey(TelemetryContext.EVENT_TYPE)
    }

    @Test
    fun `M include EVENT_TYPE W asAttributesMap() { non-null event type }`(
        @StringForgery fakeFeatureName: String,
        @IntForgery fakeDroppedBytes: Int,
        @StringForgery fakeEventType: String
    ) {
        val result = TelemetryContext(featureName = fakeFeatureName, eventType = fakeEventType)
            .asAttributesMap(bytesLost = fakeDroppedBytes)

        assertThat(result).containsEntry(TelemetryContext.FEATURE_NAME, fakeFeatureName)
        assertThat(result).containsEntry(TelemetryContext.EVENT_DROPPED_BYTES, fakeDroppedBytes)
        assertThat(result).containsEntry(TelemetryContext.EVENT_TYPE, fakeEventType)
    }

    @Test
    fun `M return map without EVENT_DROPPED_BYTES key W Companion#asAttributesMap() { null dropped bytes }`(
        @StringForgery fakeFeatureName: String
    ) {
        val result = TelemetryContext.asAttributesMap(
            featureName = fakeFeatureName,
            bytesLost = null
        )

        assertThat(result).containsEntry(TelemetryContext.FEATURE_NAME, fakeFeatureName)
        assertThat(result).doesNotContainKey(TelemetryContext.EVENT_DROPPED_BYTES)
    }
}
