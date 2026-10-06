/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.internal

import com.datadog.android.api.InternalLogger
import com.datadog.android.api.context.DatadogContext
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.feature.FeatureSdkCore
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.flags.internal.evaluation.EvaluationsManager
import com.datadog.android.flags.internal.model.FlagKeyObfuscation
import com.datadog.android.flags.internal.model.FlagsStateEntry
import com.datadog.android.flags.internal.persistence.FlagsStateDeserializer
import com.datadog.android.flags.internal.persistence.FlagsStateSerializer
import com.datadog.android.flags.internal.repository.DefaultFlagsRepository
import com.datadog.android.flags.internal.repository.net.PrecomputeMapper
import com.datadog.android.flags.internal.storage.RecordWriter
import com.datadog.android.flags.model.ErrorCode
import com.datadog.android.flags.model.EvaluationContext
import com.datadog.android.flags.model.ExposureEvent
import com.datadog.android.flags.model.ResolutionReason
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

internal class FlagKeyObfuscationTest {
    private val mockInternalLogger: InternalLogger = mock()
    private val mockSdkCore: FeatureSdkCore = mock()
    private val mockFeatureScope: FeatureScope = mock()
    private val mockDatadogContext: DatadogContext = mock()
    private val mockEvaluationsManager: EvaluationsManager = mock()
    private val mockDataStore: DataStoreHandler = mock()
    private val mockWriter: RecordWriter = mock()
    private val mockRumLogger: RumEvaluationLogger = mock()
    private val mockEvaluationsFeature: EvaluationsFeature = mock()
    private val fakeContext = EvaluationContext("athlete-123", mapOf("team" to "cycling"))
    private val testedMapper = PrecomputeMapper(mockInternalLogger)
    private lateinit var testedRepository: DefaultFlagsRepository
    private lateinit var testedClient: DatadogFlagsClient

    @BeforeEach
    fun setUp() {
        whenever(mockSdkCore.internalLogger) doReturn mockInternalLogger
        whenever(mockSdkCore.timeProvider) doReturn mock()
        whenever(mockSdkCore.getFeature(Feature.FLAGS_FEATURE_NAME)) doReturn mockFeatureScope
        whenever(mockDatadogContext.source) doReturn "android"
        doAnswer { it.getArgument<(DatadogContext) -> Unit>(1).invoke(mockDatadogContext) }
            .whenever(mockFeatureScope).withContext(any(), any())
        doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onFailure()
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        testedRepository = DefaultFlagsRepository(mockSdkCore, "obfuscation", mockDataStore)
        testedClient = createClient()
    }

    @ParameterizedTest
    @MethodSource("vectors")
    fun `M resolve original key W resolve() { shared edge hash vectors }`(vector: Pair<String, String>) {
        val (key, digest) = vector
        val encoding = checkNotNull(FlagKeyObfuscation.read(metadata()))
        assertThat(encoding.encode(key)).isEqualTo(digest)
        install(payload(key, encoded = false))
        val plain = testedClient.resolve(key, false)

        install(payload(digest))

        assertThat(testedClient.resolve(key, false)).isEqualTo(plain)
        assertThat(testedClient.resolveBooleanValue(key, false)).isTrue()
        verify(mockRumLogger, times(3)).logEvaluation(key, "variation-456")
        verify(
            mockEvaluationsFeature,
            times(3)
        ).processEvaluation(key, fakeContext, "variation-456", "allocation-123", null, null)
        val exposure = argumentCaptor<ExposureEvent>()
        verify(mockWriter).write(exposure.capture())
        assertThat(exposure.firstValue.flag.key).isEqualTo(key)
        assertThat(exposure.firstValue.serialId).isEqualTo(123L)
    }

    @Test
    fun `M cache bounded lookup hashes W encode() { descriptors keep separate caches }`() {
        val encoding = checkNotNull(FlagKeyObfuscation.read(metadata()))
        val other = checkNotNull(FlagKeyObfuscation.read(metadata("f".repeat(32))))
        val first = checkNotNull(encoding.encode("flag"))
        assertThat(encoding.encode("flag")).isSameAs(first)
        assertThat(other.encode("flag")).isNotEqualTo(first)
        assertThat(encoding.encode("flag")).isSameAs(first)
        assertThat(encoding.encode("café")).isNotEqualTo(encoding.encode("cafe\u0301"))
        repeat(1024) { encoding.encode("flag-$it") }
        assertThat(encoding.encode("flag")).isEqualTo(first).isNotSameAs(first)
    }

