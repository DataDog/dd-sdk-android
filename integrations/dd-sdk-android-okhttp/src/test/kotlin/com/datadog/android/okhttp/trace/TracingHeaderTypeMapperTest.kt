/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.okhttp.trace

import com.datadog.android.okhttp.internal.trace.mapHostsWithHeaderTypes
import com.datadog.android.okhttp.internal.trace.toInternalTracingHeaderType
import com.datadog.android.okhttp.internal.utils.forge.OkHttpConfigurator
import com.datadog.android.trace.TracingHeaderType
import fr.xgouchet.elmyr.Forge
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
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(OkHttpConfigurator::class)
internal class TracingHeaderTypeMapperTest {

    @Test
    fun `M map to List of SelectedTracingPropagator W mapToSelectedTracingPropagators`(
        forge: Forge
    ) {
        // Given
        val host1 = forge.aString()
        val host2 = forge.aString()
        val host3 = forge.aString()

        val headerType1 = forge.aValueFrom(TracingHeaderType::class.java)
        val headerType2 = forge.aValueFrom(TracingHeaderType::class.java, exclude = listOf(headerType1))

        val mappedHeaderType1 = headerType1.toInternalTracingHeaderType()
        val mappedHeaderType2 = headerType2.toInternalTracingHeaderType()

        val tracingHeaderTypes = mapOf(
            host1 to setOf(headerType1),
            host2 to setOf(headerType2, headerType1),
            host3 to emptySet()
        )

        // When
        val result = mapHostsWithHeaderTypes(tracingHeaderTypes)

        // Then
        assertThat(result.types)
            .isEqualTo(setOf(mappedHeaderType1, mappedHeaderType2))
    }
}
