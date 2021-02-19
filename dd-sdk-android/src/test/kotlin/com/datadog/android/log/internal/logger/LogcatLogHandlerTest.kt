/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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

    @Test
    fun `resolves nested stack trace element from caller`() {
        Datadog.isDebug = true

        var element: StackTraceElement? = null

        val runnable = Runnable {
            element = testedHandler.getCallerStackElement()
        }
        runnable.run()

        assertThat(element!!.className)
            .isEqualTo(
                "${javaClass.canonicalName}" +
                    "\$resolves nested stack trace element from caller" +
                    "\$runnable\$1"
            )
    }

    @Test
    fun `resolves valid stack trace element when wrapped in timber`() {

        /* ktlint-disable max-line-length */
        // Given
        val rawStackTrace =
            "com.datadog.android.log.internal.logger.LogcatLogHandler.handleLog(LogcatLogHandler.kt:31)\n" +
                "com.datadog.android.log.internal.logger.CombinedLogHandler.handleLog(CombinedLogHandler.kt:23)\n" +
                "com.datadog.android.log.Logger.internalLog\$dd_sdk_android_debug(Logger.kt:517)\n" +
                "com.datadog.android.log.Logger.internalLog\$dd_sdk_android_debug\$default(Logger.kt:512)\n" +
                "com.datadog.android.log.Logger.log(Logger.kt:165)\n" +
                "com.datadog.android.log.Logger.log\$default(Logger.kt:163)\n" +
                "com.datadog.android.timber.DatadogTree.log(DatadogTree.kt:32)\n" +
                "timber.log.Timber\$Tree.prepareLog(Timber.java:532)\n" +
                "timber.log.Timber\$Tree.d(Timber.java:405)\n" +
                "timber.log.Timber\$1.d(Timber.java:243)\n" +
                "timber.log.Timber.d(Timber.java:38)\n" +
                "com.datadog.android.sample.home.HomeFragment.onClick(HomeFragment.kt:65)\n" +
                "android.view.View.performClick(View.java:7448)\n" +
                "android.view.View.performClickInternal(View.java:7425)\n" +
                "android.view.View.access\$3600(View.java:810)"
        /* ktlint-enable max-line-length */

        val matchingRegex = Regex("(\\S+)\\((\\S+)\\)")

        val stackTrace = rawStackTrace.lines().map { line ->
            // let it crash here if something is wrong with matching
            val groups = matchingRegex.matchEntire(line)!!.groupValues

            // group 1 is like com.datadog.android.log.internal.logger.LogcatLogHandler.handleLog
            val declaringClass = groups[1].split('.')
                .dropLast(1)
                .joinToString(separator = ".")
            val methodName = groups[1].split('.').last()

            // group 2 is like LogcatLogHandler.kt:31
            val (fileName, lineNumber) = groups[2].split(':')

            StackTraceElement(declaringClass, methodName, fileName, lineNumber.toInt())
        }.toTypedArray()

        // When
        val element = testedHandler.findValidCallStackElement(stackTrace)

        // Then
        checkNotNull(element)
        assertThat(element.className)
            .isEqualTo("com.datadog.android.sample.home.HomeFragment")
    }
}
