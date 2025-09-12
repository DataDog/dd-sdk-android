/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.compose

import fr.xgouchet.elmyr.annotation.IntForgery
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(ForgeExtension::class)
internal class HashcodeBackStackKeyResolverTest {

    private val testedResolver = HashcodeBackStackKeyResolver<Any>()

    @Test
    fun `M return item hashcode as string W getStableKey()`(
        @StringForgery testItem: String
    ) {
        // Given
        val expectedKey = testItem.hashCode().toString()

        // When
        val actualKey = testedResolver.getStableKey(testItem)

        // Then
        assertThat(actualKey).isEqualTo(expectedKey)
    }

    @Test
    fun `M return item hashcode as string W getStableKey {data class}`(
        @StringForgery testItemId: String,
        @IntForgery testItemValue: Int
    ) {
        // Given
        val testItem = TestDataClass(testItemId, testItemValue)
        val identicalItem = TestDataClass(testItemId, testItemValue)
        val expectedKey = identicalItem.hashCode().toString()

        // When
        val actualKey = testedResolver.getStableKey(testItem)

        // Then
        assertThat(actualKey).isEqualTo(expectedKey)
    }

    @Test
    fun `M return item hashcode as string W getStableKey {integer}`(
        @IntForgery testItem: Int
    ) {
        // Given
        val expectedKey = testItem.hashCode().toString()

        // When
        val actualKey = testedResolver.getStableKey(testItem)

        // Then
        assertThat(actualKey).isEqualTo(expectedKey)
    }

    data class TestDataClass(val id: String, val value: Int)
}
