/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.tools.unit.assertj

import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.UnknownFormatConversionException
import junit.framework.AssertionFailedError
import org.assertj.core.api.AbstractObjectAssert
import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matcher

/**
 * Assertion methods for [ByteArrayOutputStream].
 */
class ByteArrayOutputStreamAssert(actual: ByteArrayOutputStream) :
    AbstractObjectAssert<ByteArrayOutputStreamAssert, ByteArrayOutputStream>(
        actual,
        ByteArrayOutputStreamAssert::class.java
    ) {

    private val lines = actual.toString(Charsets.UTF_8.name())
        .split('\n')
        .filter { it.isNotEmpty() }

    /**
     *  Verifies that the actual ByteArrayOutputStream is empty.
     */
    fun isEmpty(): ByteArrayOutputStreamAssert {
        assertThat(actual.size()).isEqualTo(0)
        return this
    }

    /**
     *  Verifies that the actual ByteArrayOutputStream contains an Android Log line.
     *  @param level the Android [Log] level
     *  @param tagName the log tag name
     *  @param message the exact message
     *  @param withLink whether a link to source code should be present
     */
    fun hasLogLine(
        level: Int,
        tagName: String,
        message: String,
        withLink: Boolean = false
    ): ByteArrayOutputStreamAssert {
        return hasLogLine(level, tagName, equalTo(message), withLink)
    }

    /**
     *  Verifies that the actual ByteArrayOutputStream contains an Android Log line.
     *  @param level the Android [Log] level
     *  @param tagName the log tag name
     *  @param messageMatcher a matcher for the message
     *  @param withLink whether a link to source code should be present
     */
    @Suppress("SwallowedException")
    fun hasLogLine(
        level: Int,
        tagName: String,
        messageMatcher: Matcher<String>,
        withLink: Boolean = false
    ): ByteArrayOutputStreamAssert {
        val hasMatch = lines.any {
            isLogMatch(it, level, tagName, messageMatcher, withLink)
        }
        try {
            assertThat(hasMatch)
                .overridingErrorMessage(
                    "Expected stream to have log " +
                        "with level ${LEVELS[level]}, with tag $tagName " +
                        "and message matching [$messageMatcher].\n" +
                        "Stream content was:\n" +
                        lines.joinToString("\n").replace("%", "\\%")
                )
                .isTrue()
        } catch (e: UnknownFormatConversionException) {
            // AssertJ always try to format even though it shouldn't
            // Reported at https://github.com/joel-costigliola/assertj-core/issues/1795
            throw AssertionFailedError(
                "Expected stream to have log " +
                    "with level ${LEVELS[level]}, with tag $tagName " +
                    "and message matching [$messageMatcher].\n" +
                    "Stream content was:\n" +
                    lines.joinToString("\n")
            )
        }
        return this
    }

    // region Internal

    @Suppress("MagicNumber")
    private fun isLogMatch(
        line: String,
        level: Int,
        tagName: String,
        messageMatcher: Matcher<String>,
        withLink: Boolean
    ): Boolean {
        val matchResult = LOG_REGEX_LINK.matchEntire(line) ?: LOG_REGEX_NO_LINK.matchEntire(line)
        return if (matchResult == null) {
            false
        } else if (withLink) {
            matchResult.groupValues[1] == LEVELS[level] &&
                matchResult.groupValues[2] == tagName &&
                messageMatcher.matches(matchResult.groupValues[3]) &&
                matchResult.groupValues.size == 5
        } else {
            matchResult.groupValues[1] == LEVELS[level] &&
                matchResult.groupValues[2] == tagName &&
                messageMatcher.matches(matchResult.groupValues[3])
        }
    }

    // endregion

    companion object {

        private val LOG_REGEX_LINK =
            Regex("([VDIWEA])/([a-zA-Z0-9_$ ]+): (.*)(\\t\\| at \\.[\\w\\s]+\\(.*\\))")
        private val LOG_REGEX_NO_LINK = Regex("([VDIWEA])/([a-zA-Z0-9_$ ]+): (.*)")

        private val LEVELS = arrayOf("0", "1", "V", "D", "I", "W", "E", "A", "X")

        /**
         * Create assertion for [ByteArrayOutputStream].
         * @param actual the actual object to assert on
         * @return the created assertion object.
         */
        @JvmStatic
        fun assertThat(actual: ByteArrayOutputStream): ByteArrayOutputStreamAssert =
            ByteArrayOutputStreamAssert(actual)
    }
}
