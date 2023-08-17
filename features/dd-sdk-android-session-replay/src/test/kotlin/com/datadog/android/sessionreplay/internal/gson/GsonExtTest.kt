/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.gson

import com.datadog.android.api.InternalLogger
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import com.datadog.android.utils.verifyLog
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),

    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class GsonExtTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    // region safeGetAsJsonObject

    @Test
    fun `M return the JsonObject W safeGetAsJsonObject`(@Forgery fakeJsonObject: JsonObject) {
        assertThat(fakeJsonObject.safeGetAsJsonObject(mockInternalLogger)).isSameAs(fakeJsonObject)
    }

    @Test
    fun `M return null W safeGetAsJsonObject{a JsonArray}`(@Forgery fakeJsonArray: JsonArray) {
        // Given
        val expectedLogMessage = BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
            fakeJsonArray.toString(),
            JSON_OBJECT_TYPE
        )

        // Then
        assertThat(fakeJsonArray.safeGetAsJsonObject(mockInternalLogger)).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            expectedLogMessage
        )
    }

    @Test
    fun `M return null W safeGetAsJsonObject{a JsonPrimitive}`(
        @Forgery fakeJsonPrimitive: JsonPrimitive
    ) {
        // Given
        val expectedLogMessage = BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
            fakeJsonPrimitive.toString(),
            JSON_OBJECT_TYPE
        )

        // Then
        assertThat(fakeJsonPrimitive.safeGetAsJsonObject(mockInternalLogger)).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            expectedLogMessage
        )
    }

    // endregion

    // region safeGetAsJsonArray

    @Test
    fun `M return the jsonArray W safeGetAsJsonObject`(@Forgery fakeJsonArray: JsonArray) {
        assertThat(fakeJsonArray.safeGetAsJsonArray(mockInternalLogger)).isSameAs(fakeJsonArray)
    }

    @Test
    fun `M return null W safeGetAsJsonArray{a JsonPrimitive}`(
        @Forgery fakeJsonPrimitive: JsonPrimitive
    ) {
        // Given
        val expectedLogMessage = BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
            fakeJsonPrimitive.toString(),
            JSON_ARRAY_TYPE
        )
        assertThat(fakeJsonPrimitive.safeGetAsJsonArray(mockInternalLogger)).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            expectedLogMessage
        )
    }

    // endregion

    // region safeGetAsLong

    @Test
    fun `M return the primitive W safeGetAsLong`(forge: Forge) {
        // Given
        val fakeLong = forge.aLong()
        val fakeLongPrimitive = JsonPrimitive(fakeLong)

        // Then
        assertThat(fakeLongPrimitive.safeGetAsLong(mockInternalLogger)).isEqualTo(fakeLong)
    }

    @Test
    fun `M return null W safeGetAsLong{a non long JsonPrimitive}`(forge: Forge) {
        // Given
        val fakeNonLongJsonPrimitive = JsonPrimitive(forge.aString())
        val expectedLogMessage = BROKEN_JSON_ERROR_MESSAGE_FORMAT.format(
            fakeNonLongJsonPrimitive.toString(),
            JSON_PRIMITIVE_TYPE
        )

        // Then
        assertThat(fakeNonLongJsonPrimitive.safeGetAsLong(mockInternalLogger)).isNull()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.TELEMETRY,
            expectedLogMessage,
            NumberFormatException::class.java
        )
    }

    // endregion
}
