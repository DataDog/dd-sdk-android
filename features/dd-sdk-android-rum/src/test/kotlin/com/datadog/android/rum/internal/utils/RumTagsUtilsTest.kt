/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.rum.utils.forge.Configurator
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
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
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class RumTagsUtilsTest {

    @Test
    fun `M build DD tags string with variant W buildDDTagsString() {with non-empty variant}`(
        forge: Forge
    ) {
        // Given
        val context = forge.getForgery<DatadogContext>()

        // When
        val result = buildDDTagsString(context)

        // Then
        val tagsMap = result.parseToTagsMap()
        assertThat(tagsMap).isEqualTo(
            mapOf(
                "service" to context.service,
                "version" to context.version,
                "sdk_version" to context.sdkVersion,
                "env" to context.env,
                "variant" to context.variant
            )
        )
    }

    @Test
    fun `M build DD tags string without variant W buildDDTagsString() {with empty variant}`(
        @Forgery fakeContext: DatadogContext
    ) {
        // Given
        val context = fakeContext.copy(variant = "")

        // When
        val result = buildDDTagsString(context)

        // Then
        val tagsMap = result.parseToTagsMap()
        assertThat(tagsMap).isEqualTo(
            mapOf(
                "service" to context.service,
                "version" to context.version,
                "sdk_version" to context.sdkVersion,
                "env" to context.env
            )
        )
    }

    private fun String.parseToTagsMap(): Map<String, String> {
        return this.split(",")
            .associate { tag ->
                val parts = tag.split(":")
                assertThat(parts).hasSize(2)
                parts[0] to parts[1]
            }
    }
}
