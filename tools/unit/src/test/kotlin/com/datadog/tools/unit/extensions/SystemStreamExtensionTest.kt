/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.tools.unit.extensions

import com.datadog.tools.unit.annotations.SystemErrorStream
import com.datadog.tools.unit.annotations.SystemOutStream
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
@Extensions(
    ExtendWith(SystemStreamExtension::class),
    ExtendWith(ForgeExtension::class)
)
internal class SystemStreamExtensionTest {

    @Test
    fun `@SystemOutStream catches System#out stream`(
        @SystemOutStream outStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        val singleLine = forge.anAlphabeticalString()

        System.out.print(singleLine)

        assertThat(outStream.toString())
            .isEqualTo(singleLine)
    }

    @Test
    fun `@SystemOutStream ignores System#err stream`(
        @SystemOutStream outStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        val singleLine = forge.anAlphabeticalString()

        System.err.print(singleLine)

        assertThat(outStream.toString())
            .isEmpty()
    }

    @Test
    fun `@SystemErrorStream catches System#err stream`(
        @SystemErrorStream errStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        val singleLine = forge.anAlphabeticalString()

        System.err.print(singleLine)

        assertThat(errStream.toString())
            .isEqualTo(singleLine)
    }

    @Test
    fun `@SystemErrorStream ignores System#out stream`(
        @SystemErrorStream errStream: ByteArrayOutputStream,
        forge: Forge
    ) {
        val singleLine = forge.anAlphabeticalString()

        System.out.print(singleLine)

        assertThat(errStream.toString())
            .isEmpty()
    }
}
