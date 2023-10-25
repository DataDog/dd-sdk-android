/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.domain.event

import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.assertj.JsonObjectAssert.Companion.assertThat
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
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
@ForgeConfiguration(Configurator::class)
internal class RumEventMetaSerializerTest {

    private val testedSerializer = RumEventMetaSerializer()

    @Test
    fun `𝕄 serialize RUM View Event meta 𝕎 serialize()`(
        @Forgery eventMeta: RumEventMeta.View
    ) {
        // When
        val serialized = testedSerializer.serialize(eventMeta)

        // Then
        val jsonObject = JsonParser.parseString(serialized).asJsonObject
        assertThat(jsonObject)
            .hasField("documentVersion", eventMeta.documentVersion)
            .hasField("viewId", eventMeta.viewId)
            .hasField("type", RumEventMeta.VIEW_TYPE_VALUE)
    }
}
