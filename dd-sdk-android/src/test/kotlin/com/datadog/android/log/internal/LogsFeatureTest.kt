/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.log.internal

import android.app.Application
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileDataWriter
import com.datadog.android.event.MapperSerializer
import com.datadog.android.log.internal.domain.LogFilePersistenceStrategy
import com.datadog.android.log.internal.domain.event.LogEventMapperWrapper
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.config.ApplicationContextTestConfiguration
import com.datadog.android.utils.config.CoreFeatureTestConfiguration
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.core.internal.storage.Storage
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.annotation.Forgery
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
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class LogsFeatureTest {

    private lateinit var testedFeature: LogsFeature

    @Forgery
    lateinit var fakeConfigurationFeature: Configuration.Feature.Logs

    @Mock
    lateinit var mockStorage: Storage

    @BeforeEach
    fun `set up`() {
        testedFeature = LogsFeature(coreFeature.mockInstance, mockStorage)
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(LogFilePersistenceStrategy::class.java)
    }

    @Test
    fun `𝕄 use the eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.initialize(fakeConfigurationFeature)

        // Then
        val batchFileDataWriter =
            (testedFeature.persistenceStrategy.getWriter() as? ScheduledWriter)
                ?.delegateWriter as? BatchFileDataWriter
        val logMapperSerializer = batchFileDataWriter?.serializer as? MapperSerializer<LogEvent>
        val logEventMapperWrapper = logMapperSerializer?.eventMapper as? LogEventMapperWrapper
        val logEventMapper = logEventMapperWrapper?.wrappedEventMapper
        assertThat(
            logEventMapper
        ).isSameAs(
            fakeConfigurationFeature.logsEventMapper
        )
    }

    companion object {
        val appContext = ApplicationContextTestConfiguration(Application::class.java)
        val coreFeature = CoreFeatureTestConfiguration(appContext)

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(appContext, coreFeature)
        }
    }
}