    @Test
    fun `M preserve results and defaults W resolve() { encoded types and errors }`() {
        val encoding = checkNotNull(FlagKeyObfuscation.read(metadata()))
        val flags = JSONObject()
        listOf(
            "string" to "visible",
            "integer" to 42,
            "number" to 12.5,
            "object" to JSONObject("{\"nested\":[42]}")
        ).forEach {
            flags.put(checkNotNull(encoding.encode(it.first)), assignment(it.first, it.second))
        }
        flags.put(checkNotNull(encoding.encode("bad")), assignment("boolean", "invalid"))
        val assignments = metadata().put("flags", flags)
        install(JSONObject().put("data", JSONObject().put("attributes", assignments)).toString())

        assertThat(testedClient.resolveStringValue("string", "default")).isEqualTo("visible")
        assertThat(testedClient.resolveIntValue("integer", -1)).isEqualTo(42)
        assertThat(testedClient.resolveDoubleValue("number", -1.0)).isEqualTo(12.5)
        assertThat(testedClient.resolveStructureValue("object", emptyMap())).containsKey("nested")
        assertThat(testedClient.resolve("integer", "default").errorCode)
            .isEqualTo(ErrorCode.TYPE_MISMATCH)
        assertThat(testedClient.resolve("bad", false).errorCode).isEqualTo(ErrorCode.PARSE_ERROR)
        assertThat(testedClient.resolve("missing", false).errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
    }

    @Test
    fun `M retain lookup and deduplicate exposure W setFlagsAndContext() { salt changes and cache round trip }`() {
        for (salt in listOf(SALT, "f".repeat(32), SALT)) {
            val descriptor = metadata(salt)
            val encoding = checkNotNull(FlagKeyObfuscation.read(descriptor))
            val decoded = checkNotNull(testedMapper.map(payload(checkNotNull(encoding.encode("flag")), salt = salt)))
            val entry = FlagsStateEntry(fakeContext, decoded.flags, 1234L, decoded.obfuscation)
            val serialized = FlagsStateSerializer(mockInternalLogger).serialize(entry)
            // The legacy deserializer requires flags, so it cannot ingest encoded keys.
            assertThat(JSONObject(serialized).has("flags")).isFalse()
            val restored = checkNotNull(FlagsStateDeserializer(mockInternalLogger).deserialize(serialized))
            assertThat(restored.obfuscation).isEqualTo(encoding)
            assertThat(restored.evaluationContext).isEqualTo(fakeContext)
            testedRepository.setFlagsAndContext(restored.evaluationContext, restored.flags, restored.obfuscation)
            assertThat(testedClient.resolveBooleanValue("flag", false)).isTrue()
            assertThat(testedRepository.getPrecomputedFlag("flag")).isNotNull()
            assertThat(testedRepository.getPrecomputedFlag("flag")?.variationKey).isEqualTo("variation-456")
            assertThat(testedRepository.getPrecomputedFlag("flag")?.serialId).isEqualTo(123L)
            assertThat(testedRepository.getPrecomputedFlag(checkNotNull(encoding.encode("flag")))).isNull()
        }
        verify(mockWriter, times(1)).write(any<ExposureEvent>())
        verify(mockRumLogger, times(3)).logEvaluation("flag", "variation-456")
    }

    @Test
    fun `M accept new flags and isolate unknown types W resolve() { forward compatible response }`() {
        val encoding = checkNotNull(FlagKeyObfuscation.read(metadata()))
        val flags = JSONObject()
            .put(checkNotNull(encoding.encode("flag")), assignment())
            .put(checkNotNull(encoding.encode("new-flag")), assignment().put("future-field", true))
            .put(checkNotNull(encoding.encode("future")), assignment("future-type", true))
        val attributes = metadata().put("flags", flags).put("future-field", true)
        install(JSONObject().put("data", JSONObject().put("attributes", attributes)).toString())

        assertThat(testedClient.resolveBooleanValue("flag", false)).isTrue()
        assertThat(testedClient.resolveBooleanValue("new-flag", false)).isTrue()
        assertThat(testedClient.resolve("future", false).value).isFalse()
    }

    @Test
    fun `M restore latest salt and value W init { after multiple writes }`() {
        for ((salt, value) in listOf(SALT to true, "f".repeat(32) to false)) {
            val encoding = checkNotNull(FlagKeyObfuscation.read(metadata(salt)))
            val flags = JSONObject().put(checkNotNull(encoding.encode("flag")), assignment("boolean", value))
            val attributes = metadata(salt).put("flags", flags)
            install(JSONObject().put("data", JSONObject().put("attributes", attributes)).toString())
            assertThat(testedClient.resolveBooleanValue("flag", !value)).isEqualTo(value)
        }
        val written = argumentCaptor<FlagsStateEntry>()
        verify(mockDataStore, times(2)).setValue(any(), written.capture(), any(), anyOrNull(), any())
        val serialized = FlagsStateSerializer(mockInternalLogger).serialize(written.lastValue)
        val restored = checkNotNull(FlagsStateDeserializer(mockInternalLogger).deserialize(serialized))
        assertThat(restored.obfuscation?.salt).isEqualTo("f".repeat(32))
        doAnswer {
            it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onSuccess(DataStoreContent(0, restored))
        }.whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        testedRepository = DefaultFlagsRepository(mockSdkCore, "obfuscation", mockDataStore)
        testedClient = createClient()
        assertThat(testedClient.resolveBooleanValue("flag", true)).isFalse()
        verifyNoInteractions(mockEvaluationsManager)
    }

    @Test
    fun `M hide encoded assignments W setObfuscationSupported() { legacy consumer }`() {
        install(payload(VECTORS[2].second))
        assertThat(testedRepository.getFlagsSnapshot()).isEmpty()
        testedRepository.setObfuscationSupported(false)
        assertThat(testedRepository.hasFlags()).isFalse()
        assertThat(testedRepository.getEvaluationContext()).isNull()
        install(payload("flag", encoded = false))
        assertThat(testedRepository.getFlagsSnapshot()).containsKey("flag")
    }

    @Test
    fun `M reject late encoded cache W setObfuscationSupported() { React Native consumer }`() {
        var cacheCallback: DataStoreReadCallback<FlagsStateEntry>? = null
        doAnswer { cacheCallback = it.getArgument(2) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        val testedRepository = DefaultFlagsRepository(mockSdkCore, "late-cache", mockDataStore)
        testedRepository.setObfuscationSupported(false)
        val decoded = checkNotNull(testedMapper.map(payload(VECTORS[2].second)))
        val entry = FlagsStateEntry(fakeContext, decoded.flags, 1234L, decoded.obfuscation)

        checkNotNull(cacheCallback).onSuccess(DataStoreContent(0, entry))

        assertThat(testedRepository.getFlagsSnapshot()).isEmpty()
        assertThat(testedRepository.hasFlags()).isFalse()
        assertThat(testedRepository.getEvaluationContext()).isNull()
    }

    @Test
    fun `M restore encoded cache W init { native consumer }`() {
        val decoded = checkNotNull(testedMapper.map(payload(VECTORS[2].second)))
        val entry = FlagsStateEntry(fakeContext, decoded.flags, 1234L, decoded.obfuscation)
        doAnswer { it.getArgument<DataStoreReadCallback<FlagsStateEntry>>(2).onSuccess(DataStoreContent(0, entry)) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())

        val testedRepository = DefaultFlagsRepository(mockSdkCore, "restored-cache", mockDataStore)

        assertThat(testedRepository.getPrecomputedFlagWithContext("flag")?.second).isEqualTo(fakeContext)
        assertThat(testedRepository.getPrecomputedFlag("flag")?.variationValue).isEqualTo("true")
        assertThat(testedRepository.getPrecomputedFlag("flag")?.reason).isEqualTo(ResolutionReason.CACHED.name)
        assertThat(testedRepository.getPrecomputedFlag(VECTORS[2].second)).isNull()
    }

    @ParameterizedTest
    @MethodSource("cacheInitializations")
    fun `M gate cached assignments W resolveBooleanValue() { delayed source or disk without network }`(
        source: String,
        diskFirst: Boolean,
        encoded: Boolean
    ) {
        // Given
        var sourceCallback: ((DatadogContext) -> Unit)? = null
        var diskCallback: DataStoreReadCallback<FlagsStateEntry>? = null
        doAnswer { sourceCallback = it.getArgument(1) }
            .whenever(mockFeatureScope).withContext(any(), any())
        doAnswer { diskCallback = it.getArgument(2) }
            .whenever(mockDataStore).value<FlagsStateEntry>(any(), anyOrNull(), any(), any())
        whenever(mockDatadogContext.source) doReturn source
        val decoded = checkNotNull(testedMapper.map(payload(if (encoded) VECTORS[2].second else "flag", encoded)))
        val entry = FlagsStateEntry(fakeContext, decoded.flags, 1234L, decoded.obfuscation)
        testedRepository = DefaultFlagsRepository(mockSdkCore, "deferred-cache", mockDataStore)
        testedClient = createClient()

        // When
        if (diskFirst) {
            checkNotNull(diskCallback).onSuccess(DataStoreContent(0, entry))
            assertThat(testedClient.resolveBooleanValue("flag", false)).isEqualTo(!encoded)
            assertThat(testedRepository.hasFlags()).isEqualTo(!encoded)
            assertThat(testedRepository.hasLoadedFlagsForContext(fakeContext)).isEqualTo(!encoded)
            assertThat(testedRepository.getEvaluationContext()).isEqualTo(if (encoded) null else fakeContext)
            assertThat(testedRepository.getPrecomputedFlag("flag") != null).isEqualTo(!encoded)
            assertThat(testedRepository.getPrecomputedFlagWithContext("flag") != null).isEqualTo(!encoded)
        }
        checkNotNull(sourceCallback).invoke(mockDatadogContext)
        if (!diskFirst) {
            checkNotNull(diskCallback).onSuccess(DataStoreContent(0, entry))
        }

        // Then
        val readable = !encoded || source == "android"
        assertThat(testedClient.resolveBooleanValue("flag", false)).isEqualTo(readable)
        assertThat(testedRepository.hasFlags()).isEqualTo(readable)
        assertThat(testedRepository.hasLoadedFlagsForContext(fakeContext)).isEqualTo(readable)
        assertThat(testedRepository.getEvaluationContext()).isEqualTo(if (readable) fakeContext else null)
        assertThat(testedRepository.getPrecomputedFlag("flag") != null).isEqualTo(readable)
        assertThat(testedRepository.getPrecomputedFlagWithContext("flag") != null).isEqualTo(readable)
        assertThat(testedRepository.getFlagsSnapshot().isEmpty()).isEqualTo(encoded)
        verifyNoInteractions(mockEvaluationsManager)
    }

    @Test
    fun `M reject invalid Unicode W resolve() { no replacement alias }`() {
        install(payload(VECTORS[2].second))
        for (key in listOf("\ud800", "\udc00", "a\ud800b")) {
            assertThat(testedClient.resolve(key, false).errorCode).isEqualTo(ErrorCode.FLAG_NOT_FOUND)
        }
    }

    @ParameterizedTest
    @MethodSource("invalidMetadata")
    fun `M reject response and cache W map() and deserialize() { invalid descriptor }`(json: String) {
        val attrs = JSONObject(json).put("flags", JSONObject())
        assertThat(
            testedMapper.map(JSONObject().put("data", JSONObject().put("attributes", attrs)).toString())
        ).isNull()
        val entry = FlagsStateEntry(fakeContext, emptyMap(), 1234L)
        val cache = JSONObject(FlagsStateSerializer(mockInternalLogger).serialize(entry))
        JSONObject(json).keys().forEach { cache.put(it, JSONObject(json).get(it)) }
        assertThat(FlagsStateDeserializer(mockInternalLogger).deserialize(cache.toString())).isNull()
    }

    @Test
    fun `M reject malformed map keys W map() { encoded payload }`() {
        for (key in listOf("plaintext", "a".repeat(63), "a".repeat(65), "A".repeat(64), "a".repeat(63) + "\n")) {
            assertThat(testedMapper.map(payload(key))).isNull()
        }
    }

    @Test
    fun `M accept legacy descriptors W map() { absent or false }`() {
        for (attrs in listOf(JSONObject(), JSONObject().put("obfuscated", false))) {
            attrs.put("flags", JSONObject().put("flag", assignment()))
            install(JSONObject().put("data", JSONObject().put("attributes", attrs)).toString())
            assertThat(testedClient.resolveBooleanValue("flag", false)).isTrue()
        }
    }

    private fun install(response: String) {
        val assignments = checkNotNull(testedMapper.map(response))
        testedRepository.setFlagsAndContext(fakeContext, assignments.flags, assignments.obfuscation)
    }

    private fun createClient(): DatadogFlagsClient = DatadogFlagsClient(
        featureSdkCore = mockSdkCore,
        evaluationsManager = mockEvaluationsManager,
        flagsRepository = testedRepository,
        flagsConfiguration = FlagsConfiguration.Builder().build(),
        rumEvaluationLogger = mockRumLogger,
        exposureProcessor = ExposureEventsProcessor(mockWriter, mockSdkCore.timeProvider),
        evaluationsFeature = mockEvaluationsFeature,
        flagStateManager = mock()
    )

    companion object {
        private const val SALT = "000102030405060708090a0b0c0d0e0f"
        private val VECTORS = listOf(
            "new-route-planner" to "a60479237ef2f69175bbe0bd581966d1583766941815dc1d414c883767795190",
            "Flag" to "adca75d2141c51b0c0f084c1058edfb8e6e763f91aaf54ca06326d9847bd9586",
            "flag" to "9817872c144b018abd77e3915bd77e2c27f4f534dccff8ffaca27361e3a5e1ee",
            " flag " to "b1a5f851cc82a3fdf03a461d72a2241584dcf455df1d384e02a937901b3bc80b",
            "café" to "3bfa8c3c17c1b61035b98ecf14007cdf5cba5ce61a48e8cfb65f8106541892d3",
            "cafe\u0301" to "1e8b7ec5e8028a1ec96b38dd37f6ccea041c0a90358904af40ac5810c23c8765",
            "🚲/旗" to "94b611e0d3b26b52f6ad66013d1c72f8a92ea109390049759e75d3c8d3aa4dab",
            "a\u0000b" to "bac134d201be5e7f28fc7019248f0809c2a013b137e866446ee71add2bc344c7",
            "" to "072d985b427f536ad0a11b2d4c5e0f7e6f0ff6d23e779e082f75599ce3fe3eba"
        )

        @JvmStatic
        fun vectors(): List<Pair<String, String>> = VECTORS

        @JvmStatic
        fun cacheInitializations(): List<Arguments> =
            listOf("android", "react-native", "future-bridge").flatMap { source ->
                listOf(true, false).flatMap { diskFirst ->
                    listOf(true, false).map { encoded -> Arguments.of(source, diskFirst, encoded) }
                }
            }

        @JvmStatic
        fun invalidMetadata(): List<String> = listOf(
            "{\"obfuscated\":true}",
            "{\"obfuscated\":null}",
            "{\"obfuscated\":true,\"obfuscation\":null}",
            "{\"obfuscated\":true,\"obfuscation\":[]}",
            metadata().put("obfuscated", "true").toString(),
            metadata().put("obfuscated", false).toString(),
            metadata().apply { remove("obfuscated") }.toString(),
            metadata().apply { getJSONObject("obfuscation").put("scheme", "unknown") }.toString()
        ) + listOf("", "0".repeat(30), "0".repeat(34), "G".repeat(32), SALT.uppercase(), "0".repeat(31) + "\n", 42)
            .map { metadata().apply { getJSONObject("obfuscation").put("salt", it) }.toString() }

        private fun metadata(salt: String = SALT): JSONObject = JSONObject()
            .put("obfuscated", true)
            .put("obfuscation", JSONObject().put("scheme", FlagKeyObfuscation.SCHEME).put("salt", salt))

        private fun assignment(type: String = "boolean", value: Any = true): JSONObject = JSONObject()
            .put("variationType", type).put("variationValue", value).put("allocationKey", "allocation-123")
            .put("variationKey", "variation-456").put("doLog", true).put("serialId", 123L)
            .put("reason", "TARGETING_MATCH").put("extraLogging", JSONObject().put("test", "metadata"))

        private fun payload(key: String, encoded: Boolean = true, salt: String = SALT): String {
            val attrs = if (encoded) metadata(salt) else JSONObject()
            attrs.put("flags", JSONObject().put(key, assignment()))
            return JSONObject().put("data", JSONObject().put("attributes", attrs)).toString()
        }
    }
}
