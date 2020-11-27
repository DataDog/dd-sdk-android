/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.forge.exhaustiveAttributes
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import com.nhaarman.mockitokotlin2.whenever
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
internal class MiscUtilsTest {

    // region UnitTests

    @Test
    fun `M repeat max N times W retryWithDelay { success = false }`(forge: Forge) {
        // GIVEN
        val fakeTimes = forge.anInt(min = 1, max = 10)
        val fakeDelay = TimeUnit.SECONDS.toNanos(forge.aLong(min = 0, max = 2))
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenReturn(false)

        // WHEN
        val wasSuccessful = retryWithDelay(mockedBlock, fakeTimes, fakeDelay)

        // THEN
        assertThat(wasSuccessful).isFalse()
        verify(mockedBlock, times(fakeTimes)).invoke()
    }

    @Test
    fun `M execute the block in a delayed loop W retryWithDelay`(forge: Forge) {
        // GIVEN
        val fakeTimes = forge.anInt(min = 1, max = 4)
        val fakeDelay = TimeUnit.SECONDS.toNanos(forge.aLong(min = 0, max = 2))
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenReturn(false)

        // WHEN
        val executionTime = measureNanoTime { retryWithDelay(mockedBlock, fakeTimes, fakeDelay) }

        // THEN
        assertThat(executionTime).isCloseTo(
            fakeTimes * fakeDelay,
            Offset.offset(TimeUnit.SECONDS.toNanos(1))
        )
    }

    @Test
    fun `M do nothing W retryWithDelay { times less or equal than 0 }`(forge: Forge) {
        // GIVEN
        val fakeDelay = TimeUnit.SECONDS.toNanos(forge.aLong(min = 0, max = 2))
        val mockedBlock: () -> Boolean = mock()

        // WHEN
        retryWithDelay(mockedBlock, forge.anInt(Int.MIN_VALUE, 1), fakeDelay)

        // THEN
        verifyZeroInteractions(mockedBlock)
    }

    @Test
    fun `M repeat until success W retryWithDelay`(forge: Forge) {
        // GIVEN
        val fakeDelay = TimeUnit.SECONDS.toNanos(forge.aLong(min = 0, max = 2))
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenReturn(false).thenReturn(true)

        // WHEN
        val wasSuccessful = retryWithDelay(mockedBlock, 3, fakeDelay)

        // THEN
        assertThat(wasSuccessful).isTrue()
        verify(mockedBlock, times(2)).invoke()
    }

    @Test
    fun `M provide the relevant JsonElement W toJsonElement { on Kotlin object }`(forge: Forge) {
        // GIVEN
        val attributes = forge.exhaustiveAttributes().toMutableMap()
        attributes[forge.aString()] = NULL_MAP_VALUE

        // WHEN
        attributes.forEach {
            val jsonElement = it.toJsonElement()
            assertJsonElement(it, jsonElement)
        }
    }

    // endregion

    // region Internal

    private fun assertJsonElement(kotlinObject: Any?, jsonElement: JsonElement) {
        when (kotlinObject) {
            NULL_MAP_VALUE -> assertThat(jsonElement).isEqualTo(JsonNull.INSTANCE)
            null -> assertThat(jsonElement).isEqualTo(JsonNull.INSTANCE)
            is Boolean -> assertThat(jsonElement.asBoolean).isEqualTo(kotlinObject)
            is Int -> assertThat(jsonElement.asInt).isEqualTo(kotlinObject)
            is Long -> assertThat(jsonElement.asLong).isEqualTo(kotlinObject)
            is Float -> assertThat(jsonElement.asFloat).isEqualTo(kotlinObject)
            is Double -> assertThat(jsonElement.asDouble).isEqualTo(kotlinObject)
            is String -> assertThat(jsonElement.asString).isEqualTo(kotlinObject)
            is Date -> assertThat(jsonElement.asLong).isEqualTo(kotlinObject.time)
            is JsonObject -> assertThat(jsonElement.asJsonObject).isEqualTo(kotlinObject)
            is JsonArray -> assertThat(jsonElement.asJsonArray).isEqualTo(kotlinObject)
            is Iterable<*> -> assertThat(jsonElement.asJsonArray).containsExactlyElementsOf(
                kotlinObject.map { it.toJsonElement() }
            )
            else -> assertThat(jsonElement.asString).isEqualTo(kotlinObject.toString())
        }
    }

    // endregion
}
