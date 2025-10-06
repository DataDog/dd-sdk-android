/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.internal.model.ExposureEvent
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.atMost
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class ExposureEventsProcessorTest {

    @Mock
    lateinit var mockRecordWriter: RecordWriter

    @StringForgery
    lateinit var fakeFlagName: String

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeAllocationKey: String

    @StringForgery
    lateinit var fakeVariationKey: String

    private lateinit var testedProcessor: ExposureEventsProcessor
    private lateinit var fakeFlag: PrecomputedFlag

    @BeforeEach
    fun `set up`(forge: Forge) {
        testedProcessor = ExposureEventsProcessor(mockRecordWriter)
        fakeFlag = forge.getForgery<PrecomputedFlag>().copy(
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey
        )
    }

    // region processEvent

    @Test
    fun `M process first exposure W processEvent() { new key }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf(
                "user_id" to forge.anAlphabeticalString(),
                "plan" to "premium",
                "age" to forge.anInt(min = 18, max = 99).toString()
            )
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.flag.key).isEqualTo(fakeFlagName)
        assertThat(capturedEvent.allocation.key).isEqualTo(fakeAllocationKey)
        assertThat(capturedEvent.variant.key).isEqualTo(fakeVariationKey)
        assertThat(capturedEvent.subject.id).isEqualTo(fakeTargetingKey)
        assertThat(capturedEvent.subject.attributes).containsKeys("user_id", "plan", "age")
        assertThat(capturedEvent.subject.attributes).hasSize(3)
        assertThat(capturedEvent.timeStamp).isGreaterThan(0)
    }

    @Test
    fun `M not process duplicate exposure W processEvent() { same flag and targeting key }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag) // Duplicate

        // Then
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M process different exposures W processEvent() { different flag names }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val anotherFlagName = forge.anAlphabeticalString()

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(anotherFlagName, fakeContext, fakeFlag)

        // Then
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M handle empty attributes W processEvent() { context with no attributes }`() {
        // Given
        val fakeContext = EvaluationContext(targetingKey = fakeTargetingKey)

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.subject.id).isEqualTo(fakeTargetingKey)
        assertThat(capturedEvent.subject.attributes).isEmpty()
    }

    @Test
    fun `M process different exposures W processEvent() { different targeting keys }`(forge: Forge) {
        // Given
        val fakeContext1 = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeContext2 = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext1, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext2, fakeFlag)

        // Then
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M process different exposures W processEvent() { different allocation keys }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeFlag2 = fakeFlag.copy(allocationKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag2)

        // Then
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M process different exposures W processEvent() { different variation keys }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeFlag2 = fakeFlag.copy(variationKey = forge.anAlphabeticalString())

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag2)

        // Then
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M convert attribute values to strings W processEvent() { various attribute types }`(forge: Forge) {
        // Given
        val fakeContext = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf(
                "string_attr" to forge.anAlphabeticalString(),
                "int_attr" to forge.anInt().toString(),
                "bool_attr" to forge.aBool().toString(),
                "double_attr" to forge.aDouble().toString()
            )
        )

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext, fakeFlag)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        capturedEvent.subject.attributes.values.forEach { value ->
            assertThat(value).isInstanceOf(String::class.java)
        }
        assertThat(capturedEvent.subject.attributes).hasSize(4)
    }

    @Test
    fun `M handle empty strings in parameters W processEvent() { empty flag name and keys }`() {
        // Given
        val emptyFlagName = ""
        val contextWithEmptyTargeting = EvaluationContext(
            targetingKey = "",
            attributes = mapOf("attr" to "value")
        )
        val flagWithEmptyKeys = fakeFlag.copy(
            allocationKey = "",
            variationKey = ""
        )

        // When
        testedProcessor.processEvent(emptyFlagName, contextWithEmptyTargeting, flagWithEmptyKeys)

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter).write(eventCaptor.capture())

        val capturedEvent = eventCaptor.firstValue
        assertThat(capturedEvent.flag.key).isEmpty()
        assertThat(capturedEvent.allocation.key).isEmpty()
        assertThat(capturedEvent.variant.key).isEmpty()
        assertThat(capturedEvent.subject.id).isEmpty()
    }

    @Test
    fun `M maintain separate cache entries W processEvent() { same parameters but different order }`() {
        // Given
        val context1 = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("attr1" to "value1", "attr2" to "value2")
        )
        val context2 = EvaluationContext(
            targetingKey = fakeTargetingKey,
            attributes = mapOf("attr2" to "value2", "attr1" to "value1") // Same attributes, different order
        )

        // When
        testedProcessor.processEvent(fakeFlagName, context1, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, context2, fakeFlag)

        // Then
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M generate consistent timestamps W processEvent() { multiple calls }`(forge: Forge) {
        // Given
        val fakeContext1 = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )
        val fakeContext2 = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("user_id" to forge.anAlphabeticalString())
        )

        val beforeTime = System.currentTimeMillis()

        // When
        testedProcessor.processEvent(fakeFlagName, fakeContext1, fakeFlag)
        testedProcessor.processEvent(fakeFlagName, fakeContext2, fakeFlag)

        val afterTime = System.currentTimeMillis()

        // Then
        val eventCaptor = argumentCaptor<ExposureEvent>()
        verify(mockRecordWriter, times(2)).write(eventCaptor.capture())

        val capturedEvents = eventCaptor.allValues
        capturedEvents.forEach { event ->
            assertThat(event.timeStamp).isBetween(beforeTime, afterTime)
        }
    }

    // endregion

    // region Cache Key Collision Prevention

    @Test
    fun `M prevent cache collision W processEvent() { components with separator characters }`(forge: Forge) {
        // Given
        val targetingKey1 = "user|123"
        val flagName1 = "feature"
        val allocationKey1 = "allocation"
        val variationKey1 = "variation"

        val targetingKey2 = "user"
        val flagName2 = "123|feature"
        val allocationKey2 = "allocation"
        val variationKey2 = "variation"

        val context1 = EvaluationContext(targetingKey = targetingKey1)
        val context2 = EvaluationContext(targetingKey = targetingKey2)

        val flag1 = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = allocationKey1,
            variationKey = variationKey1,
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        val flag2 = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = allocationKey2,
            variationKey = variationKey2,
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        // When
        testedProcessor.processEvent(flagName1, context1, flag1)
        testedProcessor.processEvent(flagName2, context2, flag2)

        // Then
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M prevent cache collision W processEvent() { boundary ambiguity scenarios }`(forge: Forge) {
        // Given
        val scenario1Context = EvaluationContext(targetingKey = "a")
        val scenario1Flag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = "b",
            variationKey = "c",
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        val scenario2Context = EvaluationContext(targetingKey = "ab")
        val scenario2Flag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = "",
            variationKey = "c",
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        // When
        testedProcessor.processEvent("flag", scenario1Context, scenario1Flag)
        testedProcessor.processEvent("flag", scenario2Context, scenario2Flag)

        // Then
        verify(mockRecordWriter, times(2)).write(any())
    }

    @Test
    fun `M distinguish similar cache keys W processEvent() { minimal differences }`(forge: Forge) {
        // Given
        val baseContext = EvaluationContext(targetingKey = "user123")
        val baseFlag = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = "alloc",
            variationKey = "var",
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        val flag1 = baseFlag.copy(allocationKey = "alloc1")
        val flag2 = baseFlag.copy(variationKey = "var1")
        val context2 = EvaluationContext(targetingKey = "user124")

        // When
        testedProcessor.processEvent("flag", baseContext, flag1)
        testedProcessor.processEvent("flag", baseContext, flag2)
        testedProcessor.processEvent("flag", context2, baseFlag)

        // Then
        verify(mockRecordWriter, times(3)).write(any())
    }

    @Test
    fun `M maintain cache consistency W processEvent() { mixed scenarios }`(forge: Forge) {
        // Given
        val context1 = EvaluationContext(targetingKey = "user1")
        val context2 = EvaluationContext(targetingKey = "user2")

        val flag1 = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = "alloc1",
            variationKey = "var1",
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        val flag2 = PrecomputedFlag(
            variationType = forge.anAlphabeticalString(),
            variationValue = forge.anAlphabeticalString(),
            doLog = forge.aBool(),
            allocationKey = "alloc2",
            variationKey = "var2",
            extraLogging = org.json.JSONObject(),
            reason = forge.anAlphabeticalString()
        )

        // When
        testedProcessor.processEvent("flag1", context1, flag1) // New
        testedProcessor.processEvent("flag1", context1, flag1) // Duplicate
        testedProcessor.processEvent("flag1", context2, flag1) // Different context
        testedProcessor.processEvent("flag2", context1, flag1) // Different flag name
        testedProcessor.processEvent("flag1", context1, flag2) // Different flag data
        testedProcessor.processEvent("flag1", context1, flag1) // Duplicate again

        // Then
        verify(mockRecordWriter, times(4)).write(any())
    }

    // endregion

    // region Concurrency Tests

    @Test
    fun `M handle concurrent access W processEvent() { multiple threads same cache key }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val flag = fakeFlag.copy(
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey
        )
        val threadCount = 10
        val executionsPerThread = 100

        // When
        val threads = (1..threadCount).map {
            Thread {
                repeat(executionsPerThread) {
                    testedProcessor.processEvent(fakeFlagName, context, flag)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M handle concurrent access W processEvent() { multiple threads different cache keys }`() {
        // Given
        val threadCount = 10
        val executionsPerThread = 50
        val totalExpectedWrites = threadCount * executionsPerThread

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                repeat(executionsPerThread) { executionIndex ->
                    val uniqueContext = EvaluationContext(
                        targetingKey = "thread-$threadIndex-execution-$executionIndex"
                    )
                    val uniqueFlag = fakeFlag.copy(
                        allocationKey = "alloc-$threadIndex-$executionIndex",
                        variationKey = "var-$threadIndex-$executionIndex"
                    )
                    testedProcessor.processEvent(
                        "flag-$threadIndex-$executionIndex",
                        uniqueContext,
                        uniqueFlag
                    )
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        verify(mockRecordWriter, times(totalExpectedWrites)).write(any())
    }

    @Test
    fun `M handle concurrent cache operations W processEvent and clearExposureCache() { mixed operations }`() {
        // Given
        val context = EvaluationContext(targetingKey = fakeTargetingKey)
        val flag = fakeFlag.copy(
            allocationKey = fakeAllocationKey,
            variationKey = fakeVariationKey
        )
        val processingThreadCount = 5
        val clearThreadCount = 2
        val executionsPerThread = 100

        // When
        val processingThreads = (1..processingThreadCount).map {
            Thread {
                repeat(executionsPerThread) {
                    testedProcessor.processEvent(fakeFlagName, context, flag)
                }
            }
        }

        val clearThreads = (1..clearThreadCount).map {
            Thread {
                repeat(10) {
                    Thread.sleep(5)
                    testedProcessor = ExposureEventsProcessor(mockRecordWriter)
                }
            }
        }

        val allThreads = processingThreads + clearThreads
        allThreads.forEach { it.start() }
        allThreads.forEach { it.join() }

        // Then
        verify(mockRecordWriter, atLeast(1)).write(any())
        verify(mockRecordWriter, atMost(processingThreadCount * executionsPerThread)).write(any())
    }

    @Test
    fun `M maintain cache consistency W processEvent() { high contention scenario }`() {
        // Given
        val sharedContext = EvaluationContext(targetingKey = "shared-user")
        val sharedFlag = fakeFlag.copy(
            allocationKey = "shared-allocation",
            variationKey = "shared-variation"
        )
        val threadCount = 20
        val executionsPerThread = 200

        // When
        val threads = (1..threadCount).map {
            Thread {
                repeat(executionsPerThread) {
                    testedProcessor.processEvent("shared-flag", sharedContext, sharedFlag)
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        verify(mockRecordWriter, times(1)).write(any())
    }

    @Test
    fun `M handle concurrent different flags W processEvent() { stress test }`() {
        // Given - stay within LRU cache size of 100
        val threadCount = 10
        val flagsPerThread = 9 // 10 * 9 = 90 unique entries, safely under 100
        val executionsPerFlag = 10
        val totalExpectedWrites = threadCount * flagsPerThread

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                repeat(flagsPerThread) { flagIndex ->
                    val context = EvaluationContext(targetingKey = "user-$threadIndex")
                    val flag = fakeFlag.copy(
                        allocationKey = "alloc-$threadIndex-$flagIndex",
                        variationKey = "var-$threadIndex-$flagIndex"
                    )

                    repeat(executionsPerFlag) {
                        testedProcessor.processEvent(
                            "flag-$threadIndex-$flagIndex",
                            context,
                            flag
                        )
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        verify(mockRecordWriter, times(totalExpectedWrites)).write(any())
    }

    @Test
    fun `M preserve cache state W processEvent() { verify no data corruption }`() {
        // Given
        val knownContext = EvaluationContext(targetingKey = "known-user")
        val knownFlag = fakeFlag.copy(
            allocationKey = "known-allocation",
            variationKey = "known-variation"
        )

        testedProcessor.processEvent("known-flag", knownContext, knownFlag)
        verify(mockRecordWriter, times(1)).write(any())

        val threadCount = 10
        val executionsPerThread = 100

        // When
        val threads = (1..threadCount).map { threadIndex ->
            Thread {
                repeat(executionsPerThread) { executionIndex ->
                    if (executionIndex % 2 == 0) {
                        testedProcessor.processEvent("known-flag", knownContext, knownFlag)
                    } else {
                        val uniqueContext = EvaluationContext(
                            targetingKey = "user-$threadIndex-$executionIndex"
                        )
                        val uniqueFlag = fakeFlag.copy(
                            allocationKey = "alloc-$threadIndex-$executionIndex",
                            variationKey = "var-$threadIndex-$executionIndex"
                        )
                        testedProcessor.processEvent(
                            "flag-$threadIndex-$executionIndex",
                            uniqueContext,
                            uniqueFlag
                        )
                    }
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // Then
        val uniqueWrites = threadCount * (executionsPerThread / 2)
        val totalExpectedWrites = 1 + uniqueWrites
        verify(mockRecordWriter, times(totalExpectedWrites)).write(any())
    }

    // endregion
}
