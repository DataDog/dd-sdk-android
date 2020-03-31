/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal.logger

import com.datadog.android.BuildConfig
import com.datadog.android.Datadog
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class LogcatLogHandlerTest {

    lateinit var testedHandler: LogcatLogHandler

    @StringForgery(StringForgeryType.ALPHABETICAL)
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
}
