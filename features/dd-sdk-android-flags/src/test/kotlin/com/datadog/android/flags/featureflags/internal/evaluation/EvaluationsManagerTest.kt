/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.evaluation

import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.featureflags.internal.repository.FlagsRepository
import com.datadog.android.flags.featureflags.internal.repository.net.FlagsNetworkManager
import com.datadog.android.flags.featureflags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.featureflags.model.EvaluationContext
import fr.xgouchet.elmyr.annotation.StringForgery
import fr.xgouchet.elmyr.junit5.ForgeExtension
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.util.concurrent.ExecutorService

@ExtendWith(MockitoExtension::class, ForgeExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
internal class EvaluationsManagerTest {

    @Mock
    lateinit var mockExecutorService: ExecutorService

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    @Mock
    lateinit var mockFlagsRepository: FlagsRepository

    @Mock
    lateinit var mockFlagsNetworkManager: FlagsNetworkManager

    @Mock
    lateinit var mockPrecomputeMapper: PrecomputeMapper

    @StringForgery
    lateinit var fakeTargetingKey: String

    @StringForgery
    lateinit var fakeAttributeKey: String

    @StringForgery
    lateinit var fakeAttributeValue: String

    private lateinit var mockWebServer: MockWebServer
    private lateinit var evaluationsManager: EvaluationsManager

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        evaluationsManager = EvaluationsManager(
            executorService = mockExecutorService,
            internalLogger = mockInternalLogger,
            flagsRepository = mockFlagsRepository,
            flagsNetworkManager = mockFlagsNetworkManager,
            precomputeMapper = mockPrecomputeMapper
        )

        // Mock executor to run tasks synchronously for testing
        whenever(mockExecutorService.execute(any())).thenAnswer { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
        }
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `M process context successfully W updateEvaluationsForContext() { valid response }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, mapOf(fakeAttributeKey to fakeAttributeValue))
        val context = publicContext

        val mockResponse = """
        {
            "data": {
                "attributes": {
                    "flags": {
                        "test-flag": {
                            "variationType": "boolean",
                            "variationValue": true,
                            "doLog": true,
                            "allocationKey": "test-allocation",
                            "variationKey": "test-variation",
                            "extraLogging": {},
                            "reason": "test-reason"
                        }
                    }
                }
            }
        }
        """.trimIndent()

        val expectedFlags = mapOf(
            "test-flag" to PrecomputedFlag(
                variationType = "boolean",
                variationValue = "true",
                doLog = true,
                allocationKey = "test-allocation",
                variationKey = "test-variation",
                extraLogging = org.json.JSONObject(),
                reason = "test-reason"
            )
        )

        whenever(mockFlagsNetworkManager.downloadPrecomputedFlags(context)).thenReturn(mockResponse)
        whenever(mockPrecomputeMapper.map(mockResponse)).thenReturn(expectedFlags)

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(context, expectedFlags)
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M handle network failure gracefully W updateEvaluationsForContext() { network error }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext

        whenever(mockFlagsNetworkManager.downloadPrecomputedFlags(context)).thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(context, emptyMap())
        verify(mockInternalLogger, atLeastOnce()).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.WARN),
            eq(InternalLogger.Target.USER),
            any<() -> String>(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )
    }

    @Test
    fun `M handle parsing failure gracefully W updateEvaluationsForContext() { invalid JSON }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext

        val invalidResponse = "{ invalid json }"
        whenever(mockFlagsNetworkManager.downloadPrecomputedFlags(context)).thenReturn(invalidResponse)
        whenever(mockPrecomputeMapper.map(invalidResponse)).thenReturn(emptyMap())

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        verify(mockFlagsRepository).setFlagsAndContext(context, emptyMap())
    }

    @Test
    fun `M log processing start W updateEvaluationsForContext() { any context }`() {
        // Given
        val publicContext = EvaluationContext(fakeTargetingKey, emptyMap())
        val context = publicContext

        whenever(mockFlagsNetworkManager.downloadPrecomputedFlags(context)).thenReturn(null)

        // When
        evaluationsManager.updateEvaluationsForContext(context)

        // Then
        val logCaptor = argumentCaptor<() -> String>()
        verify(mockInternalLogger, times(2)).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            logCaptor.capture(),
            anyOrNull<Throwable>(),
            any<Boolean>(),
            anyOrNull<Map<String, Any?>>()
        )

        assertThat(logCaptor.firstValue.invoke()).contains("Processing evaluation context: $fakeTargetingKey")
    }
}
