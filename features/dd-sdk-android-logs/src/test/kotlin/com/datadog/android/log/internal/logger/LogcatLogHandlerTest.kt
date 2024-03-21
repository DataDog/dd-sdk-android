/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Locale

@ExtendWith(ForgeExtension::class)
internal class LogcatLogHandlerTest {

    lateinit var testedHandler: LogcatLogHandler

    @StringForgery
    lateinit var fakeServiceName: String

    @BeforeEach
    fun `set up`() {
        testedHandler = LogcatLogHandler(fakeServiceName, true)
    }

    @Test
    fun `resolves stack trace element null if in release mode`() {
        testedHandler = LogcatLogHandler(fakeServiceName, true, isDebug = false)

        val element = testedHandler.getCallerStackElement()

        assertThat(element)
            .isNull()
    }

    @Test
    fun `resolves stack trace element null if useClassnameAsTag=false`() {
        testedHandler = LogcatLogHandler(fakeServiceName, false, isDebug = true)

        val element = testedHandler.getCallerStackElement()

        assertThat(element)
            .isNull()
    }

    @Test
    fun `resolves stack trace element from caller`() {
        testedHandler = LogcatLogHandler(fakeServiceName, true, isDebug = true)

        val element = testedHandler.getCallerStackElement()

        checkNotNull(element)
        assertThat(element.className)
            .isEqualTo(javaClass.canonicalName)
    }

    @Test
    fun `resolves nested stack trace element from caller`() {
        testedHandler = LogcatLogHandler(fakeServiceName, true, isDebug = true)

        var element: StackTraceElement? = null

        // Don't convert it to lambda, because Kotlin won't create wrapper class,
        // read more https://kotlinlang.org/docs/whatsnew15.html#sam-adapters-via-invokedynamic
        @Suppress("ObjectLiteralToLambda")
        val runnable = object : Runnable {
            override fun run() {
                element = testedHandler.getCallerStackElement()
            }
        }
        runnable.run()

        assertThat(element!!.className)
            .isEqualTo(
                "${javaClass.canonicalName}" +
                    "\$resolves nested stack trace element from caller" +
                    "\$runnable\$1"
            )
    }

    @RepeatedTest(4)
    fun `resolves valid stack trace element when wrapped in timber`(forge: Forge) {
        // Given
        val forgeFileName: Forge.() -> String = {
            "${this.anAlphabeticalString(Case.ANY)}.${this.aStringMatching("(kt|java)")}"
        }

        val ignoredElements = forge.aList {
            val className = if (aBool()) {
                // generate from ignored class names pattern
                LogcatLogHandler.IGNORED_CLASS_NAMES.random()
            } else {
                // generate from ignored packages prefixes pattern
                val packagePrefix = LogcatLogHandler.IGNORED_PACKAGE_PREFIXES.random()
                val packageSuffix = anAlphabeticalString(Case.ANY)
                    .replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
                    }
                "$packagePrefix.$packageSuffix"
            }

            StackTraceElement(
                className,
                anAlphabeticalString(Case.ANY),
                forgeFileName(this),
                aSmallInt()
            )
        }

        val validElements = forge.aList {
            val className = "com.${anAlphabeticalString(Case.LOWER, 5)}" +
                ".${anAlphabeticalString(Case.LOWER, 6)}" +
                ".${anAlphabeticalString(Case.LOWER, 7)}" +
                ".${anAlphabeticalString(Case.ANY, 8)}"

            StackTraceElement(
                className,
                anAlphabeticalString(Case.ANY),
                forgeFileName(this),
                aSmallInt()
            )
        }

        val stackTrace = (ignoredElements + validElements).toTypedArray()

        // When
        val element = testedHandler.findValidCallStackElement(stackTrace)

        // Then
        checkNotNull(element)
        assertThat(element.className)
            .isEqualTo(validElements.first().className)
    }
}
