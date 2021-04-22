/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.error.internal

import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.internal.CoreFeature
import com.datadog.android.core.internal.SdkFeatureTest
import com.datadog.android.log.internal.net.LogsOkHttpUploader
import com.datadog.android.log.model.LogEvent
import com.datadog.android.utils.forge.Configurator
import com.datadog.tools.unit.extensions.ApiLevelExtension
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.nhaarman.mockitokotlin2.mock
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(ApiLevelExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@MockitoSettings(strictness = Strictness.LENIENT)
@ForgeConfiguration(Configurator::class)
internal class CrashReportsFeatureTest :
    SdkFeatureTest<LogEvent, Configuration.Feature.CrashReport, CrashReportsFeature>() {

    var jvmExceptionHandler: Thread.UncaughtExceptionHandler? = null

    @BeforeEach
    fun `set up crash reports`() {
        jvmExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()
    }

    @AfterEach
    fun `tear down crash reports`() {
        Thread.setDefaultUncaughtExceptionHandler(jvmExceptionHandler)
    }

    override fun createTestedFeature(): CrashReportsFeature {
        return CrashReportsFeature
    }

    override fun forgeConfiguration(forge: Forge): Configuration.Feature.CrashReport {
        return forge.getForgery()
    }

    @Test
    fun `𝕄 initialize persistence strategy 𝕎 initialize()`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        assertThat(testedFeature.persistenceStrategy)
            .isInstanceOf(CrashReportFilePersistenceStrategy::class.java)
    }

    @Test
    fun `𝕄 create a crash reports uploader 𝕎 createUploader()`() {
        // When
        val uploader = testedFeature.createUploader(fakeConfigurationFeature)

        // Then
        assertThat(uploader).isInstanceOf(LogsOkHttpUploader::class.java)
        val crashUploader = uploader as LogsOkHttpUploader
        assertThat(crashUploader.url).startsWith(fakeConfigurationFeature.endpointUrl)
        assertThat(crashUploader.url).endsWith(CoreFeature.clientToken)
        assertThat(crashUploader.client).isSameAs(CoreFeature.okHttpClient)
    }

    @Test
    fun `𝕄 register crash handler 𝕎 initialize`() {
        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)

        // Then
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(handler)
            .isInstanceOf(DatadogExceptionHandler::class.java)
    }

    @Test
    fun `𝕄 restore original crash handler 𝕎 stop()`() {
        // Given
        val mockOriginalHandler: Thread.UncaughtExceptionHandler = mock()
        Thread.setDefaultUncaughtExceptionHandler(mockOriginalHandler)

        // When
        testedFeature.initialize(appContext.mockInstance, fakeConfigurationFeature)
        testedFeature.stop()

        // Then
        val finalHandler = Thread.getDefaultUncaughtExceptionHandler()
        assertThat(finalHandler).isSameAs(mockOriginalHandler)
    }
}
