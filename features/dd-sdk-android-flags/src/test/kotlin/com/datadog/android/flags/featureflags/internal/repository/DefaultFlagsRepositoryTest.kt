/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.featureflags.internal.repository

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.flags.featureflags.model.EvaluationContext
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.doReturn
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
    lateinit var mockDataStore: DataStoreHandler

    @Mock
    lateinit var mockInternalLogger: InternalLogger

    private lateinit var testedRepository: DefaultFlagsRepository

    @BeforeEach
    fun `set up`() {
        whenever(mockFeatureSdkCore.internalLogger) doReturn mockInternalLogger
        testedRepository = DefaultFlagsRepository(
            featureSdkCore = mockFeatureSdkCore,
            dataStore = mockDataStore,
            instanceName = "default"
        )
    }

    @Test
    fun `M return stored context W getEvaluationContext() { after setting context }`(forge: Forge) {
        // Given
        val context = EvaluationContext(
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
