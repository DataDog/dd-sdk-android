/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.utils.forge.Configurator
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
@ForgeConfiguration(Configurator::class)
internal class RumResourceTypeTest {

    @Test
    fun `detect image MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("image/((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}")

        val kind = RumResourceType.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceType.IMAGE)
    }

    @Test
    fun `detect media MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching(
            "(video|audio)/" +
                "((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}"
        )

        val kind = RumResourceType.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceType.MEDIA)
    }

    @Test
    fun `detect font MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("font/((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}")

        val kind = RumResourceType.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceType.FONT)
    }

    @Test
    fun `detect js MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("text/javascript")

        val kind = RumResourceType.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceType.JS)
    }

    @Test
    fun `detect css MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("text/css")

        val kind = RumResourceType.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceType.CSS)
    }

    @Test
    fun `detect unknown MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aWhitespaceString()

        val kind = RumResourceType.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceType.UNKNOWN)
    }
}
