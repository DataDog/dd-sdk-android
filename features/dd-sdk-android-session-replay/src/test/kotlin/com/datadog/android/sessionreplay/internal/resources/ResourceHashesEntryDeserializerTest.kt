/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.resources

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.sessionreplay.model.ResourceHashesEntry
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ResourceHashesEntryDeserializerTest {
    private lateinit var testedDeserializer: ResourceHashesEntryDeserializer

    @Mock
    lateinit var mockLogger: InternalLogger

    @BeforeEach
    fun setup() {
        testedDeserializer = ResourceHashesEntryDeserializer(mockLogger)
    }

    @Test
    fun `M return ResourceHashesEntry W deserialize() { valid object }`(
        @LongForgery fakeUpdateDate: Long,
        forge: Forge
    ) {
        // Given
        val expectedEntry = ResourceHashesEntry(
            lastUpdateDateNs = fakeUpdateDate,
            resourceHashes = forge.aList { aString() }.distinct()
        )
        val json = expectedEntry.toJson()

        // When
        val actualEntry = testedDeserializer.deserialize(json.toString())

        // Then
        checkNotNull(actualEntry)
        assertThat(actualEntry.lastUpdateDateNs.toLong()).isEqualTo(expectedEntry.lastUpdateDateNs)
        assertThat(actualEntry.resourceHashes).isEqualTo(expectedEntry.resourceHashes)
    }

    @Test
    fun `M throw exception W deserialize() { got exception from object }`(
        @StringForgery fakeInvalidJson: String
    ) {
        // When
        testedDeserializer.deserialize(fakeInvalidJson)

        // Then
        val messageCaptor = argumentCaptor<() -> String>()
        verify(mockLogger).log(
            eq(InternalLogger.Level.ERROR),
            eq(listOf(InternalLogger.Target.MAINTAINER, InternalLogger.Target.TELEMETRY)),
            messageCaptor.capture(),
            isNull(),
            eq(false),
            isNull(),
            eq(false)
        )

        assertThat(messageCaptor.firstValue()).startsWith("Error while trying to deserialize the ResourceHashesEntry")
    }
}
