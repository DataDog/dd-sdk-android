/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.tracking

import android.os.BadParcelableException
import android.os.Bundle
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(ForgeExtension::class),
    ExtendWith(MockitoExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
class BundleExtTest {
    @Test
    fun `M return empty map W convertToRumViewAttributes() {null bundle}`() {
        // Given
        val bundle: Bundle? = null

        // When
        val result = bundle.convertToRumViewAttributes()

        // Then

        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W convertToRumViewAttributes() {empty bundle}`() {
        // Given
        val bundle = Bundle()

        // When
        val result = bundle.convertToRumViewAttributes()

        // Then

        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W convertToRumViewAttributes() {parcelize error on keySet}`(
        @Mock mockBundle: Bundle
    ) {
        // Given
        whenever(mockBundle.keySet()).thenThrow(BadParcelableException("simulated"))

        // When
        val result = mockBundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W convertToRumViewAttributes() {NoClassDefFoundError on keySet}`(
        @Mock mockBundle: Bundle
    ) {
        // Given
        whenever(mockBundle.keySet()).thenThrow(NoClassDefFoundError("Invalid descriptor: ."))

        // When
        val result = mockBundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W convertToRumViewAttributes() {parcelize error on get}`(
        @Mock mockBundle: Bundle,
        @StringForgery fakeKey: String
    ) {
        // Given
        whenever(mockBundle.keySet()).thenReturn(setOf(fakeKey))
        @Suppress("DEPRECATION")
        whenever(mockBundle.get(fakeKey)).thenThrow(BadParcelableException("simulated"))

        // When
        val result = mockBundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return empty map W convertToRumViewAttributes() {NoClassDefFoundError on get}`(
        @Mock mockBundle: Bundle,
        @StringForgery fakeKey: String
    ) {
        // Given
        whenever(mockBundle.keySet()).thenReturn(setOf(fakeKey))
        @Suppress("DEPRECATION")
        whenever(mockBundle.get(fakeKey)).thenThrow(NoClassDefFoundError("Invalid descriptor: ."))

        // When
        val result = mockBundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEmpty()
    }

    @Test
    fun `M return map with String attributes W convertToRumViewAttributes() {bundle}`(
        forge: Forge
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        val bundle = Bundle()
        repeat(forge.aSmallInt()) {
            val key = forge.anAlphabeticalString()
            val value = forge.aNullable { aString() }
            bundle.putString(key, value)
            expectedAttributes["view.arguments.$key"] = value
        }

        // When
        val result = bundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEqualTo(expectedAttributes)
    }

    @Test
    fun `M return map with Int attributes W convertToRumViewAttributes() {bundle}`(
        forge: Forge
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        val bundle = Bundle()
        repeat(forge.aSmallInt()) {
            val key = forge.anAlphabeticalString()
            val value = forge.anInt()
            bundle.putInt(key, value)
            expectedAttributes["view.arguments.$key"] = value
        }

        // When
        val result = bundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEqualTo(expectedAttributes)
    }

    @Test
    fun `M return map with Float attributes W convertToRumViewAttributes() {bundle}`(
        forge: Forge
    ) {
        // Given
        val expectedAttributes = mutableMapOf<String, Any?>()
        val bundle = Bundle()
        repeat(forge.aSmallInt()) {
            val key = forge.anAlphabeticalString()
            val value = forge.aFloat()
            bundle.putFloat(key, value)
            expectedAttributes["view.arguments.$key"] = value
        }

        // When
        val result = bundle.convertToRumViewAttributes()

        // Then
        assertThat(result).isEqualTo(expectedAttributes)
    }
}
