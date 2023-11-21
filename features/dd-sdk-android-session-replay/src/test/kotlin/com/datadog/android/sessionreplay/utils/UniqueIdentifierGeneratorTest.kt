/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.sessionreplay.utils

import android.view.View
import com.datadog.android.sessionreplay.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class UniqueIdentifierGeneratorTest {

    @Test
    fun `M resolve an unique identifier W resolveChildUniqueIdentifier()`(forge: Forge) {
        // Given
        val numberOfCalls = forge.anInt(min = 10, max = 100)

        // When
        val uniqueNumbers = forge.aList(numberOfCalls) { aString() }
            .map { mock<View>() to it }
            .map { UniqueIdentifierGenerator.resolveChildUniqueIdentifier(it.first, it.second) }
            .distinct()

        // Then
        assertThat(uniqueNumbers.size).isEqualTo(numberOfCalls)
    }

    @Test
    fun `M resolve a consistent identifier W resolveChildUniqueIdentifier()`(
        forge: Forge
    ) {
        // Given
        val numberOfCalls = forge.anInt(min = 10, max = 100)
        val keyNames = forge.aList(numberOfCalls) { aString() }
        val parentViews: List<View> = forge.aList(numberOfCalls) { mock() }
        val uniqueNumbers = keyNames
            .mapIndexed { index, key -> parentViews[index] to key }
            .map { UniqueIdentifierGenerator.resolveChildUniqueIdentifier(it.first, it.second) }
            .distinct()

        parentViews.forEachIndexed { index, view ->
            val keyName = UniqueIdentifierGenerator.DATADOG_UNIQUE_IDENTIFIER_KEY_PREFIX +
                keyNames[index]
            whenever(view.getTag(keyName.hashCode())).thenReturn(uniqueNumbers[index])
        }

        // When
        val secondTimeCallNumbers = keyNames
            .mapIndexed { index, key -> parentViews[index] to key }
            .map { UniqueIdentifierGenerator.resolveChildUniqueIdentifier(it.first, it.second) }
            .distinct()

        // Then
        assertThat(uniqueNumbers).isEqualTo(secondTimeCallNumbers)
    }

    @Test
    fun `M use the registered value W resolveChildUniqueIdentifier(){keyName already used}`(
        forge: Forge
    ) {
        // Given
        val numberOfCalls = forge.anInt(min = 10, max = 100)
        val keyNames = forge.aList(numberOfCalls) { aString() }
        val alreadyRegisteredValues = forge.aList(numberOfCalls) { aLong() }

        // When
        val generatedUniqueNumbers = forge.aList<View>(numberOfCalls) { mock() }
            .mapIndexed { index: Int, view: View ->
                val keyName = UniqueIdentifierGenerator.DATADOG_UNIQUE_IDENTIFIER_KEY_PREFIX +
                    keyNames[index]
                whenever(view.getTag(keyName.hashCode()))
                    .thenReturn(alreadyRegisteredValues[index])
                UniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, keyNames[index])
            }
        assertThat(generatedUniqueNumbers).isEqualTo(alreadyRegisteredValues)
    }

    @Test
    fun `M return null W resolveChildUniqueIdentifier(){keyName already used, not a long value}`(
        forge: Forge
    ) {
        // Given
        val numberOfCalls = forge.anInt(min = 10, max = 100)
        val keyNames = forge.aList(numberOfCalls) { aString() }
        val alreadyRegisteredValues = forge.aList(numberOfCalls) { mock<Any>() }

        // When
        val generatedUniqueNumbers = forge.aList<View>(numberOfCalls) { mock() }
            .mapIndexedNotNull { index: Int, view: View ->
                val keyName = UniqueIdentifierGenerator.DATADOG_UNIQUE_IDENTIFIER_KEY_PREFIX +
                    keyNames[index]
                whenever(view.getTag(keyName.hashCode()))
                    .thenReturn(alreadyRegisteredValues[index])
                UniqueIdentifierGenerator.resolveChildUniqueIdentifier(view, keyNames[index])
            }

        assertThat(generatedUniqueNumbers).isEmpty()
    }

    @Test
    fun `M always generate a 32bit Int compatible identifier W resolveChildUniqueIdentifier`(forge: Forge) {
        // Given
        val numberOfCalls = 1000

        // When
        val results = forge.aList<View>(numberOfCalls) { mock() }
            .map {
                UniqueIdentifierGenerator.resolveChildUniqueIdentifier(mock(), forge.aString())
            }

        // Then
        results.forEach {
            if (it != null) {
                assertThat(it).isBetween(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            }
        }
    }
}
