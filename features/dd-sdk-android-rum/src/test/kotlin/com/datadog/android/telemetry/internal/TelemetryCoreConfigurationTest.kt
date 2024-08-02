/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.telemetry.internal

import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.kotlin.mock

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class TelemetryCoreConfigurationTest {

    @Test
    fun `M create TelemetryCoreConfiguration W fromEvent()`(
        @BoolForgery trackErrors: Boolean,
        @BoolForgery useProxy: Boolean,
        @BoolForgery useLocalEncryption: Boolean,
        @LongForgery(min = 0L) batchSize: Long,
        @LongForgery(min = 0L) batchUploadFrequency: Long,
        @IntForgery(min = 0) batchProcessingLevel: Int
    ) {
        // Given
        val event = mapOf(
            "type" to "telemetry_configuration",
            "track_errors" to trackErrors,
            "batch_size" to batchSize,
            "batch_upload_frequency" to batchUploadFrequency,
            "use_proxy" to useProxy,
            "use_local_encryption" to useLocalEncryption,
            "batch_processing_level" to batchProcessingLevel
        )

        // When
        val coreConfig = TelemetryCoreConfiguration.fromEvent(event, internalLogger = mock())

        // Then
        assertThat(coreConfig).isNotNull
        assertThat(coreConfig!!.trackErrors).isEqualTo(trackErrors)
        assertThat(coreConfig.batchSize).isEqualTo(batchSize)
        assertThat(coreConfig.batchUploadFrequency).isEqualTo(batchUploadFrequency)
        assertThat(coreConfig.useProxy).isEqualTo(useProxy)
        assertThat(coreConfig.useLocalEncryption).isEqualTo(useLocalEncryption)
        assertThat(coreConfig.batchProcessingLevel).isEqualTo(batchProcessingLevel)
    }

    @Test
    fun `M return null W fromEvent() { malformed message }`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)])
        ) fakeEvent: Map<String, String>
    ) {
        // When
        val coreConfig = TelemetryCoreConfiguration.fromEvent(fakeEvent, internalLogger = mock())

        // Then
        assertThat(coreConfig).isNull()
    }
}
