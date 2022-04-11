/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Locale
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class LogcatLogHandlerTest {

    lateinit var testedHandler: LogcatLogHandler

    @StringForgery
    lateinit var fakeServiceName: String

    @BeforeEach
    fun `set up`() {
        testedHandler = LogcatLogHandler(fakeServiceName, true)
    }

    @AfterEach
    fun `tear down`() {
        Datadog.isDebug = BuildConfig.DEBUG
    }

    @Test
    fun `resolves stack trace element null if in release mode`() {
        Datadog.isDebug = false

        val element = testedHandler.getCallerStackElement()

        assertThat(element)
            .isNull()
    }

    @Test
    fun `resolves stack trace element null if useClassnameAsTag=false`() {
        testedHandler = LogcatLogHandler(fakeServiceName, false)
        Datadog.isDebug = true

        val element = testedHandler.getCallerStackElement()

        assertThat(element)
            .isNull()
    }

    @Test
    fun `resolves stack trace element from caller`() {
        Datadog.isDebug = true

        val element = testedHandler.getCallerStackElement()

        checkNotNull(element)
        assertThat(element.className)
            .isEqualTo(javaClass.canonicalName)
    }

    // TODO RUMM-1732
    @Disabled(
        "Check this test later. After update to Gradle 7 it seems there is some change to" +
            " the test run mechanism, because .className returns parent enclosing class" +
            " without any anonymous classes, and anonymous class itself is called \$lambda-0" +
            " instead of \$1"
    )
    @Test
    fun `resolves nested stack trace element from caller`() {
        Datadog.isDebug = true

        var element: StackTraceElement? = null

        // Don't convert it to lambda, because Kotlin won't create wrapper class,
        // read more https://kotlinlang.org/docs/whatsnew15.html#sam-adapters-via-invokedynamic
        @Suppress("ObjectLiteralToLambda") val runnable = object : Runnable {
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
                packagePrefix + ".${anAlphabeticalString(Case.ANY).replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(
                        Locale.US
                    ) else it.toString()
                }}"
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
