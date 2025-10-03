/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository.net

import com.datadog.android.DatadogSite
import com.datadog.android.api.InternalLogger
import com.datadog.android.flags.featureflags.internal.model.FlagsContext
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(ForgeConfigurator::class)
internal class DefaultFlagsNetworkManagerTest {

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedNetworkManager: DefaultFlagsNetworkManager

    @BeforeEach
    fun `set up`(forge: Forge) {
        val flagsContext = FlagsContext(
            clientToken = forge.anAlphabeticalString(),
            applicationId = forge.anAlphabeticalString(),
            site = DatadogSite.US1,
            env = "test"
        )

        testedNetworkManager = DefaultFlagsNetworkManager(
            internalLogger = mockInternalLogger,
            flagsContext = flagsContext
        )
    }

    @Test
    fun `M not throw exception W downloadPrecomputedFlags() { valid context }`(forge: Forge) {
        // Given
        val context = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = mapOf("plan" to "premium")
        )

        // When & Then, does not throw exception
        assertDoesNotThrow {
            testedNetworkManager.downloadPrecomputedFlags(context)
        }
    }

    @Test
    fun `M handle empty attributes W downloadPrecomputedFlags() { context with no attributes }`(forge: Forge) {
        // Given
        val context = EvaluationContext(
            targetingKey = forge.anAlphabeticalString(),
            attributes = emptyMap()
        )

        // When & Thenm doest not throw exception
        assertDoesNotThrow {
            testedNetworkManager.downloadPrecomputedFlags(context)
        }
    }
}
