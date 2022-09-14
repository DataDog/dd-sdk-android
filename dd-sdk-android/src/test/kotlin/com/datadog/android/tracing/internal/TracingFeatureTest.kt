/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.tracing.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.core.internal.persistence.file.advanced.ScheduledWriter
import com.datadog.android.core.internal.persistence.file.batch.BatchFileDataWriter
import com.datadog.android.tracing.internal.domain.TracesFilePersistenceStrategy
import com.datadog.android.tracing.internal.domain.event.SpanEventMapperWrapper
import com.datadog.android.tracing.internal.domain.event.SpanMapperSerializer
import com.datadog.android.utils.forge.Configurator
import com.datadog.android.v2.tracing.internal.net.TracesRequestFactory
import com.datadog.opentracing.DDSpan
import com.datadog.tools.unit.extensions.TestConfigurationExtension
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
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class TracingFeatureTest :
    SdkFeatureTest<DDSpan, Configuration.Feature.Tracing, TracingFeature>() {

    override fun createTestedFeature(): TracingFeature {
        return TracingFeature(coreFeature.mockInstance)
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.Tracing {
        return forge.getForgery()
    }

    override fun featureDirName(): String {
        return "tracing"
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(TracesFilePersistenceStrategy::class.java)
    }

    @Test
    fun `𝕄 use the eventMapper 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        val batchFileDataWriter =
            (testedFeature.persistenceStrategy.getWriter() as? ScheduledWriter)
                ?.delegateWriter as? BatchFileDataWriter
        val spanSerializer = batchFileDataWriter?.serializer as? SpanMapperSerializer
        val spanEventMapperWrapper = spanSerializer?.spanEventMapper as? SpanEventMapperWrapper
        val spanEventMapper = spanEventMapperWrapper?.wrappedEventMapper
        assertThat(spanEventMapper).isSameAs(fakeConfigurationFeature.spanEventMapper)
    }

    @Test
    fun `𝕄 create a tracing request factory 𝕎 createRequestFactory()`() {
        // When
        val requestFactory = testedFeature.createRequestFactory(fakeConfigurationFeature)

        // Then
        assertThat(requestFactory).isInstanceOf(TracesRequestFactory::class.java)
    }
}
