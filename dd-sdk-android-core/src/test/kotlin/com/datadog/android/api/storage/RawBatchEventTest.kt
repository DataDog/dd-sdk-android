/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.storage

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RawBatchEventTest {

    @Test
    fun `M return true W equals() {same data and metadata}`(
        @StringForgery fakeData: String,
        @StringForgery fakeMetadata: String
    ) {
        // Given
        val event = RawBatchEvent(
            fakeData.toByteArray(),
            fakeMetadata.toByteArray()
        )
        val other = RawBatchEvent(
            fakeData.toByteArray(),
            fakeMetadata.toByteArray()
        )

        // When
        val result = event == other

        // Then
        assertThat(result).isTrue()
    }

    @Test
    fun `M return false W equals() {different data}`(
        @StringForgery fakeData: String,
        @StringForgery fakeMetadata: String
    ) {
        // Given
        val event = RawBatchEvent(
            fakeData.toByteArray(),
            fakeMetadata.toByteArray()
        )
        val other = RawBatchEvent(
            (fakeData + "x").toByteArray(),
            fakeMetadata.toByteArray()
        )

        // When
        val result = event == other

        // Then
        assertThat(result).isFalse()
    }

    @Test
    fun `M return same value W hashCode() {same data and metadata}`(
        @StringForgery fakeData: String,
        @StringForgery fakeMetadata: String
    ) {
        // Given
        val event = RawBatchEvent(
            fakeData.toByteArray(),
            fakeMetadata.toByteArray()
        )
        val other = RawBatchEvent(
            fakeData.toByteArray(),
            fakeMetadata.toByteArray()
        )

        // When
        val result = event.hashCode()

        // Then
        assertThat(result).isEqualTo(other.hashCode())
    }

    @Test
    fun `M return different value W hashCode() {different data}`(
        @StringForgery fakeData: String,
        @StringForgery fakeMetadata: String
    ) {
        // Given
        val event = RawBatchEvent(
            fakeData.toByteArray(),
            fakeMetadata.toByteArray()
        )
        val other = RawBatchEvent(
            (fakeData + "x").toByteArray(),
            fakeMetadata.toByteArray()
        )

        // When
        val result = event.hashCode()

        // Then
        assertThat(result).isNotEqualTo(other.hashCode())
    }
}
