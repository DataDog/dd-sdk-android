/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.model.PrecomputedFlag
import com.datadog.android.flags.internal.net.PrecomputedAssignmentsReader
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.FlagsClientState
import com.datadog.android.flags.utils.forge.ForgeConfigurator
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.junit5.ForgeConfiguration
import fr.xgouchet.elmyr.junit5.ForgeExtension
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService

/** Exercises request admission, assignment installation and reads together, with explicitly queued fetches. */
@ExtendWith(ForgeExtension::class)
@ForgeConfiguration(ForgeConfigurator::class)
internal class StaleAssignmentsTest {
    private lateinit var testedClient: DatadogFlagsClient
    private lateinit var repository: DefaultFlagsRepository
    private lateinit var diskCallback: DataStoreReadCallback<FlagsStateEntry>
    private lateinit var assignmentContext: EvaluationContext
    private lateinit var otherContext: EvaluationContext
    private lateinit var flag: PrecomputedFlag
    private val dataStore = mock<DataStoreHandler>()
    private val reader = mock<PrecomputedAssignmentsReader>()
    private val mapper = mock<PrecomputeMapper>()
    private val stateManager = mock<FlagsStateManager>()
    private val exposureProcessor = mock<EventsProcessor>()
    private val evaluationFeature = mock<EvaluationsFeature>()
    private val rumLogger = mock<RumEvaluationLogger>()
    private val tasks = mutableListOf<Runnable>()

