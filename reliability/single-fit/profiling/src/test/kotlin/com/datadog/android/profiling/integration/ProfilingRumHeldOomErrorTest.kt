/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.profiling.integration

import com.datadog.android.api.feature.Feature
import com.datadog.android.core.stub.StubDataStoreHandler
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.internal.data.SharedPreferencesStorage
import com.datadog.android.internal.profiling.ProfilerEvent
import com.datadog.android.profiling.ExperimentalProfilingApi
import com.datadog.android.profiling.ProfilingConfiguration
import com.datadog.android.profiling.fixtures.ProfilingFeatureTestHandle
import com.datadog.android.profiling.fixtures.ProfilingStorageFixture
import com.datadog.android.profiling.integration.tests.elmyr.ProfilingIntegrationForgeConfigurator
import com.datadog.android.profiling.integration.tests.utils.MainLooperTestConfiguration
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumAttributes
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.rum.RumErrorSource
import com.datadog.tools.unit.annotations.TestConfigurationsProvider
import com.datadog.tools.unit.extensions.TestConfigurationExtension
import com.datadog.tools.unit.extensions.config.TestConfiguration
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.Extensions
import org.junit.jupiter.api.io.TempDir
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness
import java.io.File

/**
 * A fatal OOM RUM error is held across a process death and sent on the next launch with a profiling
 * status matching whether its OOM profile was written. Each launch is a new [StubSDKCore] sharing
 * the RUM datastore and the profiling preferences with the previous one.
 */
