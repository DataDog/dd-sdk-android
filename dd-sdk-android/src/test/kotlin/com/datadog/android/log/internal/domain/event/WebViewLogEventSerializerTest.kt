/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.domain.event

import com.datadog.android.log.model.LogEvent
import com.datadog.android.log.model.WebViewLogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.BeforeEach
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
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@ForgeConfiguration(Configurator::class)
internal class WebViewLogEventSerializerTest {

    lateinit var testedSerializer: WebViewLogEventSerializer

    @BeforeEach
    fun `set up`() {
        testedSerializer = WebViewLogEventSerializer()
    }

    @Test
    fun `serializes full log as json`(@Forgery fakeLog: WebViewLogEvent) {
        val serialized = testedSerializer.serialize(fakeLog)
        assertSerializedLogMatchesInputLog(serialized, fakeLog)
    }

    @Test
    fun `ignores reserved attributes`(@Forgery fakeLog: WebViewLogEvent, forge: Forge) {
        // Given
        val logWithoutAttributes = fakeLog.copy(additionalProperties = emptyMap())
        val attributes = forge.aMap {
            anElementFrom(LogEvent.RESERVED_PROPERTIES.asList()) to forge.anAsciiString()
        }
        val logWithReservedAttributes = fakeLog.copy(additionalProperties = attributes)

        // When
        val serialized = testedSerializer.serialize(logWithReservedAttributes)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutAttributes)
    }

    @Test
    fun `ignores reserved tags keys`(@Forgery fakeLog: WebViewLogEvent, forge: Forge) {
        // Given
        val logWithoutTags = fakeLog.copy(ddtags = "")
        val key = forge.anElementFrom("host", "device", "source", "service")
        val value = forge.aNumericalString()
        val reservedTag = "$key:$value"
        val logWithReservedTags = fakeLog.copy(ddtags = reservedTag)

        // When
        val serialized = testedSerializer.serialize(logWithReservedTags)

        // Then
        assertSerializedLogMatchesInputLog(serialized, logWithoutTags)
    }

    // region Internal

    private fun assertSerializedLogMatchesInputLog(
        serializedObject: String,
        log: WebViewLogEvent
    ) {
        val jsonObject = JsonParser.parseString(serializedObject).asJsonObject
        assertThat(jsonObject)
            .hasField(KEY_MESSAGE, log.message)
            .hasNullableField(KEY_SERVICE_NAME, log.service)
            .hasNullableField(KEY_STATUS, log.status?.toJson()?.asString)
            .hasField(KEY_DATE, log.date)
            .hasField(KEY_TAGS, log.ddtags)
    }

    // endregion

    companion object {

        private const val KEY_SERVICE_NAME = "service"
        private const val KEY_DATE = "date"
        private const val KEY_MESSAGE = "message"
        private const val KEY_TAGS = "ddtags"
        private const val KEY_STATUS = "status"
    }
}
