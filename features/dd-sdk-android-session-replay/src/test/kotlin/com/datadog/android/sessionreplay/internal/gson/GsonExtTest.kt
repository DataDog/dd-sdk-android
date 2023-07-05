/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.internal.gson

import com.datadog.android.sessionreplay.forge.ForgeConfigurator
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

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(ForgeConfigurator::class)
internal class GsonExtTest {

    // region safeGetAsJsonObject

    @Test
    fun `M return the JsonObject W safeGetAsJsonObject`(@Forgery fakeJsonObject: JsonObject) {
        assertThat(fakeJsonObject.safeGetAsJsonObject()).isSameAs(fakeJsonObject)
    }

    @Test
    fun `M return null W safeGetAsJsonObject{a JsonArray}`(@Forgery fakeJsonArray: JsonArray) {
        assertThat(fakeJsonArray.safeGetAsJsonObject()).isNull()
    }

    @Test
    fun `M return null W safeGetAsJsonObject{a JsonPrimitive}`(
        @Forgery fakeJsonPrimitive: JsonPrimitive
    ) {
        assertThat(fakeJsonPrimitive.safeGetAsJsonObject()).isNull()
    }

    // endregion

    // region safeGetAsJsonArray

    @Test
    fun `M return the jsonArray W safeGetAsJsonObject`(@Forgery fakeJsonArray: JsonArray) {
        assertThat(fakeJsonArray.safeGetAsJsonArray()).isSameAs(fakeJsonArray)
    }

    @Test
    fun `M return null W safeGetAsJsonArray{a JsonPrimitive}`(
        @Forgery fakeJsonPrimitive: JsonPrimitive
    ) {
        assertThat(fakeJsonPrimitive.safeGetAsJsonObject()).isNull()
    }

    // endregion

    // region safeGetAsLong

    @Test
    fun `M return the primitive W safeGetAsLong`(forge: Forge) {
        // Given
        val fakeLong = forge.aLong()
        val fakeLongPrimitive = JsonPrimitive(fakeLong)

        // Then
        assertThat(fakeLongPrimitive.safeGetAsLong()).isEqualTo(fakeLong)
    }

    @Test
    fun `M return null W safeGetAsLong{a non long JsonPrimitive}`(
        @Forgery fakeJsonPrimitive: JsonPrimitive
    ) {
        assertThat(fakeJsonPrimitive.safeGetAsJsonObject()).isNull()
    }

    // endregion
}
