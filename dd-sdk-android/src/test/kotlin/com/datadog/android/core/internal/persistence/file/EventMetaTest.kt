/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.persistence.file

import com.google.gson.JsonParseException
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
internal class EventMetaTest {

    @Test
    fun `𝕄 return original value 𝕎 asBytes + fromBytes()`() {
        // Given
        val originalMeta = EventMeta()

        // When
        val restoredMeta = EventMeta.fromBytes(originalMeta.asBytes)

        // Then
        assertThat(restoredMeta).isEqualTo(originalMeta)
    }

    @Test
    fun `𝕄 throw JsonParseException 𝕎 fromBytes() { bad meta bytes }`(
        @StringForgery metaBytes: String
    ) {
        assertThrows<JsonParseException> { EventMeta.fromBytes(metaBytes.toByteArray()) }
    }

    @Test
    fun `𝕄 throw JsonParseException 𝕎 fromBytes() { unexpected json }`(
        forge: Forge
    ) {
        // Given
        val metaBytes = forge.anElementFrom(
            "[]"
        ).toByteArray()

        // When + Then
        assertThrows<JsonParseException> { EventMeta.fromBytes(metaBytes) }
    }
}
