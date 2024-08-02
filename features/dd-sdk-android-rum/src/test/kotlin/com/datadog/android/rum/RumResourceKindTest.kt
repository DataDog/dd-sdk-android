/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum

import com.datadog.android.rum.utils.forge.Configurator
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
internal class RumResourceKindTest {

    @Test
    fun `detect image MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("image/((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}")

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.IMAGE)
    }

    @Test
    fun `detect media MimeType { audio }`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching(
            "audio/((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}"
        )

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.MEDIA)
    }

    @Test
    fun `detect media MimeType { video }`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching(
            "video/((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}"
        )

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.MEDIA)
    }

    @Test
    fun `detect font MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("font/((vnd\\.)|([xX]\\.)|(x\\-)|([a-z]))[a-z]{2,8}")

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.FONT)
    }

    @Test
    fun `detect js MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("text/javascript")

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.JS)
    }

    @Test
    fun `detect css MimeType`(
        forge: Forge
    ) {
        val mimeType = forge.aStringMatching("text/css")

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.CSS)
    }

    @Test
    fun `detect unknown MimeType as NATIVE`(
        forge: Forge
    ) {
        val mimeType = forge.aWhitespaceString()

        val kind = RumResourceKind.fromMimeType(mimeType)

        assertThat(kind)
            .isEqualTo(RumResourceKind.NATIVE)
    }
}
