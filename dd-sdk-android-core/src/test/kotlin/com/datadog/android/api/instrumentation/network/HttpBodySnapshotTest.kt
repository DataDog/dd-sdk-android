/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.api.instrumentation.network

import com.datadog.android.utils.forge.Configurator
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@Extensions(
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
internal class HttpBodySnapshotTest {

    @Test
    fun `M decode as UTF-8 W string() {no content type}`(@StringForgery fakePayload: String) {
        // Given
        val testedSnapshot = HttpBodySnapshot(fakePayload.toByteArray(Charsets.UTF_8))

        // When
        val result = testedSnapshot.string()

        // Then
        assertThat(result).isEqualTo(fakePayload)
    }

    @Test
    fun `M decode as UTF-8 W string() {content type without charset}`(@StringForgery fakePayload: String) {
        // Given
        val testedSnapshot = HttpBodySnapshot(
            bytes = fakePayload.toByteArray(Charsets.UTF_8),
            contentType = "application/json"
        )

        // When
        val result = testedSnapshot.string()

        // Then
        assertThat(result).isEqualTo(fakePayload)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "text/plain; charset=ISO-8859-1",
            "text/plain;charset=ISO-8859-1",
            "text/plain; charset=iso-8859-1",
            "text/plain; CHARSET=ISO-8859-1",
            "text/plain; charset=\"ISO-8859-1\"",
            "text/plain; charset = ISO-8859-1",
            "text/plain;\tcharset\t=\t\"ISO-8859-1\" ",
            "text/plain; boundary=xyz; charset=ISO-8859-1"
        ]
    )
    fun `M decode with declared charset W string() {charset parameter}`(fakeContentType: String) {
        // Given
        val fakePayload = "café naïve"
        val testedSnapshot = HttpBodySnapshot(
            bytes = fakePayload.toByteArray(Charsets.ISO_8859_1),
            contentType = fakeContentType
        )

        // When
        val result = testedSnapshot.string()

        // Then
        assertThat(result).isEqualTo(fakePayload)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "text/plain; charset=not-a-real-charset",
            "text/plain; charset=<>!",
            "text/plain; charset=",
            "text/plain; charset-extra=ISO-8859-1"
        ]
    )
    fun `M fall back to UTF-8 W string() {unusable charset}`(fakeContentType: String) {
        // Given
        val fakePayload = "café naïve"
        val testedSnapshot = HttpBodySnapshot(
            bytes = fakePayload.toByteArray(Charsets.UTF_8),
            contentType = fakeContentType
        )

        // When
        val result = testedSnapshot.string()

        // Then
        assertThat(result).isEqualTo(fakePayload)
    }

    @Test
    fun `M not be truncated by default W constructor()`(@StringForgery fakePayload: String) {
        // When
        val testedSnapshot = HttpBodySnapshot(fakePayload.toByteArray())

        // Then
        assertThat(testedSnapshot.isTruncated).isFalse()
        assertThat(testedSnapshot.contentType).isNull()
    }
}
