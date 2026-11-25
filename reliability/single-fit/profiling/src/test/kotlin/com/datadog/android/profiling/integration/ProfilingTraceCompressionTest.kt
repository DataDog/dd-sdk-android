/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.core.stub.StubTelemetryEvent
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.fixtures.ProfilingFeatureTestHandle
import com.datadog.android.profiling.fixtures.ProfilingStorageFixture
import com.datadog.android.profiling.integration.tests.elmyr.ProfilingIntegrationForgeConfigurator
import com.datadog.android.profiling.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONArray
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.AutoClose
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.mock
import org.mockito.quality.Strictness
import java.io.File
import java.io.RandomAccessFile
import java.util.Random
import java.util.zip.GZIPInputStream

/**
 * Writes profiles through the real profiling feature, writer and gzip compression, and checks what
 * reaches the storage of a stubbed SDK core. Unit tests cover the writer with an injected compressor.
 */
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(ProfilingIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfilingTraceCompressionTest {

    @TempDir
    lateinit var tmp: File

    @AutoClose
    private lateinit var stubSdkCore: StubSDKCore

    private lateinit var handle: ProfilingFeatureTestHandle

    @BeforeEach
    fun `set up`(forge: Forge) {
        stubSdkCore = StubSDKCore(forge)
        ProfilingStorageFixture.stubWith(mock<SharedPreferencesStorage>())
        handle = ProfilingFeatureTestHandle.create(
            sdkCore = stubSdkCore,
            configuration = ProfilingConfiguration.Builder().build()
        )
    }

    @AfterEach
    fun `tear down`() {
        ProfilingStorageFixture.reset()
    }

    @Test
    fun `M save a gzip trace and raw RUM events W writeManualProfile()`(forge: Forge) {
        // Given
        val rawBytes = forge.aString(size = 4096).toByteArray()
        val traceFile = tmp.resolve("trace").apply { writeBytes(rawBytes) }

        // When
        handle.writeManualProfile(traceFile)

        // Then
        val saved = savedProfiles().single()
        assertThat(saved.perfettoBytes).startsWith(*GZIP_MAGIC)
        assertThat(saved.perfettoBytes.gzipDecompress()).isEqualTo(rawBytes)
        assertThat(JSONArray(String(saved.rumMobileEventsBytes)).length()).isEqualTo(1)
        assertThat(traceFile.exists()).isFalse()
        val writeMetric = writeMetrics().single()
        assertThat(writeMetric["dropped"]).isEqualTo(false)
        assertThat(writeMetric["profile_compressed"]).isEqualTo(true)
        assertThat(writeMetric["profile_size_bytes"]).isEqualTo(saved.perfettoBytes.size.toLong())
    }

    @Test
    fun `M keep the profile W writeManualProfile() {raw over the limit, compressed under it}`() {
        // Given a trace one byte over the limit that compresses far below it
        val traceFile = tmp.resolve("trace")
        RandomAccessFile(traceFile, "rw").use { it.setLength(PROFILE_SIZE_LIMIT_BYTES + 1) }

        // When
        handle.writeManualProfile(traceFile)

        // Then
        val saved = savedProfiles().single()
        assertThat(saved.perfettoBytes.size.toLong()).isLessThan(PROFILE_SIZE_LIMIT_BYTES)
        assertThat(saved.perfettoBytes.gzipDecompress().size.toLong()).isEqualTo(PROFILE_SIZE_LIMIT_BYTES + 1)
    }

    @Test
    fun `M drop the profile W writeManualProfile() {compressed still over the limit}`(forge: Forge) {
        // Given a trace that does not compress, so its compressed stream passes the limit too
        val incompressible = ByteArray(PROFILE_SIZE_LIMIT_BYTES.toInt() + 1).also {
            Random(forge.aLong()).nextBytes(it)
        }
        val traceFile = tmp.resolve("trace").apply { writeBytes(incompressible) }

        // When
        handle.writeManualProfile(traceFile)

        // Then nothing is stored, the trace file is cleaned up, and the drop is reported as too large
        assertThat(stubSdkCore.eventsWritten(Feature.PROFILING_FEATURE_NAME)).isEmpty()
        assertThat(traceFile.exists()).isFalse()
        val writeMetric = writeMetrics().single()
        assertThat(writeMetric["dropped"]).isEqualTo(true)
        assertThat(writeMetric["drop_reason"]).isEqualTo("profile_too_large")
        assertThat(writeMetric["profile_compressed"]).isEqualTo(true)
        assertThat(writeMetric["profile_size_bytes"] as Long).isGreaterThan(PROFILE_SIZE_LIMIT_BYTES)
    }

    // The attributes of the profiling write metrics reported to telemetry
    private fun writeMetrics(): List<Map<*, *>> {
        return stubSdkCore.telemetryEventsWritten()
            .filter { it.type == StubTelemetryEvent.Type.METRIC }
            .mapNotNull { it.additionalProperties["profiling_write"] as? Map<*, *> }
    }

    private fun savedProfiles(): List<ProfilingFeatureTestHandle.SavedProfile> {
        return stubSdkCore.eventsWritten(Feature.PROFILING_FEATURE_NAME).map {
            checkNotNull(ProfilingFeatureTestHandle.decodeSavedProfile(it.rawEventMetadata))
        }
    }

    private fun ByteArray.gzipDecompress(): ByteArray = GZIPInputStream(inputStream()).use { it.readBytes() }

    companion object {
        // 30 MiB, the intake limit, kept as a literal independent from the production constant
        private const val PROFILE_SIZE_LIMIT_BYTES = 31_457_280L
        private val GZIP_MAGIC = byteArrayOf(0x1F, 0x8B.toByte())
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
