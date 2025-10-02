/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.flags.featureflags.internal.model.DatadogEvaluationContext
import com.datadog.android.flags.featureflags.internal.model.PrecomputedFlag
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultFlagsRepositoryTest {

    @Mock
    lateinit var mockFeatureSdkCore: FeatureSdkCore

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedRepository: DefaultFlagsRepository

    @BeforeEach
    fun `set up`() {
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
        testedRepository = DefaultFlagsRepository(mockFeatureSdkCore)
    }

    @Test
    fun `M store flags and context W setFlagsAndContext() { valid input }`(forge: Forge) {
        // Given
        val context = DatadogEvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("plan" to "premium")
        )
        val flags = mapOf(
            "flag1" to PrecomputedFlag(
                variationType = "boolean",
                variationValue = "true",
                doLog = true,
                allocationKey = forge.anAlphabeticalString(),
                variationKey = forge.anAlphabeticalString(),
                extraLogging = JSONObject(),
                reason = forge.anAlphabeticalString()
            )
        )

        // When
        testedRepository.setFlagsAndContext(context, flags)

        // Then
        verify(mockInternalLogger).log(
            eq(InternalLogger.Level.DEBUG),
            eq(InternalLogger.Target.MAINTAINER),
            any<() -> String>(),
            eq(null),
            eq(false),
            eq(null)
        )
    }

    @Test
    fun `M return stored context W getEvaluationContext() { after setting context }`(forge: Forge) {
        // Given
        val context = DatadogEvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("env" to "test")
        )
        testedRepository.setFlagsAndContext(context, emptyMap())

        // When
        val result = testedRepository.getEvaluationContext()

        // Then
        assertThat(result).isEqualTo(context)
    }
}
