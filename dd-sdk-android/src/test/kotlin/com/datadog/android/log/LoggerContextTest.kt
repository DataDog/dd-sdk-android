/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.log

import com.datadog.android.log.assertj.LogAssert.Companion.assertThat
import com.datadog.android.log.forge.Configurator
import com.datadog.android.log.internal.Log
import com.datadog.android.log.internal.LogStrategy
import com.datadog.android.log.internal.LogWriter
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class LoggerContextTest {

    lateinit var testedLogger: Logger

    @Mock
    lateinit var mockLogStrategy: LogStrategy
    @Mock
    lateinit var mockLogWriter: LogWriter

    @BeforeEach
    fun `set up logger`(forge: Forge) {
        whenever(mockLogStrategy.getLogWriter()) doReturn mockLogWriter

        testedLogger = Logger.Builder()
            .withLogStrategy(mockLogStrategy)
            .build()
    }

    // region addAttribute

    @Test
    fun `add boolean attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aBool()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    @Test
    fun `add int attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.anInt()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    @Test
    fun `add long attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aLong()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    @Test
    fun `add float attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aFloat()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    @Test
    fun `add double attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aDouble()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    @Test
    fun `add String attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    @Test
    fun `add Date attribute to logger`(forge: Forge, @Forgery value: Date) {
        val key = forge.anAlphabeticalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasAttributes(mapOf(key to value))
        }
    }

    // endregion

    // region removeAttribute

    @Test
    fun `remove boolean attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aBool()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    @Test
    fun `remove int attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.anInt()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    @Test
    fun `remove long attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aLong()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    @Test
    fun `remove float attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aFloat()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    @Test
    fun `remove double attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aDouble()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    @Test
    fun `remove String attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    @Test
    fun `remove Date attribute to logger`(forge: Forge, @Forgery value: Date) {
        val key = forge.anAlphabeticalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(message)
        testedLogger.removeAttribute(key)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasAttributes(mapOf(key to value))
            assertThat(lastValue.attributes)
                .isEmpty()
        }
    }

    // endregion

    // region Tags

    @Test
    fun `add tag to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue)
                .hasTags(listOf("$key:$value"))
        }
    }

    @Test
    fun `ignore invalid tag - start with a letter`(forge: Forge) {
        val key = forge.aStringMatching("\\d[a-z]+")
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .isEmpty()
        }
    }

    @Test
    fun `replace illegal characters`(forge: Forge) {
        val validPart = forge.anAlphabeticalString(size = 3)
        val invalidPart = forge.aString {
            anElementFrom(
                ',', '?', '%', '(', ')', '[', ']', '{', '}',
                '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', '\u0020'
            )
        }
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag("$validPart$invalidPart", value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            val converted = '_' * invalidPart.length
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .containsExactly("$validPart$converted:$value")
        }
    }

    @Test
    fun `convert uppercase to lowercase`(forge: Forge) {
        val key = forge.anAlphabeticalString(case = Case.UPPER)
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            val converted = key.toLowerCase()
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .containsExactly("$converted:$value")
        }
    }

    @Test
    fun `trim tags over 200 characters`(forge: Forge) {
        val tag = forge.anAlphabeticalString(size = forge.aSmallInt() + 200)
        val message = forge.anAlphabeticalString()

        testedLogger.addTag(tag)
        testedLogger.i(message)

        argumentCaptor<Log> {
            val trimmed = tag.substring(0, 200)
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .containsExactly(trimmed)
        }
    }

    @Test
    fun `ignore reserved tag keys`(forge: Forge) {
        val key = forge.anElementFrom(
            "host", "device", "source", "service"
        )
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag(key, value)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .isEmpty()
        }
    }

    @Test
    fun `ignore reserved tag keys (workaround 1)`(forge: Forge) {
        val key = forge.anElementFrom(
            "host", "device", "source", "service"
        )
        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag("$key:$value")
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .isEmpty()
        }
    }

    @Test
    fun `ignore reserved tag keys (workaround 2)`(forge: Forge) {
        val key = forge.randomizeCase(
            forge.anElementFrom(
                "host", "device", "source", "service"
            )
        )

        val value = forge.aNumericalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag("$key:$value")
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter).writeLog(capture())
            assertThat(lastValue.tags)
                .isEmpty()
        }
    }

    // endregion

    // region remove Tags

    @Test
    fun `remove tag from logger`(forge: Forge) {
        val tag = forge.anAlphabeticalString()
        val message = forge.anAlphabeticalString()

        testedLogger.addTag(tag)
        testedLogger.i(message)
        testedLogger.removeTag(tag)
        testedLogger.i(message)

        argumentCaptor<Log> {
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasTags(listOf(tag))
            assertThat(lastValue.tags)
                .isEmpty()
        }
    }

    @Test
    fun `remove converted tag from logger`(forge: Forge) {
        val validPart = forge.randomizeCase(forge.anAlphabeticalString(size = 10))
        val invalidPart = forge.aString {
            anElementFrom(
                ',', '?', '%', '(', ')', '[', ']', '{', '}',
                '\u0009', '\u000A', '\u000B', '\u000C', '\u000D', '\u0020'
            )
        }
        val value = forge.aNumericalString()
        val tag = "$validPart:$invalidPart:$value"

        val message = forge.anAlphabeticalString()

        testedLogger.addTag(tag)
        testedLogger.i(message)
        testedLogger.removeTag(tag)
        testedLogger.i(message)

        argumentCaptor<Log> {
            val converted = '_' * invalidPart.length
            verify(mockLogWriter, times(2)).writeLog(capture())
            assertThat(firstValue)
                .hasTags(listOf("${validPart.toLowerCase()}:$converted:$value"))
            assertThat(lastValue.tags)
                .isEmpty()
        }
    }

    // endregion
}

private operator fun Char.times(i: Int): String {
    check(i >= 0) { "Can't repeat character negative times " }
    return String(CharArray(i) { this })
}

private fun Forge.randomizeCase(string: String): String {
    return string.toCharArray().joinToString("") {
        val s = it.toString()
        if (aBool()) s.toLowerCase() else s.toUpperCase()
    }
}
