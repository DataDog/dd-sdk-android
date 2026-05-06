/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.internal.heatmaps

import com.datadog.android.internal.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class HeatmapIdentifierTest {

    @Test
    fun `M produce slash-joined path W canonicalPath`(
        @StringForgery fakeBundleIdentifier: String,
        @StringForgery fakeScreenName: String,
        forge: Forge
    ) {
        // Given
        val fakeSegments = forge.aList(size = forge.anInt(min = 1, max = 20)) { forge.anAlphabeticalString() }

        // When
        val canonicalPath = HeatmapIdentifier.canonicalPath(fakeSegments, fakeScreenName, fakeBundleIdentifier)

        // Then
        assertThat(canonicalPath).isEqualTo(
            "$fakeBundleIdentifier/$fakeScreenName/${fakeSegments.joinToString("/")}"
        )
    }
}
