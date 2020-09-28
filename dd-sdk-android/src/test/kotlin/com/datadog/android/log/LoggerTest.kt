/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log

import android.util.Log
import com.datadog.android.core.internal.utils.NULL_MAP_VALUE
import com.datadog.android.log.internal.logger.LogHandler
import com.datadog.android.utils.forge.Configurator
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.isNull
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
@MockitoSettings
@ForgeConfiguration(Configurator::class)
internal class LoggerTest {

    lateinit var testedLogger: Logger

    @Mock
    lateinit var mockLogHandler: LogHandler

    lateinit var fakeMessage: String

    @BeforeEach
    fun `set up`(forge: Forge) {
        fakeMessage = forge.anAlphabeticalString()
        testedLogger = Logger(mockLogHandler)
    }

    // region Log

    @Test
    fun `logger logs message with verbose level`() {
        testedLogger.v(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.VERBOSE,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `logger logs message with verbose level and custom timestamp`(forge: Forge) {
        val timestamp = forge.aLong()
        val level = forge.anInt()
        testedLogger.internalLog(level, fakeMessage, null, emptyMap(), timestamp)

        verify(mockLogHandler)
            .handleLog(
                level,
                fakeMessage,
                null,
                emptyMap(),
                emptySet(),
                timestamp
            )
    }

    @Test
    fun `logger logs message with debug level`() {
        testedLogger.d(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.DEBUG,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `logger logs message with info level`() {
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `logger logs message with warning level`() {
        testedLogger.w(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.WARN,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `logger logs message with error level`() {
        testedLogger.e(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.ERROR,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `logger logs message with assert level`() {
        testedLogger.wtf(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.ASSERT,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    // endregion

    // region throwable
    @Test
    fun `log verbose with exception`(@Forgery throwable: Throwable) {
        testedLogger.v(fakeMessage, throwable)

        verify(mockLogHandler)
            .handleLog(
                Log.VERBOSE,
                fakeMessage,
                throwable,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `log debug with exception`(@Forgery throwable: Throwable) {
        testedLogger.d(fakeMessage, throwable)

        verify(mockLogHandler)
            .handleLog(
                Log.DEBUG,
                fakeMessage,
                throwable,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `log info with exception`(@Forgery throwable: Throwable) {
        testedLogger.i(fakeMessage, throwable)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                throwable,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `log warning with exception`(@Forgery throwable: Throwable) {
        testedLogger.w(fakeMessage, throwable)

        verify(mockLogHandler)
            .handleLog(
                Log.WARN,
                fakeMessage,
                throwable,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `log error with exception`(@Forgery throwable: Throwable) {
        testedLogger.e(fakeMessage, throwable)

        verify(mockLogHandler)
            .handleLog(
                Log.ERROR,
                fakeMessage,
                throwable,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `log wtf with exception`(@Forgery throwable: Throwable) {
        testedLogger.wtf(fakeMessage, throwable)

        verify(mockLogHandler)
            .handleLog(
                Log.ASSERT,
                fakeMessage,
                throwable,
                emptyMap(),
                emptySet()
            )
    }

    // endregion

    // region addAttribute

    @Test
    fun `add boolean attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aBool()

        testedLogger.addAttribute(key, value)
        testedLogger.v(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.VERBOSE,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add int attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.anInt()

        testedLogger.addAttribute(key, value)
        testedLogger.d(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.DEBUG,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add long attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aLong()

        testedLogger.addAttribute(key, value)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add float attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aFloat()

        testedLogger.addAttribute(key, value)
        testedLogger.w(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.WARN,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add double attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aDouble()

        testedLogger.addAttribute(key, value)
        testedLogger.e(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.ERROR,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add String attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()

        testedLogger.addAttribute(key, value)
        testedLogger.wtf(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.ASSERT,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add null String attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value: String? = null

        testedLogger.addAttribute(key, value)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                mapOf(key to NULL_MAP_VALUE),
                emptySet()
            )
    }

    @Test
    fun `add Date attribute to logger`(forge: Forge, @Forgery value: Date) {
        val key = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add JsonObject attribute to logger`(forge: Forge, @Forgery value: JsonObject) {
        val key = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `add JsonArray attribute to logger`(forge: Forge, @Forgery value: JsonArray) {
        val key = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    // endregion

    // region removeAttribute

    @Test
    fun `remove boolean attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aBool()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.v(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.VERBOSE,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove int attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.anInt()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.d(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.DEBUG,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove long attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aLong()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove float attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aFloat()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.w(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.WARN,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove double attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aDouble()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.e(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.ERROR,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove null String attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value: String? = null

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.wtf(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.ASSERT,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove String attribute to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove Date attribute to logger`(forge: Forge, @Forgery value: Date) {
        val key = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, value)
        testedLogger.removeAttribute(key)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    // endregion

    // region Local Attributes

    @Test
    fun `log message with local attributes`(forge: Forge) {

        val key = forge.anAlphabeticalString()
        val value = forge.anInt()

        testedLogger.v(fakeMessage, null, mapOf(key to value))

        verify(mockLogHandler)
            .handleLog(
                Log.VERBOSE,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `log message with local attributes and timestamp`(forge: Forge) {
        val timestamp = forge.aLong()
        val key = forge.anAlphabeticalString()
        val value = forge.anInt()
        val level = forge.anInt()

        testedLogger.internalLog(level, fakeMessage, null, mapOf(key to value), timestamp)

        verify(mockLogHandler)
            .handleLog(
                level,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet(),
                timestamp
            )
    }

    @Test
    fun `log message with local attributes (null value)`(forge: Forge) {

        val key = forge.anAlphabeticalString()
        val value: Any? = null

        testedLogger.d(fakeMessage, null, mapOf(key to value))

        verify(mockLogHandler)
            .handleLog(
                Log.DEBUG,
                fakeMessage,
                null,
                mapOf(key to value),
                emptySet()
            )
    }

    @Test
    fun `log message with local attributes override logger value`(forge: Forge) {

        val key = forge.anAlphabeticalString()
        val loggerValue = forge.aFloat()
        val localValue = forge.anAlphabeticalString()

        testedLogger.addAttribute(key, loggerValue)
        testedLogger.i(fakeMessage, null, mapOf(key to localValue))

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                mapOf(key to localValue),
                emptySet()
            )
    }

    @Test
    fun `log message without local attributes after message with local attributes`(forge: Forge) {

        val key = forge.anAlphabeticalString()
        val value = forge.anInt()
        val message1 = forge.anAlphabeticalString()
        val message2 = forge.anAlphabeticalString()

        testedLogger.w(message1, null, mapOf(key to value))
        testedLogger.e(message2)

        inOrder(mockLogHandler) {
            verify(mockLogHandler)
                .handleLog(
                    eq(Log.WARN),
                    eq(message1),
                    isNull(),
                    eq(mapOf(key to value)),
                    eq(emptySet()),
                    isNull()
                )
            verify(mockLogHandler)
                .handleLog(
                    eq(Log.ERROR),
                    eq(message2),
                    isNull(),
                    eq(emptyMap()),
                    eq(emptySet()),
                    isNull()
                )
        }
    }

    // endregion

    // region Tags

    @Test
    fun `add simple tag to logger`(forge: Forge) {
        val tag = forge.anAlphabeticalString()

        testedLogger.addTag(tag)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                setOf(tag)
            )
    }

    @Test
    fun `add key-value tag to logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()

        testedLogger.addTag(key, value)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                setOf("$key:$value")
            )
    }

    @Test
    fun `add multiple tags with same key`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value1 = forge.anAlphabeticalString()
        val value2 = forge.anAlphabeticalString()
        val value3 = forge.anAlphabeticalString()

        testedLogger.addTag(key, value1)
        testedLogger.addTag(key, value2)
        testedLogger.addTag(key, value3)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                setOf("$key:$value1", "$key:$value2", "$key:$value3")
            )
    }

    // endregion

    // region Remove Tags

    @Test
    fun `remove tag from logger`(forge: Forge) {
        val tag = forge.anAlphabeticalString()

        testedLogger.addTag(tag)
        testedLogger.removeTag(tag)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove tag with key from logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value = forge.anAlphabeticalString()

        testedLogger.addTag(key, value)
        testedLogger.removeTagsWithKey(key)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    @Test
    fun `remove all tags with key from logger`(forge: Forge) {
        val key = forge.anAlphabeticalString()
        val value1 = forge.anAlphabeticalString()
        val value2 = forge.anAlphabeticalString()
        val value3 = forge.anAlphabeticalString()

        testedLogger.addTag(key, value1)
        testedLogger.addTag(key, value2)
        testedLogger.addTag(key, value3)
        testedLogger.removeTagsWithKey(key)
        testedLogger.i(fakeMessage)

        verify(mockLogHandler)
            .handleLog(
                Log.INFO,
                fakeMessage,
                null,
                emptyMap(),
                emptySet()
            )
    }

    // endregion

    // region Multi Thread Access

    @Test
    fun `adding and removing tags is thread safe`(forge: Forge) {
        val asyncOperations = 100
        val syncOperations = 10
        val randomTags =
            forge.aList(asyncOperations) {
                "${forge.aString(syncOperations)}:${forge.aString(syncOperations)}"
            }
        val countDownLatch = CountDownLatch(asyncOperations)
        var logDebugExecutionCalls = 0
        repeat(asyncOperations) {

            val closure = when (forge.anInt(min = 0, max = 3)) {
                0 -> {
                    {
                        repeat(syncOperations) {
                            repeat(syncOperations) {
                                val randomTagIndex = forge.anInt(0, asyncOperations)
                                testedLogger.addTag(randomTags[randomTagIndex])
                            }
                        }
                    }
                }
                1 -> {
                    {
                        repeat(syncOperations) {
                            val randomTagIndex = forge.anInt(0, asyncOperations)
                            testedLogger.removeTag(randomTags[randomTagIndex])
                        }
                    }
                }
                2 -> {
                    {
                        repeat(syncOperations) {
                            val randomTagIndex = forge.anInt(0, asyncOperations)
                            val tagKey = randomTags[randomTagIndex].split(":").first()
                            testedLogger.removeTagsWithKey(tagKey)
                        }
                    }
                }
                3 -> {
                    logDebugExecutionCalls++
                    {
                        val attributes =
                            forge.aMap<String, Any>(size = forge.anInt(min = 1, max = 5)) {
                                forge.aString(size = syncOperations) to
                                    forge.aString(size = syncOperations)
                            }
                        testedLogger.d(
                            forge.aString(size = syncOperations),
                            attributes = attributes
                        )
                    }
                }
                else -> {
                    { }
                }
            }
            async(countDownLatch, closure)
        }

        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockLogHandler, times(logDebugExecutionCalls)).handleLog(
            eq(Log.DEBUG),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    @Test
    fun `adding and removing attributes is thread safe`(forge: Forge) {
        val asyncOperations = 100
        val syncedOperations = 10
        val randomAttributes = forge.aList(size = asyncOperations) {
            forge.aString(syncedOperations) to forge.aString(syncedOperations)
        }
        val countDownLatch = CountDownLatch(asyncOperations)
        var logDebugExecutionCalls = 0
        repeat(asyncOperations) {

            val closure = when (forge.anInt(min = 0, max = 2)) {
                0 -> {
                    {
                        repeat(syncedOperations) {
                            val randomAttributeIndex = forge.anInt(0, asyncOperations)
                            testedLogger.addAttribute(
                                randomAttributes[randomAttributeIndex].first,
                                randomAttributes[randomAttributeIndex].second
                            )
                        }
                    }
                }
                1 -> {
                    {
                        repeat(syncedOperations) {
                            val randomAttributeIndex = forge.anInt(0, asyncOperations)
                            testedLogger.removeAttribute(
                                randomAttributes[randomAttributeIndex].first
                            )
                        }
                    }
                }
                2 -> {
                    logDebugExecutionCalls++
                    {
                        val attributes =
                            forge.aMap<String, Any>(size = forge.anInt(min = 1, max = 5)) {
                                forge.aString(size = syncedOperations) to
                                    forge.aString(size = syncedOperations)
                            }
                        testedLogger.d(
                            forge.aString(size = syncedOperations),
                            attributes = attributes
                        )
                    }
                }
                else -> {
                    { }
                }
            }
            async(countDownLatch, closure)
        }

        countDownLatch.await(5, TimeUnit.SECONDS)
        verify(mockLogHandler, times(logDebugExecutionCalls)).handleLog(
            eq(Log.DEBUG),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull(),
            anyOrNull()
        )
    }

    // endregion

    // region internal

    private fun async(countDownLatch: CountDownLatch, closure: () -> Unit) {
        Thread {
            closure()
            countDownLatch.countDown()
        }.start()
    }

    // endregion
}