    @BeforeEach
    fun setUp(forge: Forge) {
        val sdkCore = mock<FeatureSdkCore>()
        val featureScope = mock<FeatureScope>()
        val executor = mock<ExecutorService>()
        val datadogContext = forge.getForgery<DatadogContext>()
        whenever(sdkCore.internalLogger) doReturn mock()
        whenever(sdkCore.timeProvider) doReturn mock()
        whenever(sdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn featureScope
        doAnswer {
            it.getArgument<(DatadogContext) -> Unit>(1)(datadogContext)
        }.whenever(featureScope).withContext(eq(setOf(Feature.RUM_FEATURE_NAME)), any())
        doAnswer { tasks.add(it.getArgument(0)); null }.whenever(executor).execute(any())
        doAnswer {
            diskCallback = it.getArgument(2)
            null
        }.whenever(dataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        repository = DefaultFlagsRepository(sdkCore, "stale-test", dataStore, persistenceLoadTimeoutMs = 0)
        val manager = EvaluationsManager(
            sdkCore, executor, sdkCore.internalLogger, repository, reader, mapper, stateManager,
            initializationTimeoutMs = null,
            initializationTimeoutScheduler = { _, _ -> {} }
        )
        testedClient = DatadogFlagsClient(
            sdkCore,
            manager,
            repository,
            forge.getForgery<FlagsConfiguration>().copy(
                trackExposures = true,
                trackEvaluations = true,
                rumIntegrationEnabled = true
            ),
            rumLogger,
            exposureProcessor,
            evaluationFeature,
            stateManager
        )
        assignmentContext = EvaluationContext(forge.anAlphabeticalString(), mapOf("plan" to "free", "region" to "us"))
        otherContext = assignmentContext.copy(targetingKey = assignmentContext.targetingKey + "-other")
        flag = forge.getForgery<PrecomputedFlag>().copy(
            variationType = "boolean",
            variationValue = "true",
            doLog = true,
            reason = "TARGETING_MATCH"
        )
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M restore original reason W switching context back to assignments`(fromDisk: Boolean) {
        install(fromDisk)
        assertReason(originalReason(fromDisk))
        testedClient.setEvaluationContext(assignmentContext.copy())
        assertReason(originalReason(fromDisk))
        testedClient.setEvaluationContext(otherContext)
        assertReason("STALE")
        assertThat(repository.getEvaluationContext()).isEqualTo(assignmentContext)
        testedClient.setEvaluationContext(assignmentContext)
        assertReason(originalReason(fromDisk))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M compare all attributes W context changes`(fromDisk: Boolean) {
        install(fromDisk)
        val contexts = listOf(
            assignmentContext.copy(attributes = mapOf("plan" to "paid", "region" to "us")),
            assignmentContext.copy(attributes = assignmentContext.attributes + ("new" to "value")),
            assignmentContext.copy(attributes = mapOf("plan" to "free"))
        )
        contexts.forEach {
            testedClient.setEvaluationContext(it)
            assertReason("STALE")
        }
        testedClient.setEvaluationContext(
            assignmentContext.copy(attributes = linkedMapOf("region" to "us", "plan" to "free"))
        )
        assertReason(originalReason(fromDisk))
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M reconcile restored assignments W context requested before disk load`(matching: Boolean) {
        testedClient.setEvaluationContext(if (matching) assignmentContext else otherContext)
        assertThat(repository.getEvaluationContext()).isNull()
        assertThat(testedClient.resolve("flag", false).errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        install(fromDisk = true)
        assertReason(if (matching) "CACHED" else "STALE")
    }

    @Test
    fun `M distinguish no request from explicit empty context W disk load`() {
        install(fromDisk = true)
        assertReason("CACHED")
        testedClient.setEvaluationContext(EvaluationContext.EMPTY)
        assertReason("STALE")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `M retain reason W matching or differing refresh fails`(fromDisk: Boolean) {
        install(fromDisk)
        testedClient.setEvaluationContext(assignmentContext)
        runNextFetch()
        assertReason(originalReason(fromDisk))
        verify(stateManager).updateState(FlagsClientState.Stale)
        testedClient.setEvaluationContext(otherContext)
        runNextFetch()
        assertReason("STALE")
        testedClient.setEvaluationContext(assignmentContext)
        assertReason(originalReason(fromDisk))
    }

    @Test
    fun `M mark older response stale W newer context requested during fetch`() {
        install(fromDisk = true)
        whenever(reader.readPrecomputedFlags(eq(assignmentContext), any())) doAnswer {
            // A is in flight when B is synchronously requested and queued behind it.
            testedClient.setEvaluationContext(otherContext)
            "response-a"
        }
        whenever(reader.readPrecomputedFlags(eq(otherContext), any())) doReturn "response-b"
        val replacement = flag.copy(variationValue = "false", reason = "SPLIT")
        whenever(mapper.map("response-a")) doReturn mapOf("flag" to flag)
        whenever(mapper.map("response-b")) doReturn mapOf("flag" to replacement)
        testedClient.setEvaluationContext(assignmentContext)
        runNextFetch()
        assertReason("STALE")
        assertThat(repository.getEvaluationContext()).isEqualTo(assignmentContext)
        val saved = argumentCaptor<FlagsStateEntry>()
        verify(dataStore).setValue(any(), saved.capture(), any(), anyOrNull(), any())
        assertThat(saved.firstValue.evaluationContext).isEqualTo(assignmentContext)
        assertThat(saved.firstValue.flags["flag"]).isSameAs(flag)
        runNextFetch()
        assertReason("SPLIT", value = false)
        assertThat(repository.getEvaluationContext()).isEqualTo(otherContext)
    }

    @Test
    fun `M retain older response as stale W newer fetch fails`() {
        whenever(reader.readPrecomputedFlags(eq(assignmentContext), any())) doAnswer {
            testedClient.setEvaluationContext(otherContext)
            "response-a"
        }
        whenever(mapper.map("response-a")) doReturn mapOf("flag" to flag)
        testedClient.setEvaluationContext(assignmentContext)
        runNextFetch()
        runNextFetch()
        assertReason("STALE")
    }

    @Test
    fun `M keep installed network state W disk completes late`() {
        testedClient.setEvaluationContext(otherContext)
        repository.setFlagsAndContext(assignmentContext, mapOf("flag" to flag))
        install(fromDisk = true)
        assertReason("STALE")
        testedClient.setEvaluationContext(assignmentContext)
        assertReason("TARGETING_MATCH")
    }

    @Test
    fun `M keep empty network state W disk completes late`() {
        testedClient.setEvaluationContext(otherContext)
        repository.setFlagsAndContext(otherContext, emptyMap())
        install(fromDisk = true)
        assertThat(testedClient.getFlagAssignmentsSnapshot()).isEmpty()
        assertThat(testedClient.resolve("flag", false).errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
    }

    @Test
    fun `M retain owned context W caller mutates attributes before fetch`() {
        val attributes = assignmentContext.attributes.toMutableMap()
        val input = assignmentContext.copy(attributes = attributes)
        install(fromDisk = false)
        whenever(reader.readPrecomputedFlags(any(), any())) doReturn "response"
        whenever(mapper.map("response")) doReturn mapOf("flag" to flag)
        testedClient.setEvaluationContext(input)
        attributes["plan"] = "paid"
        runNextFetch()
        verify(reader).readPrecomputedFlags(eq(assignmentContext), any())
        assertThat(repository.getEvaluationContext()).isEqualTo(assignmentContext)
        val saved = argumentCaptor<FlagsStateEntry>()
        verify(dataStore, org.mockito.kotlin.times(2)).setValue(any(), saved.capture(), any(), anyOrNull(), any())
        assertThat(saved.lastValue.evaluationContext).isEqualTo(assignmentContext)
        assertReason("TARGETING_MATCH")
        testedClient.setEvaluationContext(input)
        assertReason("STALE")
    }

    @Test
    fun `M preserve snapshots and raw assignments W context transitions`() {
        install(fromDisk = false)
        val aligned = testedClient.getFlagAssignmentsSnapshot()
        clearInvocations(exposureProcessor, evaluationFeature, rumLogger, dataStore)
        testedClient.setEvaluationContext(otherContext)
        val stale = testedClient.getFlagAssignmentsSnapshot()
        assertThat(stale["flag"]).isEqualTo(flag.copy(reason = "STALE"))
        assertThat(repository.getPrecomputedFlag("flag")).isSameAs(flag)
        assertThat(aligned["flag"]).isSameAs(flag)
        testedClient.setEvaluationContext(assignmentContext)
        assertThat(testedClient.getFlagAssignmentsSnapshot()["flag"]).isSameAs(flag)
        assertThat(stale["flag"]?.reason).isEqualTo("STALE")
        verifyNoInteractions(exposureProcessor, evaluationFeature, rumLogger)
        verify(dataStore, never()).setValue<FlagsStateEntry>(any(), any(), any(), anyOrNull(), any())
    }

    @Test
    fun `M retain errors W evaluating stale assignments`() {
        install(fromDisk = false)
        testedClient.setEvaluationContext(otherContext)
        val missing = testedClient.resolve("missing", false)
        assertThat(missing.reason?.name).isEqualTo("ERROR")
        assertThat(missing.errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
        val mismatch = testedClient.resolve("flag", "default")
        assertThat(mismatch.reason?.name).isEqualTo("ERROR")
        assertThat(mismatch.errorCode).isEqualTo(ErrorCode.TYPE_MISMATCH)
        repository.setFlagsAndContext(assignmentContext, mapOf("flag" to flag.copy(variationValue = "not-boolean")))
        val malformed = testedClient.resolve("flag", false)
        assertThat(malformed.reason?.name).isEqualTo("ERROR")
        assertThat(malformed.errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
    }

    @Test
    fun `M use captured reason and assignment context W request changes during tracking`() {
        install(fromDisk = false)
        testedClient.setEvaluationContext(otherContext)
        doAnswer {
            testedClient.setEvaluationContext(assignmentContext)
            null
        }.whenever(exposureProcessor).processEvent(any(), any(), any())
        val result = testedClient.resolve("flag", false)
        assertThat(result.reason?.name).isEqualTo("STALE")
        verify(exposureProcessor).processEvent("flag", assignmentContext, flag)
        verify(evaluationFeature).processEvaluation(
            "flag",
            assignmentContext,
            flag.variationKey,
            flag.allocationKey,
            null,
            null
        )
        assertReason("TARGETING_MATCH")
    }

    @Test
    fun `M keep captured assignment generation W network replacement during tracking`() {
        install(fromDisk = false)
        testedClient.setEvaluationContext(otherContext)
        val replacement = flag.copy(variationValue = "false", variationKey = "new-variant", reason = "SPLIT")
        doAnswer {
            repository.setFlagsAndContext(otherContext, mapOf("flag" to replacement))
            null
        }.whenever(exposureProcessor).processEvent(any(), any(), any())
        val oldResult = testedClient.resolve("flag", false)
        assertThat(oldResult.value).isTrue()
        assertThat(oldResult.variant).isEqualTo(flag.variationKey)
        assertThat(oldResult.reason?.name).isEqualTo("STALE")
        verify(evaluationFeature).processEvaluation(
            "flag",
            assignmentContext,
            flag.variationKey,
            flag.allocationKey,
            null,
            null
        )
        val newResult = testedClient.resolve("flag", true)
        assertThat(newResult.value).isFalse()
        assertThat(newResult.variant).isEqualTo(replacement.variationKey)
        assertThat(newResult.reason?.name).isEqualTo("SPLIT")
    }

    @Test
    fun `M retain not ready W requested context but disk fails`() {
        testedClient.setEvaluationContext(otherContext)
        diskCallback.onFailure()
        assertThat(repository.getEvaluationContext()).isNull()
        val result = testedClient.resolve("flag", false)
        assertThat(result.value).isFalse()
        assertThat(result.reason?.name).isEqualTo("ERROR")
        assertThat(result.errorCode).isEqualTo(ErrorCode.PROVIDER_NOT_READY)
        assertThat(testedClient.getFlagAssignmentsSnapshot()).isEmpty()
    }

    @Test
    fun `M mark each value type stale without changing metadata W resolving mismatched assignments`() {
        testedClient.setEvaluationContext(otherContext)
        assertStaleValue("boolean", "true", false, true)
        assertStaleValue("string", "value", "default", "value")
        assertStaleValue("integer", "42", 0, 42)
        assertStaleValue("number", "4.5", 0.0, 4.5)
        assertStaleValue("object", "{\"key\":\"value\"}", emptyMap(), mapOf("key" to "value"))
        assertStaleValue("object", "{\"key\":\"value\"}", JSONObject(), JSONObject().put("key", "value"))
    }

    private fun <T : Any> assertStaleValue(type: String, raw: String, defaultValue: T, expected: T) {
        val typedFlag = flag.copy(variationType = type, variationValue = raw)
        repository.setFlagsAndContext(assignmentContext, mapOf("flag" to typedFlag, "other" to typedFlag))
        val details = testedClient.resolve("flag", defaultValue)
        assertThat(details.value.toString()).isEqualTo(expected.toString())
        assertThat(details.reason?.name).isEqualTo("STALE")
        assertThat(details.errorCode).isNull()
        assertThat(details.variant).isEqualTo(flag.variationKey)
        assertThat(details.flagMetadata["allocationKey"]).isEqualTo(flag.allocationKey)
        assertThat(testedClient.getFlagAssignmentsSnapshot().values.map { it.reason }).containsOnly("STALE")
        verify(exposureProcessor).processEvent("flag", assignmentContext, typedFlag)
        clearInvocations(exposureProcessor)
    }

    private fun install(fromDisk: Boolean) {
        if (fromDisk) {
            diskCallback.onSuccess(DataStoreContent(1, FlagsStateEntry(assignmentContext, mapOf("flag" to flag), 1)))
        } else {
            repository.setFlagsAndContext(assignmentContext, mapOf("flag" to flag))
        }
    }

    private fun originalReason(fromDisk: Boolean): String = if (fromDisk) "CACHED" else "TARGETING_MATCH"

    private fun runNextFetch() {
        tasks.removeAt(0).run()
    }

    private fun assertReason(reason: String, value: Boolean = true) {
        val details = testedClient.resolve("flag", false)
        assertThat(details.value).isEqualTo(value)
        assertThat(details.reason?.name).isEqualTo(reason)
        assertThat(details.errorCode).isNull()
        assertThat(details.variant).isEqualTo(flag.variationKey)
        assertThat(testedClient.getFlagAssignmentsSnapshot()["flag"]?.reason).isEqualTo(reason)
    }
}
