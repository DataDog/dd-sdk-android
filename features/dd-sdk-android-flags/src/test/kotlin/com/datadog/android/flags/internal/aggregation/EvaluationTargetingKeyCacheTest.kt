/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal.aggregation

import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@ExtendWith(ForgeExtension::class)
internal class EvaluationTargetingKeyCacheTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `M reuse matching identity W get()`(fakeConsent: Boolean, @StringForgery fakeKey: String) {
        // Given
        val testedCache = EvaluationTargetingKeyCache()
        val initial = testedCache.get(fakeKey, fakeConsent)

        // When
        val repeated = testedCache.get(fakeKey, fakeConsent)

        // Then
        assertThat(repeated).isSameAs(initial)
        assertThat(repeated.value).isEqualTo(EvaluationPrivacy.targetingKey(fakeKey, fakeConsent))
    }

    @Test
    fun `M invalidate on key and consent changes W get()`(@StringForgery fakeKey: String) {
        // Given
        val testedCache = EvaluationTargetingKeyCache()
        val protected = testedCache.get(fakeKey, false)

        // When
        val full = testedCache.get(fakeKey, true)
        val changedKey = testedCache.get(fakeKey + fakeKey, true)
        val absent = testedCache.get(null, false)
        val empty = testedCache.get("", false)
        val malformed = testedCache.get("\uD800", true)
        val protectedAgain = testedCache.get(fakeKey, false)

        // Then
        assertThat(full.value).isEqualTo(fakeKey)
        assertThat(changedKey.value).isEqualTo(fakeKey + fakeKey)
        assertThat(absent.value).isNull()
        assertThat(empty.value).isEmpty()
        assertThat(malformed.value).isNull()
        assertThat(protectedAgain.value).isEqualTo(protected.value)
        assertThat(protectedAgain).isNotSameAs(protected)
    }

    @Test
    fun `M isolate identities between cache instances W get()`(@StringForgery fakeKey: String) {
        // Given
        val firstCache = EvaluationTargetingKeyCache()
        val secondCache = EvaluationTargetingKeyCache()

        // When
        val firstIdentity = firstCache.get(fakeKey, false)
        val secondIdentity = secondCache.get(fakeKey, false)
        secondCache.get(fakeKey, true)
        val firstAgain = firstCache.get(fakeKey, false)

        // Then
        assertThat(secondIdentity.value).isEqualTo(firstIdentity.value)
        assertThat(secondIdentity).isNotSameAs(firstIdentity)
        assertThat(firstAgain).isSameAs(firstIdentity)
    }

    @Test
    fun `M pair consent with its own identity W get() {concurrent callers}`() {
        // Given
        val testedCache = EvaluationTargetingKeyCache()
        val executor = Executors.newFixedThreadPool(4)
        val ready = CountDownLatch(4)
        val start = CountDownLatch(1)
        val fakeInputs = listOf("jane.doe@datadoghq.com" to false, "😀" to true, "\uD800" to true, null to false)
        try {
            val results = fakeInputs.map { (key, consent) ->
                executor.submit<List<String?>> {
                    ready.countDown()
                    check(start.await(5, TimeUnit.SECONDS))
                    List(100) { testedCache.get(key, consent).value }
                }
            }

            // When
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
            start.countDown()

            // Then
            results.zip(fakeInputs).forEach { (result, input) ->
                assertThat(result.get(5, TimeUnit.SECONDS))
                    .containsOnly(EvaluationPrivacy.targetingKey(input.first, input.second))
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue()
        }
    }
}