@OptIn(ExperimentalProfilingApi::class)
@Extensions(
    ExtendWith(MockitoExtension::class),
    ExtendWith(ForgeExtension::class),
    ExtendWith(TestConfigurationExtension::class)
)
@ForgeConfiguration(ProfilingIntegrationForgeConfigurator::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfilingRumHeldOomErrorTest {

    private val rumDataStore = StubDataStoreHandler()
    private val prefs = mutableMapOf<String, String>()

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun `set up`() {
        ProfilingStorageFixture.stubWith(mapBackedPreferences())
    }

    @AfterEach
    fun `tear down`() {
        ProfilingStorageFixture.reset()
    }

    @Test
    fun `M send held error as running W profile matched in process before death`(forge: Forge) {
        // Given
        val crashed = launch(forge, isProfilingFirst = false)
        val errorId = crashWithOom(crashed)
        crashed.profiling.deliverOomTriggerResult(traceFile(forge), START_MS, END_MS)
        assertThat(errorEvents(crashed.core)).isEmpty()

        // When
        val next = launch(forge, isProfilingFirst = false)

        // Then
        assertThat(profileErrorIds(next.core)).containsExactly(errorId)
        assertThat(errorProfilingStatus(next.core, errorId)).isEqualTo("running")
    }

    @Test
    fun `M send held error as running W profile delivered on next launch {profiling enabled first}`(
        forge: Forge
    ) {
        // Given
        val crashed = launch(forge, isProfilingFirst = false)
        val errorId = crashWithOom(crashed)

        // When
        val next = launch(forge, isProfilingFirst = true)
        next.profiling.deliverOomTriggerResult(traceFile(forge), START_MS, END_MS)

        // Then
        assertThat(profileErrorIds(next.core)).containsExactly(errorId)
        assertThat(errorProfilingStatus(next.core, errorId)).isEqualTo("running")
    }

    @Test
    fun `M send held error as stopped and no profile W profile write fails {unreadable trace}`(forge: Forge) {
        // Given
        val crashed = launch(forge, isProfilingFirst = false)
        val errorId = crashWithOom(crashed)
        val emptyTrace = File(tempDir, forge.anAlphabeticalString()).apply { writeBytes(ByteArray(0)) }
        crashed.profiling.deliverOomTriggerResult(emptyTrace.absolutePath, START_MS, END_MS)

        // When
        val next = launch(forge, isProfilingFirst = true)

        // Then
        assertThat(profileErrorIds(next.core)).isEmpty()
        assertThat(errorProfilingStatus(next.core, errorId)).isEqualTo("stopped")
    }

    @Test
    fun `M send held error once W third launch`(forge: Forge) {
        // Given
        val crashed = launch(forge, isProfilingFirst = false)
        val errorId = crashWithOom(crashed)
        crashed.profiling.deliverOomTriggerResult(traceFile(forge), START_MS, END_MS)
        launch(forge, isProfilingFirst = false)

        // When
        val third = launch(forge, isProfilingFirst = true)

        // Then
        assertThat(profileErrorIds(third.core)).isEmpty()
        assertThat(errorEvents(third.core).map { it.errorId() }).doesNotContain(errorId)
    }

    private class Launch(val core: StubSDKCore, val profiling: ProfilingFeatureTestHandle)

    private fun launch(forge: Forge, isProfilingFirst: Boolean): Launch {
        val core = StubSDKCore(forge)
        core.stubDataStore(Feature.RUM_FEATURE_NAME, rumDataStore)
        val enableProfiling = {
            ProfilingFeatureTestHandle.create(
                sdkCore = core,
                configuration = ProfilingConfiguration.Builder()
                    .enableOutOfMemoryTrigger(true)
                    .setApplicationLaunchSampleRate(0f)
                    .setContinuousSampleRate(0f)
                    .build(),
                isOomTriggerRegistered = true
            )
        }
        val profiling = if (isProfilingFirst) enableProfiling() else null
        Rum.enable(
            RumConfiguration.Builder(forge.anAlphabeticalString())
                .trackNonFatalAnrs(false)
                .setSessionSampleRate(100f)
                .build(),
            core
        )
        GlobalRumMonitor.get(core).startView(forge.anAlphabeticalString(), forge.anAlphabeticalString())
        return Launch(core, profiling ?: enableProfiling())
    }

    private fun crashWithOom(launch: Launch): String {
        GlobalRumMonitor.get(launch.core).addError(
            "Out of memory",
            RumErrorSource.SOURCE,
            OutOfMemoryError(),
            mapOf(RumAttributes.INTERNAL_ERROR_IS_CRASH to true)
        )
        val gatingEvent = launch.core.eventsReceived(Feature.PROFILING_FEATURE_NAME)
            .filterIsInstance<ProfilerEvent.RumOomErrorEvent>()
            .single()
        return gatingEvent.id
    }

    private fun traceFile(forge: Forge): String {
        return File(tempDir, forge.anAlphabeticalString())
            .apply { writeText(forge.anAlphabeticalString(size = 64)) }
            .absolutePath
    }

    private fun errorEvents(core: StubSDKCore): List<JsonObject> {
        return core.eventsWritten(Feature.RUM_FEATURE_NAME)
            .map { JsonParser.parseString(it.eventData).asJsonObject }
            .filter { it.get("type")?.asString == "error" }
    }

    private fun errorProfilingStatus(core: StubSDKCore, errorId: String): String? {
        val error = errorEvents(core).single { it.errorId() == errorId }
        return error.getAsJsonObject("_dd")?.getAsJsonObject("profiling")?.get("status")?.asString
    }

    private fun profileErrorIds(core: StubSDKCore): List<String> {
        return core.eventsWritten(Feature.PROFILING_FEATURE_NAME)
            .map { JsonParser.parseString(it.eventData).asJsonObject }
            .flatMap { it.getAsJsonObject("error")?.getAsJsonArray("id")?.map { id -> id.asString }.orEmpty() }
    }

    private fun JsonObject.errorId(): String? = getAsJsonObject("error")?.get("id")?.asString

    private fun mapBackedPreferences(): SharedPreferencesStorage {
        return mock<SharedPreferencesStorage>().also { storage ->
            whenever(storage.getString(any(), anyOrNull())) doAnswer {
                prefs[it.getArgument(0)] ?: it.getArgument(1)
            }
            whenever(storage.putString(any(), any(), any())) doAnswer {
                prefs[it.getArgument(0)] = it.getArgument(1)
                Unit
            }
            whenever(storage.remove(any(), any())) doAnswer {
                prefs.remove(it.getArgument<String>(0))
                Unit
            }
        }
    }

    companion object {
        private const val START_MS = 1_000L
        private const val END_MS = 2_000L
        private val mainLooper = MainLooperTestConfiguration()

        @TestConfigurationsProvider
        @JvmStatic
        fun getTestConfigurations(): List<TestConfiguration> {
            return listOf(mainLooper)
        }
    }
}
