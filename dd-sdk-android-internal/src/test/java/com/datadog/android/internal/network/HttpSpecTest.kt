/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.internal.network

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.ListAssert
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

internal class HttpSpecTest {

    @Test
    fun `M return all declared constants W values()`() {
        // Given
        val declaredConstants = HttpSpec.StatusCode.getDeclaredConstants<Int>()

        // When
        val values = HttpSpec.StatusCode.values()

        // Then
        assertThat(values).isEqualTo(declaredConstants, "StatusCode")
    }

    @Test
    fun `M return all declared constants W Method values()`() {
        // Given
        val declaredConstants = HttpSpec.Method.getDeclaredConstants<String>()

        // When
        val values = HttpSpec.Method.values()

        // Then
        assertThat(values).isEqualTo(declaredConstants, "Method")
    }

    @Test
    fun `M return all declared constants W ContentType values()`() {
        // Given
        val declaredConstants = HttpSpec.ContentType.getDeclaredConstants<String>()

        // When
        val values = HttpSpec.ContentType.values()

        // Then
        assertThat(values).isEqualTo(declaredConstants, "ContentType")
    }

    @Test
    fun `M return all declared constants W Header values()`() {
        // Given
        val declaredConstants = HttpSpec.Header.getDeclaredConstants<String>()

        // When
        val values = HttpSpec.Header.values()

        // Then
        assertThat(values).isEqualTo(declaredConstants, "Header")
    }

    companion object {
        private fun <C> ListAssert<C>.isEqualTo(
            declaredConstants: List<Pair<String, C>>,
            objectName: String
        ) = apply {
            hasSameSizeAs(declaredConstants)
            declaredConstants.forEach { (name, value) ->
                withFailMessage { "$objectName constant $name=$value is not included in values()" }
                    .contains(value)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified C : Any> Any.getDeclaredConstants(): List<Pair<String, C>> {
            val constantType = C::class.javaPrimitiveType ?: C::class.java
            return this::class.java.declaredFields
                .filter { Modifier.isStatic(it.modifiers) && Modifier.isPublic(it.modifiers) }
                .filter { it.type == constantType }
                .map { it.name to it.get(null) as C }
        }
    }
}
