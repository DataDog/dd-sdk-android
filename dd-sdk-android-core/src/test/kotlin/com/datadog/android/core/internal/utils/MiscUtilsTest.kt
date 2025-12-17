/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.utils

import com.datadog.android.api.InternalLogger
import com.datadog.android.core.internal.utils.JsonSerializer.ITEM_SERIALIZATION_ERROR
import com.datadog.android.core.internal.utils.JsonSerializer.safeMapValuesToJson
import com.datadog.android.internal.time.TimeProvider
import com.datadog.android.internal.utils.NULL_MAP_VALUE
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.verifyLog
import com.datadog.tools.unit.forge.anException
import com.datadog.tools.unit.forge.exhaustiveAttributes
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.AdvancedForgery
import fr.xgouchet.elmyr.annotation.BoolForgery
import fr.xgouchet.elmyr.annotation.FloatForgery
import fr.xgouchet.elmyr.annotation.Forgery
import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.LongForgery
import fr.xgouchet.elmyr.annotation.MapForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.annotation.StringForgeryType
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@ForgeConfiguration(Configurator::class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
internal class MiscUtilsTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockTimeProvider: TimeProvider

    @LongForgery(min = 0L)
    var fakeCurrentTimeNs = 0L

    @LongForgery(min = 0L, max = 2L)
    var fakeDelaySeconds = 0L

    @Test
    fun `M repeat max N times W retryWithDelay { success = false }`(forge: Forge) {
        // Given
        val fakeTimes = forge.anInt(min = 1, max = 10)
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenReturn(false)
        stubTimeProviderWithDelay()

        // When
        val wasSuccessful = retryWithDelay(mockedBlock, fakeTimes, fakeDelayNs, mockInternalLogger, mockTimeProvider)

        // Then
        assertThat(wasSuccessful).isFalse()
        verify(mockedBlock, times(fakeTimes)).invoke()
    }

    @Test
    fun `M execute the block in a delayed loop W retryWithDelay`(forge: Forge) {
        // Given
        val fakeTimes = forge.anInt(min = 1, max = 4)
        val fakeDelay = TimeUnit.SECONDS.toNanos(forge.aLong(min = 0, max = 2))
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenReturn(false)
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()).thenAnswer { System.nanoTime() }

        // When
        val executionTime = measureNanoTime {
            retryWithDelay(mockedBlock, fakeTimes, fakeDelay, mockInternalLogger, mockTimeProvider)
        }

        // Then
        assertThat(executionTime).isCloseTo(
            fakeTimes * fakeDelay,
            Offset.offset(TimeUnit.SECONDS.toNanos(1))
        )
    }

    @Test
    fun `M do nothing W retryWithDelay { times less or equal than 0 }`(forge: Forge) {
        // Given
        val mockedBlock: () -> Boolean = mock()

        // When
        retryWithDelay(mockedBlock, forge.anInt(Int.MIN_VALUE, 1), fakeDelayNs, mockInternalLogger, mockTimeProvider)

        // Then
        verifyNoInteractions(mockedBlock)
    }

    @Test
    fun `M repeat until success W retryWithDelay { result false }`() {
        // Given
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenReturn(false).thenReturn(true)
        stubTimeProviderWithDelay()

        // When
        val wasSuccessful = retryWithDelay(mockedBlock, 3, fakeDelayNs, mockInternalLogger, mockTimeProvider)

        // Then
        assertThat(wasSuccessful).isTrue()
        verify(mockedBlock, times(2)).invoke()
    }

    @Test
    fun `M repeat until success W retryWithDelay { exception }`(
        @Forgery exception: Exception
    ) {
        // Given
        val mockedBlock: () -> Boolean = mock()
        whenever(mockedBlock.invoke()).thenThrow(exception).thenReturn(true)
        stubTimeProviderWithDelay()

        // When
        val wasSuccessful = retryWithDelay(mockedBlock, 3, fakeDelayNs, mockInternalLogger, mockTimeProvider)

        // Then
        assertThat(wasSuccessful).isTrue()
        verify(mockedBlock, times(2)).invoke()
    }

    @Test
    fun `M provide the relevant JsonElement W toJsonElement { on Kotlin object }`(forge: Forge) {
        // Given
        val attributes = forge.exhaustiveAttributes().toMutableMap()
        attributes[forge.aString()] = NULL_MAP_VALUE
        attributes[forge.aString()] = JsonNull.INSTANCE

        // When
        attributes.forEach {
            // be careful here, we shouldn't pass `it`, because it has Map.Entry type, so will fall
            // always into `else` branch of underlying assertion
            val jsonElement = JsonSerializer.toJsonElement(it.value)
            assertJsonElement(it.value, jsonElement)
        }
    }

    @Test
    fun `M map values to JSON without throwing W safeMapValuesToJson()`(forge: Forge) {
        // Given
        val attributes = forge.exhaustiveAttributes().toMutableMap()
        val fakeException = forge.anException()
        val faultyKey = forge.anAlphabeticalString()
        val faultyItem = object {
            override fun toString(): String {
                throw fakeException
            }
        }
        val mockInternalLogger = mock<InternalLogger>()

        // When
        val mapped = attributes.apply { this += faultyKey to faultyItem }
            .safeMapValuesToJson(mockInternalLogger)

        // Then
        assertThat(mapped).hasSize(attributes.size - 1)
        assertThat(mapped.values).doesNotContainNull()
        assertThat(mapped).doesNotContainKey(faultyKey)

        mockInternalLogger
            .verifyLog(
                level = InternalLogger.Level.ERROR,
                targets = listOf(InternalLogger.Target.USER, InternalLogger.Target.TELEMETRY),
                message = ITEM_SERIALIZATION_ERROR.format(Locale.US, faultyKey),
                throwable = fakeException
            )
    }

    @Test
    fun `M return null W fromJsonElement() {JsonNull}`() {
        // Given
        val json = JsonNull.INSTANCE

        // When
        val mapped = json.fromJsonElement()

        // Then
        assertThat(mapped).isNull()
    }

    @Test
    fun `M return value W fromJsonElement() {JsonPrimitive boolean}`(
        @BoolForgery value: Boolean
    ) {
        // Given
        val json = JsonPrimitive(value)

        // When
        val mapped = json.fromJsonElement()

        // Then
        assertThat(mapped).isEqualTo(value)
    }

    @Test
    fun `M return value W fromJsonElement() {JsonPrimitive int}`(
        @IntForgery value: Int
    ) {
        // Given
        val json = JsonPrimitive(value)

        // When
        val mapped = json.fromJsonElement()

        // Then
        assertThat(mapped).isEqualTo(value)
    }

    @Test
    fun `M return value W fromJsonElement() {JsonPrimitive float}`(
        @FloatForgery value: Float
    ) {
        // Given
        val json = JsonPrimitive(value)

        // When
        val mapped = json.fromJsonElement()

        // Then
        assertThat(mapped).isEqualTo(value)
    }

    @Test
    fun `M return value W fromJsonElement() {JsonPrimitive String}`(
        @StringForgery value: String
    ) {
        // Given
        val json = JsonPrimitive(value)

        // When
        val mapped = json.fromJsonElement()

        // Then
        assertThat(mapped).isEqualTo(value)
    }

    @Test
    fun `M return value W fromJsonElement() {Json Object}`(
        @MapForgery(
            key = AdvancedForgery(string = [StringForgery(StringForgeryType.ALPHABETICAL)]),
            value = AdvancedForgery(string = [StringForgery()])
        ) value: Map<String, String>
    ) {
        // Given
        val json = JsonObject()
        value.forEach { (k, v) ->
            json.addProperty(k, v)
        }

        // When
        val mapped = json.fromJsonElement()

        // Then
        assertThat(mapped).isEqualTo(value)
    }

    // region Internal

    private fun assertJsonElement(kotlinObject: Any?, jsonElement: JsonElement) {
        when (kotlinObject) {
            NULL_MAP_VALUE -> assertThat(jsonElement).isEqualTo(JsonNull.INSTANCE)
            null -> assertThat(jsonElement).isEqualTo(JsonNull.INSTANCE)
            JsonNull.INSTANCE -> assertThat(jsonElement).isEqualTo(JsonNull.INSTANCE)
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
                kotlinObject.map { JsonSerializer.toJsonElement(it) }
            )

            is Map<*, *> -> assertThat(jsonElement.asJsonObject).satisfies {
                assertThat(kotlinObject.keys.map { key -> key.toString() })
                    .containsExactlyElementsOf(it.keySet())
                kotlinObject.entries.forEach { entry ->
                    assertJsonElement(entry.value, it[entry.key.toString()])
                }
            }

            is JSONArray -> assertThat(jsonElement.asJsonArray.toString())
                .isEqualTo(kotlinObject.toString())

            is JSONObject -> assertThat(jsonElement.asJsonObject.toString())
                .isEqualTo(kotlinObject.toString())

            else -> assertThat(jsonElement.asString).isEqualTo(kotlinObject.toString())
        }
    }

    private val fakeDelayNs: Long
        get() = TimeUnit.SECONDS.toNanos(fakeDelaySeconds)

    private fun stubTimeProviderWithDelay() {
        whenever(mockTimeProvider.getDeviceElapsedTimeNs()).thenAnswer {
            fakeCurrentTimeNs += fakeDelayNs
            fakeCurrentTimeNs
        }
    }

    // endregion
}
