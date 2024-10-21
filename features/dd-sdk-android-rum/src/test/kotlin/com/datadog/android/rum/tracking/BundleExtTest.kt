package com.datadog.android.rum.tracking

import android.os.Bundle
import com.datadog.android.rum.utils.forge.Configurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
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
