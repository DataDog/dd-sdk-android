/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2026-Present Datadog, Inc.
 */

package com.datadog.flags.benchmark

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.protobuf.util.JsonFormat
import datadog.ffe.flagging.ufc.v1.Ufc
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlagsBenchmarkTest {
    @TempDir lateinit var temporary: File
    private val fixtures = JsonParser.parseString(
        requireNotNull(javaClass.getResource("/client-benchmark.json")).readText()
    ).asJsonObject.getAsJsonArray("configurations")

    private fun bridge() = FlagsBenchmark(File(temporary, "cache-${System.nanoTime()}.bin"))
    private fun input(flag: String = "flag-0", plan: String = "paid") = mapOf<String, Any?>(
        "op" to "evaluate", "flagKey" to flag, "type" to "boolean", "defaultValue" to false,
        "context" to mapOf("targetingKey" to "subject-0", "plan" to plan, "age" to 25)
    )
    private fun install(bridge: FlagsBenchmark, revision: Int = 0) = bridge.run(mapOf(
        "op" to "installJson", "json" to fixtures[revision].asJsonObject["protoJson"].asString
    ))
    private fun configuration() = Ufc.FlagsConfiguration.newBuilder().also {
        JsonFormat.parser().merge(fixtures[0].asJsonObject["protoJson"].asString, it)
    }
    private fun canonical(result: Map<String, Any?>) = Gson().toJsonTree(result).asJsonObject.also {
        it.getAsJsonObject("flagMetadata")?.remove("__dd_eval_timestamp_ms")
    }

    @Test
    fun `M match JS results W both decoders and context replacement`() {
        val tested = bridge()
        for (fixture in fixtures.map { it.asJsonObject }) {
            val base64 = fixture["protobufBase64"].asString
            val bytes = Base64.getDecoder().decode(base64)
            assertEquals(fixture["sha256"].asString, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            assertNull(tested.run(mapOf("op" to "preload", "base64" to base64))["benchmarkError"])
            for (installation in listOf(
                mapOf("op" to "installBinary"), mapOf("op" to "installJson", "json" to fixture["protoJson"].asString)
            )) {
                assertEquals(10, tested.run(installation)["flagCount"])
                for (case in fixture.getAsJsonArray("cases").map { it.asJsonObject }) {
                    @Suppress("UNCHECKED_CAST")
                    val request = Gson().fromJson(case["request"], Map::class.java) as Map<String, Any?>
                    assertEquals(case["expected"], canonical(tested.run(request)))
                }
            }
            val roundtrip = Ufc.FlagsConfiguration.newBuilder()
            JsonFormat.parser().merge(tested.run(mapOf("op" to "binaryToJson"))["json"] as String, roundtrip)
            assertEquals(Ufc.FlagsConfiguration.parseFrom(bytes), roundtrip.build())
        }
    }

    @Test
    fun `M return original assignment W A B A and configuration replacement`() {
        val tested = bridge()
        install(tested)
        assertEquals(true, tested.run(input("flag-1"))["value"])
        assertEquals(false, tested.run(input("flag-1", "free"))["value"])
        assertEquals(true, tested.run(input("flag-1"))["value"])
        install(tested, 1)
        assertEquals(false, tested.run(input("flag-1"))["value"])
        install(tested)
        assertEquals(true, tested.run(input("flag-1"))["value"])
    }

    @Test
    fun `M retain snapshot W malformed replacement`() {
        val tested = bridge()
        assertNotNull(tested.run(input())["benchmarkError"])
        install(tested)
        assertNotNull(tested.run(mapOf("op" to "installJson", "json" to "{"))["benchmarkError"])
        tested.run(mapOf("op" to "preload", "base64" to "AA=="))
        assertNotNull(tested.run(mapOf("op" to "installBinary"))["benchmarkError"])
        assertEquals(true, tested.run(input())["value"])
        assertNotNull(tested.run(mapOf("op" to "preload", "base64" to "not base64"))["benchmarkError"])
    }

    @Test
    fun `M reject unsupported or cyclic rules W evaluation`() {
        val config = configuration()
        config.setConditions(0, Ufc.Condition.newBuilder().setRegex(Ufc.RegexCondition.getDefaultInstance()))
        val request = EvaluationRequest.from(input("flag-1"))
        assertEquals("PARSE_ERROR", RulesEvaluator(config.build()).evaluate(request)["errorCode"])
        config.setConditions(0, Ufc.Condition.newBuilder().setAll(Ufc.ConditionOperands.newBuilder().addConditionIndexes(0)))
        assertEquals("PARSE_ERROR", RulesEvaluator(config.build()).evaluate(request)["errorCode"])
        config.putFlags("flag-0", config.getFlagsOrThrow("flag-0").toBuilder().setMinimumFeatureLevel(1).build())
        assertEquals("PARSE_ERROR", RulesEvaluator(config.build()).evaluate(EvaluationRequest.from(input()))["errorCode"])
    }

    @Test
    fun `M report targeting errors W missing targeting key and invalid shards`() {
        val config = configuration()
        val missing = EvaluationRequest.from(input("flag-3") + ("context" to emptyMap<String, Any?>()))
        assertEquals("TARGETING_KEY_MISSING", RulesEvaluator(config.build()).evaluate(missing)["errorCode"])
        val flag = config.getFlagsOrThrow("flag-3").toBuilder()
        val allocation = flag.getAllocations(0).toBuilder()
        val partition = allocation.getPartitionKey(0).toBuilder()
        partition.setShardMd5(partition.shardMd5.toBuilder().setTotalShards(0))
        allocation.setPartitionKey(0, partition)
        flag.setAllocations(0, allocation)
        config.putFlags("flag-3", flag.build())
        assertEquals("PARSE_ERROR", RulesEvaluator(config.build()).evaluate(EvaluationRequest.from(input("flag-3")))["errorCode"])
    }

    @Test
    fun `M match installed config W all hydration paths and replacement`() {
        val tested = bridge()
        assertNotNull(tested.run(mapOf("op" to "readCachedBinary"))["benchmarkError"])
        for (fixture in fixtures.map { it.asJsonObject }) {
            val base64 = fixture["protobufBase64"].asString
            tested.run(mapOf("op" to "preload", "base64" to base64))
            assertNull(tested.run(mapOf("op" to "persistBinary"))["benchmarkError"])
            assertEquals(base64, tested.run(mapOf("op" to "readCachedBinary"))["base64"])
            val json = tested.run(mapOf("op" to "readCachedJson"))["json"] as String
            assertNull(tested.run(mapOf("op" to "installJson", "json" to json))["benchmarkError"])
            assertEquals(canonical(tested.run(input())), canonical(tested.run(input() + ("op" to "evaluateCachedBinary"))))
        }
        assertNotNull(bridge().run(mapOf("op" to "readCachedBinary"))["benchmarkError"])
    }

    @Test
    fun `M consume values and validate counts W native controls`() {
        val tested = bridge()
        val fixture = fixtures[0].asJsonObject
        tested.run(mapOf("op" to "preload", "base64" to fixture["protobufBase64"].asString))
        val cases = fixture.getAsJsonArray("cases").map { it.asJsonObject }
        val requests = cases.map { Gson().fromJson(it["request"], Map::class.java) }
        val request = mapOf("op" to "controls", "requests" to requests, "iterations" to 64, "warmup" to 1, "decodeIterations" to 3)
        val output = tested.run(request)
        assertNull(output["benchmarkError"])
        assertEquals(cases.count { it["expected"].asJsonObject["value"].asBoolean }, output["checksum"])
        assertEquals(64, (output["directEvaluation"] as Map<*, *>)["count"])
        assertEquals(3, (output["decode"] as Map<*, *>)["count"])
        for (count in listOf(0, -1, 100_001, 1.5, true, Double.NaN)) {
            assertNotNull(tested.run(request + ("iterations" to count))["benchmarkError"])
        }
        assertEquals(2_000.0, FlagsBenchmark.summarize(listOf(4.0, 1.0, 3.0, 2.0))["p50Us"])
    }

    @Test
    fun `M reject invalid request and keep scalar types distinct W evaluation`() {
        val tested = bridge()
        install(tested)
        assertNotNull(tested.run(input() + ("defaultValue" to 0))["benchmarkError"])
        assertNotNull(tested.run(input() + ("context" to mapOf("age" to Double.NaN)))["benchmarkError"])
        assertEquals("FLAG_NOT_FOUND", tested.run(input("missing"))["errorCode"])
        assertTrue((tested.run(input())["flagMetadata"] as Map<*, *>)["__dd_eval_timestamp_ms"] is Long)
    }
}
