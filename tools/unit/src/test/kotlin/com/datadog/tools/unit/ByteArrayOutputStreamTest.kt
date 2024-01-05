/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit

import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class ByteArrayOutputStreamTest {

    @Test
    fun `get lastLine() from empty stream`() {
        val stream = ByteArrayOutputStream()

        val lastLine = stream.lastLine()

        assertThat(lastLine)
            .isNull()
    }

    @Test
    fun `get lastLine() from stream with one line`(
        forge: Forge
    ) {
        val line = forge.anAlphabeticalString()
        val stream = ByteArrayOutputStream()
        PrintWriter(stream.writer(Charsets.UTF_8)).use {
            it.print(line)
        }

        val lastLine = stream.lastLine()

        assertThat(lastLine)
            .isEqualTo(line)
    }

    @Test
    fun `get lastLine() from stream with one line and carriage return`(
        forge: Forge
    ) {
        val line = forge.anAlphabeticalString()
        val stream = ByteArrayOutputStream()
        PrintWriter(stream.writer(Charsets.UTF_8)).use {
            it.println(line)
        }

        val lastLine = stream.lastLine()

        assertThat(lastLine)
            .isEqualTo(line)
    }

    @Test
    fun `get lastLine() from stream with non utf 8 charset`(
        forge: Forge
    ) {
        val charset = Charsets.ISO_8859_1
        val line = forge.anAlphabeticalString()
        val stream = ByteArrayOutputStream()
        PrintWriter(stream.writer(charset)).use {
            it.print(line)
        }

        val lastLine = stream.lastLine(charset.name())

        assertThat(lastLine)
            .isEqualTo(line)
    }

    @Test
    fun `get lastLine() from stream with many lines`(
        forge: Forge
    ) {
        val line = forge.anAlphabeticalString()
        val stream = ByteArrayOutputStream()
        PrintWriter(stream.writer(Charsets.UTF_8)).use { pw ->
            repeat(20) {
                pw.println(forge.aNumericalString())
            }
            pw.print(line)
        }

        val lastLine = stream.lastLine()

        assertThat(lastLine)
            .isEqualTo(line)
    }

    @Test
    fun `get lastLine() from stream with many lines and carriage return`(
        forge: Forge
    ) {
        val line = forge.anAlphabeticalString()
        val stream = ByteArrayOutputStream()
        PrintWriter(stream.writer(Charsets.UTF_8)).use { pw ->
            repeat(20) {
                pw.println(forge.aNumericalString())
            }
            pw.println(line)
        }

        val lastLine = stream.lastLine()

        assertThat(lastLine)
            .isEqualTo(line)
    }
}
