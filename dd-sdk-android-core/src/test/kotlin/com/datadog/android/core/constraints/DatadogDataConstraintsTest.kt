/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.constraints

import com.datadog.android.api.InternalLogger
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.utils.times
import com.datadog.android.utils.verifyLog
import fr.xgouchet.elmyr.Case
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.util.Locale

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings()
@ForgeConfiguration(Configurator::class)
internal class DatadogDataConstraintsTest {

    lateinit var testedConstraints: DataConstraints

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @BeforeEach
    fun `set up`() {
        testedConstraints = DatadogDataConstraints(mockInternalLogger)
    }

    // region Tags

    @Test
    fun `keep valid tag`(forge: Forge) {
        val tag = forge.aStringMatching("[a-z]([a-z0-9_:./-]{0,198}[a-z0-9_./-])?")

        val result = testedConstraints.validateTags(listOf(tag))

        assertThat(result).containsOnly(tag)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `ignore invalid tag - start with a letter`(forge: Forge) {
        val key = forge.aStringMatching("\\d[a-z]+")
        val value = forge.aNumericalString()
        val tag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        assertThat(result).isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "\"$tag\" is an invalid tag, and was ignored."
        )
    }

    @Test
    fun `replace illegal characters`(forge: Forge) {
        val validPart = forge.anAlphabeticalString(size = 3)
        val invalidPart = forge.aString {
            anElementFrom(',', '?', '%', '(', ')', '[', ']', '{', '}')
        }
        val value = forge.aNumericalString()
        val tag = "$validPart$invalidPart:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        val converted = '_' * invalidPart.length
        val expectedCorrectedTag = "$validPart$converted:$value"
        assertThat(result)
            .containsOnly(expectedCorrectedTag)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "tag \"$tag\" was modified to \"$expectedCorrectedTag\" to match our constraints."
        )
    }

    @Test
    fun `convert uppercase key to lowercase`(forge: Forge) {
        val key = forge.anAlphabeticalString(case = Case.UPPER)
        val value = forge.aNumericalString()
        val tag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(tag))

        val expectedCorrectedTag = "${key.lowercase(Locale.US)}:$value"
        assertThat(result)
            .containsOnly(expectedCorrectedTag)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "tag \"$tag\" was modified to \"$expectedCorrectedTag\" to match our constraints."
        )
    }

    @Test
    fun `trim tags over 200 characters`(forge: Forge) {
        val tag = forge.anAlphabeticalString(size = forge.aSmallInt() + 200)

        val result = testedConstraints.validateTags(listOf(tag))

        val expectedCorrectedTag = tag.substring(0, 200)
        assertThat(result)
            .containsOnly(expectedCorrectedTag)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "tag \"$tag\" was modified to \"$expectedCorrectedTag\" to match our constraints."
        )
    }

    @Test
    fun `trim tags ending with a colon`(forge: Forge) {
        val expectedCorrectedTag = forge.anAlphabeticalString()

        val result = testedConstraints.validateTags(listOf("$expectedCorrectedTag:"))

        assertThat(result)
            .containsOnly(expectedCorrectedTag)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "tag \"$expectedCorrectedTag:\" was modified to " +
                "\"$expectedCorrectedTag\" to match our constraints."
        )
    }

    @Test
    fun `ignore reserved tag keys`(forge: Forge) {
        val key = forge.anElementFrom("host", "device", "source", "service")
        val value = forge.aNumericalString()
        val invalidTag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(invalidTag))

        assertThat(result)
            .isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "\"$invalidTag\" is an invalid tag, and was ignored."
        )
    }

    @Test
    fun `ignore reserved tag keys (workaround)`(forge: Forge) {
        val key = forge.randomizeCase { anElementFrom("host", "device", "source", "service") }
        val value = forge.aNumericalString()
        val invalidTag = "$key:$value"

        val result = testedConstraints.validateTags(listOf(invalidTag))

        assertThat(result)
            .isEmpty()
        mockInternalLogger.verifyLog(
            InternalLogger.Level.ERROR,
            InternalLogger.Target.USER,
            "\"$invalidTag\" is an invalid tag, and was ignored."
        )
    }

    @Test
    fun `ignore tag if adding more than 100`(forge: Forge) {
        val tags = forge.aList(128) { aStringMatching("[a-z]{1,8}:[0-9]{1,8}") }
        val firstTags = tags.take(100)

        val result = testedConstraints.validateTags(tags)

        val discardedCount = tags.size - 100
        assertThat(result)
            .containsExactlyElementsOf(firstTags)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "too many tags were added, $discardedCount had to be discarded."
        )
    }

    //endregion

    // region Attributes

    @Test
    fun `keep valid attribute`(
        forge: Forge
    ) {
        val key = forge.anAlphabeticalString()
        val value = forge.aNumericalString()

        val result = testedConstraints.validateAttributes(mapOf(key to value))

        assertThat(result)
            .containsEntry(key, value)
        verifyNoInteractions(mockInternalLogger)
    }

    @Test
    fun `M convert nested attribute keys W over 10 levels`(forge: Forge) {
        val topLevels = forge.aList(10) { anAlphabeticalString() }
        val lowerLevels = forge.aList { anAlphabeticalString() }
        val key = (topLevels + lowerLevels).joinToString(".")
        val value = forge.aNumericalString()

        val result = testedConstraints.validateAttributes(mapOf(key to value))

        val expectedKey = topLevels.joinToString(".") + "_" + lowerLevels.joinToString("_")
        assertThat(result)
            .containsEntry(expectedKey, value)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Key \"$key\" was modified to \"$expectedKey\" to match our constraints."
        )
    }

    @Test
    fun `M convert nested attribute keys W over 10 levels and using prefix`(forge: Forge) {
        val keyPrefix = forge
            .aList(5) { forge.anAlphabeticalString() }
            .joinToString(".")
        val topLevels = forge.aList(5) { anAlphabeticalString() }
        val lowerLevels = forge.aList { anAlphabeticalString() }
        val key = (topLevels + lowerLevels).joinToString(".")
        val value = forge.aNumericalString()
        val result =
            testedConstraints.validateAttributes(
                mapOf(key to value),
                keyPrefix = keyPrefix
            )

        val expectedKey = topLevels.joinToString(".") + "_" + lowerLevels.joinToString("_")
        assertThat(result)
            .containsEntry(expectedKey, value)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Key \"$key\" was modified to \"$expectedKey\" to match our constraints."
        )
    }

    @Test
    fun `ignore attribute if adding more than 128`(forge: Forge) {
        val attributes = forge.aList(202) { anAlphabeticalString() to anInt() }.toMap()
        val firstAttributes = attributes.toList().take(128).toMap()

        val result = testedConstraints.validateAttributes(attributes)

        val discardedCount = attributes.size - 128
        assertThat(result)
            .hasSize(128)
            .containsAllEntriesOf(firstAttributes)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Too many attributes were added, $discardedCount had to be discarded."
        )
    }

    @Test
    fun `M use a custom error format W validateAttributes`(forge: Forge) {
        val attributes = forge.aList(202) { anAlphabeticalString() to anInt() }.toMap()
        val firstAttributes = attributes.toList().take(128).toMap()
        val fakeAttributesGroup = forge
            .aList(size = 10) { forge.anAlphabeticalString() }
            .joinToString(".")
        val result = testedConstraints.validateAttributes(
            attributes,
            attributesGroupName = fakeAttributesGroup
        )

        val discardedCount = attributes.size - 128
        assertThat(result)
            .hasSize(128)
            .containsAllEntriesOf(firstAttributes)
        mockInternalLogger.verifyLog(
            InternalLogger.Level.WARN,
            InternalLogger.Target.USER,
            "Too many attributes were added for [$fakeAttributesGroup]," +
                " $discardedCount had to be discarded."
        )
    }

    @Test
    fun `M drop the reserved attributes W validateAttributes { reservedKeys provided }`(
        forge: Forge
    ) {
        // GIVEN
        val attributes = forge.aMap(size = 10) {
            forge.anAlphaNumericalString() to forge.anAlphabeticalString()
        }
        val reservedKeys = attributes.keys.take(2).toHashSet()

        // WHEN
        val sanitizedAttributes =
            testedConstraints.validateAttributes(attributes, reservedKeys = reservedKeys)

        // THEN
        assertThat(sanitizedAttributes)
            .containsExactlyEntriesOf(attributes.filterNot { reservedKeys.contains(it.key) })
    }

    // endregion

    // region Events

    @Test
    fun `M sanitize custom timings and log warning W validateEvent`(
        forge: Forge
    ) {
        // Given
        val expectedSanitizedKeys = mutableSetOf<String>()
        val badToSanitizedKeys = mutableMapOf<String, String>()

        val customTimings = forge.aMap {
            val goodTimingPart = forge.anAlphabeticalString(case = Case.ANY)
            if (forge.aBool()) {
                expectedSanitizedKeys.add(goodTimingPart)
                goodTimingPart to forge.aLong()
            } else {
                val badTimingPart = forge.anAsciiString()
                    .replace(Regex("[a-zA-Z0-9\\-_.@$]"), forge.anElementFrom("%", "*", "!", "&"))

                val badKey = goodTimingPart + badTimingPart
                val sanitizedKey = goodTimingPart + "_".repeat(badTimingPart.length)

                expectedSanitizedKeys.add(sanitizedKey)
                badToSanitizedKeys[badKey] = sanitizedKey

                badKey to forge.aLong()
            }
        }
        val expectedLogs = badToSanitizedKeys.toList().map {
            DatadogDataConstraints.CUSTOM_TIMING_KEY_REPLACED_WARNING.format(
                Locale.US,
                it.first,
                it.second
            )
        }

        // When
        val sanitizedTimings = testedConstraints.validateTimings(customTimings)

        // Then
        assertThat(sanitizedTimings.keys)
            .isEqualTo(expectedSanitizedKeys)

        argumentCaptor<() -> String> {
            verify(mockInternalLogger, times(badToSanitizedKeys.size)).log(
                eq(InternalLogger.Level.WARN),
                eq(InternalLogger.Target.USER),
                capture(),
                isNull(),
                eq(false)
            )

            assertThat(allValues.map { it() })
                .containsExactlyInAnyOrderElementsOf(expectedLogs)
        }
    }

    // endregion
}
